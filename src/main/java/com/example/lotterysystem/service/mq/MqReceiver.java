package com.example.lotterysystem.service.mq;

import cn.hutool.core.date.DateUtil;
import com.example.lotterysystem.common.exception.ServiceException;
import com.example.lotterysystem.common.utils.JacksonUtil;
import com.example.lotterysystem.controller.param.DrawPrizeParam;
import com.example.lotterysystem.dao.dateobject.ActivityPrizeDO;
import com.example.lotterysystem.dao.dateobject.WinningRecordDO;
import com.example.lotterysystem.dao.mapper.ActivityPrizeMapper;
import com.example.lotterysystem.dao.mapper.WinningRecordMapper;
import com.example.lotterysystem.service.DrawPrizeService;
import com.example.lotterysystem.service.activitystatus.ActivityStatusManager;
import com.example.lotterysystem.service.dto.ConvertActivityStatusDTO;
import com.example.lotterysystem.service.enums.ActivityPrizeStatusEnum;
import com.example.lotterysystem.service.enums.ActivityStatusEnum;
import com.example.lotterysystem.service.enums.ActivityUserStatusEnum;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.example.lotterysystem.common.config.DirectRabbitConfig.QUEUE_NAME;

@Component
@RabbitListener(queues = QUEUE_NAME)
public class MqReceiver {

    private static final Logger logger = LoggerFactory.getLogger(MqReceiver.class);

    /**
     * 分布式锁 key 前缀，锁粒度：活动 + 奖品
     */
    private static final String DRAW_LOCK_PREFIX = "DRAW_LOCK:";

    /**
     * 获取锁的最大等待时间（秒）
     */
    private static final long LOCK_WAIT_TIME = 5;

    /**
     * 持有锁的最大时间（秒），超时自动释放，防止死锁
     */
    private static final long LOCK_LEASE_TIME = 30;

    @Autowired
    private DrawPrizeService drawPrizeService;
    @Autowired
    private ActivityStatusManager activityStatusManager;

    @Autowired
    private ActivityPrizeMapper activityPrizeMapper;

    @Autowired
    private WinningRecordMapper winningRecordMapper;

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private org.springframework.amqp.rabbit.core.RabbitTemplate rabbitTemplate;

    @RabbitHandler
    public void process(Map<String, String> message) throws Exception {
        // 成功接收到队列中的消息
        logger.info("MQ成功接收到消息，message:{}",
                JacksonUtil.writeValueAsString(message));
        String paramString = message.get("messageData");
        DrawPrizeParam param = JacksonUtil.readValue(paramString, DrawPrizeParam.class);

        // 构造分布式锁 key，锁粒度：活动ID + 奖品ID
        // 保证多实例部署时，同一活动的同一奖品只能被一个消费者处理
        String lockKey = DRAW_LOCK_PREFIX + param.getActivityId() + ":" + param.getPrizeId();
        RLock lock = redissonClient.getLock(lockKey);

        boolean acquired = false;
        try {
            // 尝试获取锁：最多等待5秒，持有锁最多30秒
            acquired = lock.tryLock(LOCK_WAIT_TIME, LOCK_LEASE_TIME, TimeUnit.SECONDS);
            if (!acquired) {
                logger.warn("获取分布式锁失败，跳过本次抽奖处理。lockKey:{}", lockKey);
                return;
            }
            logger.info("成功获取分布式锁，开始处理抽奖。lockKey:{}", lockKey);

            // 校验抽奖请求是否有效
            // 1、有可能前端发起两个一样的抽奖请求，对于param来说也是一样的两个请求
            // 2、param：最后一个奖项-》
            //      处理param1：活动完成、奖品完成
            //      处理param2: 回滚活动、奖品状态
            if (!drawPrizeService.checkDrawPrizeParam(param)) {
                return;
            }

            // 状态扭转处理（重要！！ 设计模式）
            statusConvert(param);

            // 保存中奖者名单
            List<WinningRecordDO> winningRecordDOList =
                    drawPrizeService.saveWinnerRecords(param);

            // 架构 2.0 升级：斩尾！放弃原来的 JVM 内线程池同步阻塞调用，将获奖记录抛送至新的专用防抖缓冲通道。
            if (!CollectionUtils.isEmpty(winningRecordDOList)) {
                for (WinningRecordDO recordDO : winningRecordDOList) {
                    Map<String, String> aiMsg = new HashMap<>();
                    // 将单个中奖人员的全部信息发送出去，下游并发分批提货不受牵制
                    aiMsg.put("recordData", JacksonUtil.writeValueAsString(recordDO));
                    rabbitTemplate.convertAndSend(com.example.lotterysystem.common.config.DirectRabbitConfig.EXCHANGE_NAME, 
                                                  com.example.lotterysystem.common.config.DirectRabbitConfig.ROUTING_AI, 
                                                  aiMsg);
                }
            }

        } catch (ServiceException e) {
            logger.error("处理 MQ 消息异常！{}:{}", e.getCode(), e.getMessage(), e);
            // 需要保证事务一致性（回滚）
            rollback(param);
            // 抛出异常: 消息重试（解决异常：代码bug、网络问题、服务问题...）
            throw e;

        } catch (Exception e) {
            logger.error("处理 MQ 消息异常！", e);
            // 需要保证事务一致性（回滚）
            rollback(param);
            // 抛出异常
            throw e;
        } finally {
            // 释放锁：只有当前线程持有锁时才释放，避免误释放其他线程的锁
            if (acquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
                logger.info("分布式锁已释放。lockKey:{}", lockKey);
            }
        }

    }

    /**
     * 处理抽奖异常的回滚行为：恢复处理请求之前的库表状态
     *
     * @param param
     */
    private void rollback(DrawPrizeParam param) {

        // 1、回滚状态：活动、奖品、人员
        // 状态是否需要回滚
        if (!statusNeedRollback(param)) {
            // 不需要：return
            return;
        }
        // 需要回滚: 回滚
        rollbackStatus(param);

        // 2、回滚中奖者名单
        // 是否需要回滚
        if (!winnerNeedRollback(param)) {
            // 不需要：return
            return;
        }
        // 需要: 回滚
        rollbackWinner(param);

    }

    /**
     * 回滚中奖记录：删除奖品下的中奖者
     *
     * @param param
     */
    private void rollbackWinner(DrawPrizeParam param) {
        drawPrizeService.deleteRecords(param.getActivityId(), param.getPrizeId());
    }

    private boolean winnerNeedRollback(DrawPrizeParam param) {
        // 判断活动中的奖品是否存在中奖者
        int count = winningRecordMapper.countByAPId(param.getActivityId(), param.getPrizeId());
        return count > 0;
    }

    /**
     * 恢复相关状态
     *
     * @param param
     */
    private void rollbackStatus(DrawPrizeParam param) {
        // 涉及状态的恢复，使用 ActivityStatusManager
        ConvertActivityStatusDTO convertActivityStatusDTO = new ConvertActivityStatusDTO();
        convertActivityStatusDTO.setActivityId(param.getActivityId());
        convertActivityStatusDTO.setTargetActivityStatus(ActivityStatusEnum.RUNNING);
        convertActivityStatusDTO.setPrizeId(param.getPrizeId());
        convertActivityStatusDTO.setTargetPrizeStatus(ActivityPrizeStatusEnum.INIT);
        convertActivityStatusDTO.setUserIds(
                param.getWinnerList().stream()
                        .map(DrawPrizeParam.Winner::getUserId)
                        .collect(Collectors.toList())
        );
        convertActivityStatusDTO.setTargetUserStatus(ActivityUserStatusEnum.INIT);
        activityStatusManager.rollbackHandlerEvent(convertActivityStatusDTO);

    }

    private boolean statusNeedRollback(DrawPrizeParam param) {
        // 判断活动+奖品+人员表相关状态是否已经扭转（正常思路）
        // 扭转状态时，保证了事务一致性，要么都扭转了，要么都没扭转（不包含活动）：
        // 因此，只用判断人员/奖品是否扭转过，就能判断出状态是否全部扭转
        // 不能判断活动是否已经扭转
        // 结论：判断奖品状态是否扭转，就能判断出全部状态是否扭转
        ActivityPrizeDO activityPrizeDO =
                activityPrizeMapper.selectByAPId(param.getActivityId(), param.getPrizeId());
        // 已经扭转了，需要回滚
        return activityPrizeDO.getStatus()
                .equalsIgnoreCase(ActivityPrizeStatusEnum.COMPLETED.name());
    }

    // syncExecute, sendMail, sendMessage 已经随 2.0 架构升级全部下放入专用的 AiNotificationReceiver 中进行极限并发处理，故此段旧线程池阻塞代码彻底废弃。

    /**
     * 状态扭转
     *
     * @param param
     */
    private void statusConvert(DrawPrizeParam param) {
        ConvertActivityStatusDTO convertActivityStatusDTO = new ConvertActivityStatusDTO();
        convertActivityStatusDTO.setActivityId(param.getActivityId());
        convertActivityStatusDTO.setTargetActivityStatus(ActivityStatusEnum.COMPLETED);
        convertActivityStatusDTO.setPrizeId(param.getPrizeId());
        convertActivityStatusDTO.setTargetPrizeStatus(ActivityPrizeStatusEnum.COMPLETED);
        convertActivityStatusDTO.setUserIds(
                param.getWinnerList().stream()
                        .map(DrawPrizeParam.Winner::getUserId)
                        .collect(Collectors.toList())
        );
        convertActivityStatusDTO.setTargetUserStatus(ActivityUserStatusEnum.COMPLETED);
        activityStatusManager.handlerEvent(convertActivityStatusDTO);
    }

//  private void statusConvert(DrawPrizeParam param) {
        // 问题：
        // 1、活动状态扭转有依赖性，导致代码维护性差
        // 2、状态扭转条件可能会扩展，当前写法，扩展性差，维护性差
        // 3、代码的灵活性、扩展性、维护性极差
        // 解决方案：设计模式（责任链设计模式、策略模式）


        // 活动: RUNNING-->COMPLETED ??  全部奖品抽完之后才改变状态
        // 奖品：INIT-->COMPLETED
        // 人员列表: INIT-->COMPLETED

        // 1 扭转奖品状态
        // 查询活动关联的奖品信息
        // 条件判断是否符合扭转奖品状态：判断当前状态是否 不是COMPLETED，如果不是，要扭转
        // 才去扭转

        // 2 扭转人员状态
        // 查询活动关联的人员信息
        // 条件判断是否符合扭转人员状态：判断当前状态是否 不是COMPLETED，如果不是，要扭转
        // 才去扭转

        // (4、扭转xxx状态)

        // 3 扭转活动状态（必须再扭转奖品状态之后完成）
        // 查询活动信息
        // 条件判断是否符合扭转活动状态：才改变状态
        //      判断当前状态是否 不是COMPLETED，如果不是，
        //      且全部奖品抽完之后，
        //      （且xxx状态扭转）
        // 才去扭转

        // 4 更新活动完整信息缓存

//    }

}

package com.example.lotterysystem.service.impl;

import com.example.lotterysystem.common.errorcode.ServiceErrorCodeConstants;
import com.example.lotterysystem.common.exception.ServiceException;
import com.example.lotterysystem.common.utils.JacksonUtil;
import com.example.lotterysystem.common.utils.RedisUtil;
import com.example.lotterysystem.controller.param.DrawPrizeParam;
import com.example.lotterysystem.controller.param.ShowWinningRecordsParam;
import com.example.lotterysystem.dao.dateobject.*;
import com.example.lotterysystem.dao.mapper.*;
import com.example.lotterysystem.service.DrawPrizeService;
import com.example.lotterysystem.service.dto.WinningRecordDTO;
import com.example.lotterysystem.service.enums.ActivityPrizeStatusEnum;
import com.example.lotterysystem.service.enums.ActivityPrizeTiersEnum;
import com.example.lotterysystem.service.enums.ActivityStatusEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

import static com.example.lotterysystem.common.config.DirectRabbitConfig.EXCHANGE_NAME;
import static com.example.lotterysystem.common.config.DirectRabbitConfig.ROUTING;

@Service
public class DrawPrizeServiceImpl implements DrawPrizeService {

    private static final Logger logger = LoggerFactory.getLogger(DrawPrizeServiceImpl.class);

    /** 活动级分布式锁前缀：保证同一活动的并发抽奖请求在 API 入口串行化，防止并发读到相同候选人名单 */
    private static final String DRAW_LOCK_ACTIVITY_PREFIX = "DRAW_LOCK_ACTIVITY:";

    private final String WINNING_RECORDS_PREFIX = "WINNING_RECORDS_";
    private final Long WINNING_RECORDS_TIMEOUT = 60 * 60 * 24 * 2L;

    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Autowired
    private ActivityMapper activityMapper;
    @Autowired
    private ActivityPrizeMapper activityPrizeMapper;
    @Autowired
    private ActivityUserMapper activityUserMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private PrizeMapper prizeMapper;
    @Autowired
    private WinningRecordMapper winningRecordMapper;

    @Autowired
    private RedisUtil redisUtil;

    @Autowired
    private RedissonClient redissonClient;

    @Override
    public void drawPrize(DrawPrizeParam param) {

        // ── 活动级分布式锁（粗粒度）──────────────────────────────────────────────
        // 设计说明：
        // 同一活动下存在多个奖品，若并发触发不同奖品的抽奖请求，
        // 各请求会读取同一份 INIT 状态候选人名单，可能造成同一人被不同奖品同时选中。
        // 因此在 API 入口处以「活动维度」加锁，保证同一活动的所有抽奖请求串行执行。
        // MqReceiver 中的「奖品级细粒度锁」不受影响，仍负责防止 MQ 重复消费（幂等保障）。
        String activityLockKey = DRAW_LOCK_ACTIVITY_PREFIX + param.getActivityId();
        RLock activityLock = redissonClient.getLock(activityLockKey);
        boolean acquired;
        try {
            // 非阻塞：等待最多 3 秒，若仍无法获锁则说明有其他奖项正在抽取，直接拒绝本次请求
            acquired = activityLock.tryLock(3, 15, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServiceException(ServiceErrorCodeConstants.DRAW_PRIZE_IS_PROCESSING);
        }
        if (!acquired) {
            logger.warn("获取活动级抽奖锁失败，活动下有其他奖品正在抽取中，activityId={}", param.getActivityId());
            throw new ServiceException(ServiceErrorCodeConstants.DRAW_PRIZE_IS_PROCESSING);
        }

        try {
            // 后端独立随机抽取中奖者，前端不再传入 winnerList，防止前端篡改中奖结果

            // 1. 查询该奖品的数量（需要抽取的中奖人数）
            ActivityPrizeDO activityPrizeDO = activityPrizeMapper.selectByAPId(
                    param.getActivityId(), param.getPrizeId());
            if (activityPrizeDO == null) {
                throw new ServiceException(ServiceErrorCodeConstants.ACTIVITY_OR_PRIZE_IS_EMPTY);
            }
            int prizeAmount = activityPrizeDO.getPrizeAmount().intValue();

            // 2. 查询活动的全量参与者名单（状态为 INIT 的，即尚未中奖的参与者）
            List<ActivityUserDO> activityUserList = activityUserMapper.selectByActivityId(param.getActivityId());
            if (CollectionUtils.isEmpty(activityUserList)) {
                throw new ServiceException(ServiceErrorCodeConstants.ACTIVITY_USER_ERROR);
            }

            // 3. 过滤掉已中奖（COMPLETED）的参与者，只从状态为 INIT 的人中抽取
            List<ActivityUserDO> eligibleUsers = activityUserList.stream()
                    .filter(u -> "INIT".equalsIgnoreCase(u.getStatus()))
                    .collect(Collectors.toList());
            if (eligibleUsers.size() < prizeAmount) {
                throw new ServiceException(ServiceErrorCodeConstants.USER_PRIZE_AMOUNT_EROOR);
            }

            // 4. 使用 SecureRandom 打乱参与者顺序，取前 prizeAmount 个作为中奖者
            // SecureRandom 基于操作系统熵源，替代默认线性同余伪随机，消除随机序列可预测性
            Collections.shuffle(eligibleUsers, new java.security.SecureRandom());
            List<DrawPrizeParam.Winner> winnerList = eligibleUsers.subList(0, prizeAmount)
                    .stream()
                    .map(user -> {
                        DrawPrizeParam.Winner winner = new DrawPrizeParam.Winner();
                        winner.setUserId(user.getUserId());
                        winner.setUserName(user.getUserName());
                        return winner;
                    })
                    .collect(Collectors.toList());

            // 5. 将后端抽取的中奖者填充到 param 中
            param.setWinnerList(winnerList);
            logger.info("后端随机抽取中奖者完成，activityId={}，prizeId={}，中奖人数={}",
                    param.getActivityId(), param.getPrizeId(), winnerList.size());

            // 6. 发送 MQ，后续通知/落库逻辑完全不变
            Map<String, String> map = new HashMap<>();
            map.put("messageId", String.valueOf(UUID.randomUUID()));
            map.put("messageData", JacksonUtil.writeValueAsString(param));
            // 发消息 交换机、绑定的key、消息体
            rabbitTemplate.convertAndSend(EXCHANGE_NAME, ROUTING, map);
            logger.info("mq消息发送成功：map={}", JacksonUtil.writeValueAsString(map));

        } finally {
            // 确保锁一定会被释放，防止死锁
            if (activityLock.isHeldByCurrentThread()) {
                activityLock.unlock();
                logger.info("活动级抽奖锁已释放，activityId={}", param.getActivityId());
            }
        }
    }

    @Override
    public Boolean checkDrawPrizeParam(DrawPrizeParam param) {

        ActivityDO activityDO = activityMapper.selectById(param.getActivityId());
        // 奖品是否存在可以从 activity_prize表里查，原因是保存activity做了本地事务，保证一致性
        ActivityPrizeDO activityPrizeDO = activityPrizeMapper.selectByAPId(
                param.getActivityId(), param.getPrizeId());

        // 活动或奖品是否存在
        if (null == activityDO || null == activityPrizeDO) {
            // throw new
            // ServiceException(ServiceErrorCodeConstants.ACTIVITY_OR_PRIZE_IS_EMPTY);
            logger.info("校验抽奖请求失败！失败原因：{}",
                    ServiceErrorCodeConstants.ACTIVITY_OR_PRIZE_IS_EMPTY.getMsg());
            return false;
        }

        // 活动是否有效
        if (activityDO.getStatus()
                .equalsIgnoreCase(ActivityStatusEnum.COMPLETED.name())) {
            // throw new ServiceException(ServiceErrorCodeConstants.ACTIVITY_COMPLETED);
            logger.info("校验抽奖请求失败！失败原因：{}",
                    ServiceErrorCodeConstants.ACTIVITY_COMPLETED.getMsg());
            return false;
        }

        // 奖品是否有效
        if (activityPrizeDO.getStatus()
                .equalsIgnoreCase(ActivityPrizeStatusEnum.COMPLETED.name())) {
            // throw new
            // ServiceException(ServiceErrorCodeConstants.ACTIVITY_PRIZE_COMPLETED);
            logger.info("校验抽奖请求失败！失败原因：{}",
                    ServiceErrorCodeConstants.ACTIVITY_PRIZE_COMPLETED.getMsg());
            return false;
        }

        // 注：中奖者人数校验已在 drawPrize() 内后端抽取时保证，此处无需重复校验
        return true;
    }

    @Override
    public List<WinningRecordDO> saveWinnerRecords(DrawPrizeParam param) {
        // 查询相关信息：活动、人员、奖品、活动关联奖品
        ActivityDO activityDO = activityMapper.selectById(param.getActivityId());
        List<UserDO> userDOList = userMapper.batchSelectByIds(
                param.getWinnerList()
                        .stream()
                        .map(DrawPrizeParam.Winner::getUserId)
                        .collect(Collectors.toList()));
        PrizeDO prizeDO = prizeMapper.selectById(param.getPrizeId());
        ActivityPrizeDO activityPrizeDO = activityPrizeMapper.selectByAPId(param.getActivityId(), param.getPrizeId());

        // 构造中奖者记录，保存

        List<WinningRecordDO> winningRecordDOList = userDOList.stream()
                .map(userDO -> {
                    WinningRecordDO winningRecordDO = new WinningRecordDO();
                    winningRecordDO.setActivityId(activityDO.getId());
                    winningRecordDO.setActivityName(activityDO.getActivityName());
                    winningRecordDO.setPrizeId(prizeDO.getId());
                    winningRecordDO.setPrizeName(prizeDO.getName());
                    winningRecordDO.setPrizeTier(activityPrizeDO.getPrizeTiers());
                    winningRecordDO.setWinnerId(userDO.getId());
                    winningRecordDO.setWinnerName(userDO.getUserName());
                    winningRecordDO.setWinnerEmail(userDO.getEmail());
                    winningRecordDO.setWinnerPhoneNumber(userDO.getPhoneNumber());
                    winningRecordDO.setWinningTime(param.getWinningTime());
                    return winningRecordDO;
                }).collect(Collectors.toList());
        winningRecordMapper.batchInsert(winningRecordDOList);

        // 缓存中奖者记录
        // 1、缓存奖品维度中奖记录(WinningRecord_activityId_prizeId,
        // winningRecordDOList（奖品维度的中奖名单）)
        cacheWinningRecords(param.getActivityId() + "_" + param.getPrizeId(),
                winningRecordDOList,
                WINNING_RECORDS_TIMEOUT);

        // 2、缓存活动维度中奖记录(WinningRecord_activityId, winningRecordDOList(活动维度的中奖名单))
        // 当活动已完成再去存放活动维度中奖记录
        if (activityDO.getStatus()
                .equalsIgnoreCase(ActivityStatusEnum.COMPLETED.name())) {
            // 查询活动维度的全量中奖记录
            List<WinningRecordDO> allList = winningRecordMapper.selectByActivityId(param.getActivityId());
            cacheWinningRecords(String.valueOf(param.getActivityId()),
                    allList,
                    WINNING_RECORDS_TIMEOUT);
        }

        return winningRecordDOList;

    }

    @Override
    public void deleteRecords(Long activityId, Long prizeId) {
        if (null == activityId) {
            logger.warn("要删除中奖记录相关的活动id为空！");
            return;
        }
        // 删除数据表
        winningRecordMapper.deleteRecords(activityId, prizeId);

        // 删除缓存（奖品维度、活动维度）
        if (null != prizeId) {
            deleteWinningRecords(activityId + "_" + prizeId);
        }
        // 无论是否传递了prizeId，都需要删除活动维度的中奖记录缓存：
        // 如果传递了prizeId, 证明奖品未抽奖，必须删除活动维度的缓存记录
        // 如果没有传递prizeId，就只是删除活动维度的信息
        deleteWinningRecords(String.valueOf(activityId));
    }

    @Override
    public List<WinningRecordDTO> getRecords(ShowWinningRecordsParam param) {
        // 查询redis: 奖品、活动
        String key = null == param.getPrizeId()
                ? String.valueOf(param.getActivityId())
                : param.getActivityId() + "_" + param.getPrizeId();
        List<WinningRecordDO> winningRecordDOList = getWinningRecords(key);
        if (!CollectionUtils.isEmpty(winningRecordDOList)) {
            return convertToWinningRecordDTOList(winningRecordDOList);
        }
        // 如果redis不存在，查库
        winningRecordDOList = winningRecordMapper.selectByActivityIdOrPrizeId(
                param.getActivityId(), param.getPrizeId());
        // 存放记录到redis
        if (CollectionUtils.isEmpty(winningRecordDOList)) {
            logger.info("查询的中奖记录为空！param:{}",
                    JacksonUtil.writeValueAsString(param));
            return Arrays.asList();
        }
        cacheWinningRecords(key, winningRecordDOList, WINNING_RECORDS_TIMEOUT);
        return convertToWinningRecordDTOList(winningRecordDOList);
    }

    private List<WinningRecordDTO> convertToWinningRecordDTOList(
            List<WinningRecordDO> winningRecordDOList) {
        if (CollectionUtils.isEmpty(winningRecordDOList)) {
            return Arrays.asList();
        }
        return winningRecordDOList.stream()
                .map(winningRecordDO -> {
                    WinningRecordDTO winningRecordDTO = new WinningRecordDTO();
                    winningRecordDTO.setWinnerId(winningRecordDO.getWinnerId());
                    winningRecordDTO.setWinnerName(winningRecordDO.getWinnerName());
                    winningRecordDTO.setPrizeName(winningRecordDO.getPrizeName());
                    winningRecordDTO.setPrizeTier(
                            ActivityPrizeTiersEnum.forName(winningRecordDO.getPrizeTier()));
                    winningRecordDTO.setWinningTime(winningRecordDO.getWinningTime());
                    return winningRecordDTO;
                }).collect(Collectors.toList());
    }

    /**
     * 从缓存中删除中奖记录
     *
     * @param key
     */
    private void deleteWinningRecords(String key) {
        try {
            if (redisUtil.hasKey(WINNING_RECORDS_PREFIX + key)) {
                // 存在再删除
                redisUtil.del(WINNING_RECORDS_PREFIX + key);
            }
        } catch (Exception e) {
            logger.error("删除中奖记录缓存异常，key:{}", key);
        }
    }

    /**
     * 缓存中奖记录
     *
     * @param key
     * @param winningRecordDOList
     * @param time
     */
    private void cacheWinningRecords(String key,
            List<WinningRecordDO> winningRecordDOList,
            Long time) {
        String str = "";
        try {
            if (!StringUtils.hasText(key)
                    || CollectionUtils.isEmpty(winningRecordDOList)) {
                logger.warn("要缓存的内容为空！key:{}, value:{}",
                        key, JacksonUtil.writeValueAsString(winningRecordDOList));
                return;
            }

            str = JacksonUtil.writeValueAsString(winningRecordDOList);
            redisUtil.set(WINNING_RECORDS_PREFIX + key,
                    str,
                    time);
        } catch (Exception e) {
            logger.error("缓存中奖记录异常！key:{}, value:{}", WINNING_RECORDS_PREFIX + key, str);
        }
    }

    /**
     * 从缓存中获取中奖记录
     *
     * @param key
     * @return
     */
    private List<WinningRecordDO> getWinningRecords(String key) {
        try {
            if (!StringUtils.hasText(key)) {
                logger.warn("要从缓存中查询中奖记录的key为空！");
                return Arrays.asList();
            }
            String str = redisUtil.get(WINNING_RECORDS_PREFIX + key);
            if (!StringUtils.hasText(str)) {
                return Arrays.asList();
            }

            return JacksonUtil.readListValue(str, WinningRecordDO.class);
        } catch (Exception e) {
            logger.error("从缓存中查询中奖记录异常！key:{}", WINNING_RECORDS_PREFIX + key);
            return Arrays.asList();
        }
    }

}

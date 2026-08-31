package com.example.lotterysystem.service.mq;

import com.example.lotterysystem.common.utils.JacksonUtil;
import com.example.lotterysystem.controller.param.DrawPrizeParam;
import com.example.lotterysystem.dao.dateobject.DlxMessageDO;
import com.example.lotterysystem.dao.mapper.DlxMessageMapper;
import com.example.lotterysystem.service.DrawReservationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.Map;

import static com.example.lotterysystem.common.config.DirectRabbitConfig.EXCHANGE_NAME;
import static com.example.lotterysystem.common.config.DirectRabbitConfig.ROUTING;

/**
 * 死信消息定时重试任务
 * 定时扫描dlx_message表中待处理的消息，重新投递到普通队列
 * 超过最大重试次数的消息标记为FAILED，等待人工介入
 */
@Component
public class DlxRetryTask {

    private static final Logger logger = LoggerFactory.getLogger(DlxRetryTask.class);

    /**
     * 每次扫描处理的最大消息数量，防止一次处理过多
     */
    private static final int BATCH_SIZE = 10;

    @Autowired
    private DlxMessageMapper dlxMessageMapper;

    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Autowired
    private DrawReservationService drawReservationService;

    /**
     * 第3步 + 第4步：定时扫描异常消息表，对待处理的消息进行重试
     * 每30秒执行一次
     */
    @Scheduled(fixedDelay = 30000)
    public void retryFailedMessages() {
        // 查询待重试的消息（状态为PENDING且已到达重试时间）
        List<DlxMessageDO> pendingMessages = dlxMessageMapper.selectPendingMessages(BATCH_SIZE);

        if (CollectionUtils.isEmpty(pendingMessages)) {
            return;
        }

        logger.info("扫描到{}条待重试的死信消息", pendingMessages.size());

        for (DlxMessageDO dlxMessage : pendingMessages) {
            try {
                // 判断是否超过最大重试次数
                if (dlxMessage.getRetryCount() >= dlxMessage.getMaxRetry()) {
                    // 超过重试上限，标记为 FAILED 并释放未完成的预占，避免人员/奖品永久卡在 PROCESSING。
                    dlxMessageMapper.updateStatus(dlxMessage.getId(), "FAILED");
                    releaseReservation(dlxMessage);
                    logger.error("死信消息重试次数已达上限，标记为FAILED，需人工处理！" +
                                    "messageId:{}, retryCount:{}/{}",
                            dlxMessage.getMessageId(),
                            dlxMessage.getRetryCount(),
                            dlxMessage.getMaxRetry());
                    // 扩展点：此处可接入告警通知（钉钉/企微/邮件）
                    continue;
                }

                // 将消息重新投递到普通队列
                Map<String, String> messageMap = JacksonUtil.readMapValue(
                        dlxMessage.getMessageBody(), String.class, String.class);
                rabbitTemplate.convertAndSend(EXCHANGE_NAME, ROUTING, messageMap);

                // 更新重试次数和下次重试时间
                dlxMessage.setRetryCount(dlxMessage.getRetryCount() + 1);
                dlxMessage.setStatus("PENDING");
                dlxMessage.setNextRetryTime(
                        DlxReceiver.calculateNextRetryTime(dlxMessage.getRetryCount()));
                dlxMessageMapper.updateRetryInfo(dlxMessage);

                logger.info("死信消息重新投递成功！messageId:{}, 当前重试第{}次/最大{}次",
                        dlxMessage.getMessageId(),
                        dlxMessage.getRetryCount(),
                        dlxMessage.getMaxRetry());

            } catch (Exception e) {
                logger.error("重试死信消息失败！messageId:{}", dlxMessage.getMessageId(), e);
            }
        }
    }

    private void releaseReservation(DlxMessageDO dlxMessage) {
        try {
            Map<String, String> messageMap = JacksonUtil.readMapValue(
                    dlxMessage.getMessageBody(), String.class, String.class);
            DrawPrizeParam param = JacksonUtil.readValue(messageMap.get("messageData"), DrawPrizeParam.class);
            drawReservationService.release(param);
        } catch (Exception e) {
            logger.error("死信消息最终失败后释放预占失败！messageId:{}", dlxMessage.getMessageId(), e);
        }
    }
}

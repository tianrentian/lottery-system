package com.example.lotterysystem.service.mq;

import cn.hutool.http.HttpRequest;
import com.example.lotterysystem.common.config.DirectRabbitConfig;
import com.example.lotterysystem.common.utils.JacksonUtil;
import com.example.lotterysystem.common.utils.MailUtil;
import com.example.lotterysystem.dao.dateobject.WinningRecordDO;
import com.example.lotterysystem.service.ai.notification.AiNotificationTemplateService;
import com.example.lotterysystem.service.ai.notification.RenderedNotification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;

@Component
@RabbitListener(queues = DirectRabbitConfig.QUEUE_AI_NOTIFICATION)
public class AiNotificationReceiver {

    private static final Logger logger = LoggerFactory.getLogger(AiNotificationReceiver.class);

    @Autowired
    private AiNotificationTemplateService aiNotificationTemplateService;

    @Autowired
    private ThreadPoolTaskExecutor threadPoolTaskExecutor;

    @Autowired
    private MailUtil mailUtil;

    @Value("${wechat.robot.webhook-url}")
    private String webhookUrl;

    @RabbitHandler
    public void process(Map<String, String> message) {
        WinningRecordDO recordDO;
        try {
            String recordData = message.get("recordData");
            recordDO = JacksonUtil.readValue(recordData, WinningRecordDO.class);
            logger.info("开始准备中奖通知，activityId={}，prizeId={}，winnerId={}",
                    recordDO.getActivityId(), recordDO.getPrizeId(), recordDO.getWinnerId());
        } catch (Exception e) {
            logger.error("中奖通知消息解析失败", e);
            return;
        }

        RenderedNotification notification = aiNotificationTemplateService.resolve(recordDO);
        final String mailAddress = recordDO.getWinnerEmail();

        threadPoolTaskExecutor.execute(() -> {
            logger.info("开始发送中奖邮件，winnerId={}", recordDO.getWinnerId());
            mailUtil.sendSampleMail(
                    mailAddress, notification.getMailSubject(), notification.getPersonalText());
        });

        threadPoolTaskExecutor.execute(() -> {
            logger.info("开始发送钉钉群中奖通知，winnerId={}", recordDO.getWinnerId());
            try {
                Map<String, Object> payload = Map.of(
                        "msgtype", "text",
                        "text", Collections.singletonMap("content", notification.getGroupText()));
                HttpRequest.post(webhookUrl)
                        .header("Content-Type", "application/json")
                        .body(JacksonUtil.writeValueAsString(payload))
                        .timeout(3000)
                        .execute();
                logger.info("钉钉群中奖通知发送完成，winnerId={}", recordDO.getWinnerId());
            } catch (Exception error) {
                logger.error("钉钉群中奖通知发送失败，winnerId={}",
                        recordDO.getWinnerId(), error);
            }
        });
    }
}

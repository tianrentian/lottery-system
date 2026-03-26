package com.example.lotterysystem.service.mq;

import com.example.lotterysystem.common.utils.JacksonUtil;
import com.example.lotterysystem.dao.dateobject.DlxMessageDO;
import com.example.lotterysystem.dao.mapper.DlxMessageMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Calendar;
import java.util.Date;
import java.util.Map;

import static com.example.lotterysystem.common.config.DirectRabbitConfig.*;

/**
 * 死信队列消费者
 * 正确处理方案：将异常消息持久化到数据库，由定时任务负责重试
 * 避免直接重发导致的无限循环问题
 */
@Component
@RabbitListener(queues = DLX_QUEUE_NAME)
public class DlxReceiver {

    private static final Logger logger = LoggerFactory.getLogger(DlxReceiver.class);

    /**
     * 默认最大重试次数
     */
    private static final int DEFAULT_MAX_RETRY = 3;

    /**
     * 首次重试延迟（秒）：30秒后重试
     */
    private static final int INITIAL_RETRY_DELAY_SECONDS = 30;

    @Autowired
    private DlxMessageMapper dlxMessageMapper;

    @RabbitHandler
    public void process(Map<String, String> message) {
        // 之前死信队列的处理方法
        // logger.info("开始处理异常消息！");
        // rabbitTemplate.convertAndSend(EXCHANGE_NAME, ROUTING, message);
        // 该流程是有问题的，在这里只是为了演示处理过程中发生异常：消息堆积-》处理异常-》消息重发

        // 现在正确方案：
        logger.info("死信队列接收到异常消息，开始持久化处理！message:{}",
                JacksonUtil.writeValueAsString(message));

        try {
            // 第1步：将异常消息持久化到数据库表中
            DlxMessageDO dlxMessageDO = new DlxMessageDO();
            dlxMessageDO.setMessageId(message.get("messageId"));
            dlxMessageDO.setMessageBody(JacksonUtil.writeValueAsString(message));
            dlxMessageDO.setErrorMsg("MQ消费失败，重试次数耗尽后进入死信队列");
            dlxMessageDO.setRetryCount(0);
            dlxMessageDO.setMaxRetry(DEFAULT_MAX_RETRY);
            dlxMessageDO.setStatus("PENDING");
            // 计算下次重试时间：当前时间 + 30秒
            dlxMessageDO.setNextRetryTime(calculateNextRetryTime(0));

            dlxMessageMapper.insert(dlxMessageDO);
            logger.info("异常消息已持久化到数据库，messageId:{}, 等待定时任务重试",
                    message.get("messageId"));

            // 第2步：消息正常消费完成（ACK），死信队列不再堆积
            // 方法正常返回即自动ACK

        } catch (Exception e) {
            // 持久化本身失败，记录日志，消息将被丢弃
            // 生产环境可接入告警系统（如钉钉/企微机器人通知）
            logger.error("持久化死信消息失败！该消息将丢失，需人工介入。message:{}",
                    JacksonUtil.writeValueAsString(message), e);
        }
    }

    /**
     * 计算下次重试时间（指数退避策略）
     * 第0次重试：30秒后
     * 第1次重试：60秒后
     * 第2次重试：120秒后
     *
     * @param currentRetryCount 当前已重试次数
     * @return 下次重试时间
     */
    public static Date calculateNextRetryTime(int currentRetryCount) {
        Calendar calendar = Calendar.getInstance();
        // 指数退避：delay = INITIAL_RETRY_DELAY_SECONDS * 2^retryCount
        int delaySeconds = INITIAL_RETRY_DELAY_SECONDS * (1 << currentRetryCount);
        calendar.add(Calendar.SECOND, delaySeconds);
        return calendar.getTime();
    }
}

package com.example.lotterysystem.dao.dateobject;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * 死信消息持久化对象
 * 用于存储消费失败的MQ消息，支持定时重试和人工介入
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DlxMessageDO extends BaseDO {

    /**
     * 消息唯一标识
     */
    private String messageId;

    /**
     * 消息体（JSON格式）
     */
    private String messageBody;

    /**
     * 异常原因
     */
    private String errorMsg;

    /**
     * 已重试次数
     */
    private Integer retryCount;

    /**
     * 最大重试次数
     */
    private Integer maxRetry;

    /**
     * 处理状态：PENDING-待处理, RETRYING-重试中, SUCCESS-处理成功, FAILED-人工处理
     */
    private String status;

    /**
     * 下次重试时间
     */
    private Date nextRetryTime;
}

package com.example.lotterysystem.service.ai.notification;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/** 发送给 Python AI 服务的通知生成上下文，不包含中奖者个人信息。 */
@Data
public class NotificationTemplateRequest {

    @JsonProperty("activity_name")
    private String activityName;

    @JsonProperty("activity_description")
    private String activityDescription;

    @JsonProperty("prize_name")
    private String prizeName;

    @JsonProperty("prize_description")
    private String prizeDescription;

    @JsonProperty("prize_tier")
    private String prizeTier;
}

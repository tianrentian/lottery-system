package com.example.lotterysystem.service.ai.notification;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.io.Serializable;

/** 同一创意下分别面向中奖者本人和群成员的两种通知文案。 */
@Data
public class NotificationVariant implements Serializable {

    @JsonProperty("personal_text")
    private String personalText;

    @JsonProperty("group_text")
    private String groupText;
}

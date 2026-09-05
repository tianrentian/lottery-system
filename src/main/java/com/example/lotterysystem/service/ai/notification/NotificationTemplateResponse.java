package com.example.lotterysystem.service.ai.notification;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/** Python AI 服务生成的可复用通知模板集合。 */
@Data
public class NotificationTemplateResponse implements Serializable {

    @JsonProperty("mail_subject")
    private String mailSubject;

    private List<NotificationVariant> variants = new ArrayList<>();
}

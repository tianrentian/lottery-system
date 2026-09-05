package com.example.lotterysystem.service.ai.notification;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 已经填入中奖者姓名、可以直接发送的通知内容。 */
@Getter
@AllArgsConstructor
public class RenderedNotification {

    private final String mailSubject;
    private final String personalText;
    private final String groupText;
}

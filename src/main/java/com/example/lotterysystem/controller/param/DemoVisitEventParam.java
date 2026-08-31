package com.example.lotterysystem.controller.param;

import com.example.lotterysystem.service.enums.DemoVisitEventType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.io.Serializable;

@Data
public class DemoVisitEventParam implements Serializable {

    @Pattern(
            regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89aAbB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$",
            message = "匿名会话编号格式错误"
    )
    private String sessionId;

    @NotNull(message = "访问事件不能为空")
    private DemoVisitEventType eventType;
}

package com.example.lotterysystem.controller.param;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

/**
 * 管理员提交的 AI 活动策划需求。
 * 奖品目录由后端从数据库查询，不由浏览器提交，避免客户端篡改目录。
 */
@Data
public class AiActivityPlanParam implements Serializable {

    @NotBlank(message = "AI策划需求不能为空！")
    @Size(max = 300, message = "AI策划需求不能超过300字！")
    private String prompt;

    @Size(max = 100, message = "澄清答案不能超过100字！")
    private String clarificationAnswer;

    @DecimalMin(value = "0", message = "预算不能为负数！")
    private java.math.BigDecimal hardBudget;

    @Size(max = 64, message = "策划会话标识不能超过64位！")
    private String sessionId;
}

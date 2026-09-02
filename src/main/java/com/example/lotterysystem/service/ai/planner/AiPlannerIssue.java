package com.example.lotterysystem.service.ai.planner;

import lombok.Data;

import java.io.Serializable;

/** AI 草稿的确定性校验问题。 */
@Data
public class AiPlannerIssue implements Serializable {

    private String field;
    private String code;
    private String message;
}

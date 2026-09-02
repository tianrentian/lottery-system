package com.example.lotterysystem.service.ai.planner;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Python AI Planner 的内部请求结构。 */
@Data
public class AiPlannerRequest implements Serializable {

    private String prompt;

    @JsonProperty("available_prizes")
    private List<PrizeOption> availablePrizes = new ArrayList<>();

    @JsonProperty("clarification_answer")
    private String clarificationAnswer;

    @JsonProperty("hard_budget")
    private BigDecimal hardBudget;
}

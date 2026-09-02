package com.example.lotterysystem.service.ai.planner;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/** Python AI Planner 返回的工作流状态。 */
@Data
public class AiPlannerResponse implements Serializable {

    @JsonProperty("session_id")
    private String sessionId;

    private String status;
    private ActivityPlanDraft draft;
    private List<AiPlannerIssue> issues = new ArrayList<>();
    private List<String> assumptions = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();

    @JsonProperty("participant_hint")
    private String participantHint;

    @JsonProperty("clarification_question")
    private String clarificationQuestion;

    @JsonProperty("clarification_options")
    private List<String> clarificationOptions = new ArrayList<>();

    @JsonProperty("repair_attempts")
    private Integer repairAttempts;

    @JsonProperty("error_message")
    private String errorMessage;
}

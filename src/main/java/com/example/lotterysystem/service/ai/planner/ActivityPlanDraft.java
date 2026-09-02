package com.example.lotterysystem.service.ai.planner;

import lombok.Data;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * AI策划产生的活动草稿，不代表已经创建活动。
 */
@Data
public class ActivityPlanDraft implements Serializable {

    @JsonProperty("activity_name")
    private String activityName;

    @JsonProperty("description")
    private String description;

    @JsonProperty("prizes")
    private List<ActivityPlanPrize> prizes = new ArrayList<>();

    @JsonProperty("assumptions")
    private List<String> assumptions = new ArrayList<>();

    @JsonProperty("warnings")
    private List<String> warnings = new ArrayList<>();

    @JsonProperty("participant_hint")
    private String participantHint;
}

package com.example.lotterysystem.service.ai.planner;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * AI策划产生的活动草稿，不代表已经创建活动。
 */
@Data
public class ActivityPlanDraft implements Serializable {

    private String activityName;

    private String description;

    private List<ActivityPlanPrize> prizes = new ArrayList<>();

    private List<String> assumptions = new ArrayList<>();

    private List<String> warnings = new ArrayList<>();

    private String participantHint;
}

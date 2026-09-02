package com.example.lotterysystem.service.ai.planner;

import java.util.List;

/**
 * 活动策划生成器抽象，真实DeepSeek实现和本地Mock实现共用此契约。
 */
public interface ActivityPlanGenerator {

    ActivityPlanDraft generate(String prompt, List<PrizeOption> availablePrizes);
}

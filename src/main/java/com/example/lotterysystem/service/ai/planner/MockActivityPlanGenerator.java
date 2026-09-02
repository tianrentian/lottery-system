package com.example.lotterysystem.service.ai.planner;

import com.example.lotterysystem.service.enums.ActivityPrizeTiersEnum;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;

/**
 * 本地确定性Mock生成器，不调用任何外部模型，用于开发和流程测试。
 */
public class MockActivityPlanGenerator implements ActivityPlanGenerator {

    @Override
    public ActivityPlanDraft generate(String prompt, List<PrizeOption> availablePrizes) {
        if (!StringUtils.hasText(prompt)) {
            throw new IllegalArgumentException("AI策划需求不能为空");
        }

        ActivityPlanDraft draft = new ActivityPlanDraft();
        draft.setActivityName("AI策划抽奖活动");
        draft.setDescription("根据您的活动需求生成的抽奖活动方案。");
        draft.getAssumptions().add("未指定预算，按基础规模抽奖生成");

        List<PrizeOption> prizes = availablePrizes == null
                ? Collections.emptyList()
                : availablePrizes;
        int prizeCount = Math.min(ActivityPrizeTiersEnum.values().length, prizes.size());
        long totalAmount = 0L;
        for (int i = 0; i < prizeCount; i++) {
            ActivityPlanPrize planPrize = new ActivityPlanPrize();
            planPrize.setPrizeId(prizes.get(i).getPrizeId());
            planPrize.setPrizeAmount((long) i + 1);
            planPrize.setPrizeTiers(ActivityPrizeTiersEnum.values()[i].name());
            draft.getPrizes().add(planPrize);
            totalAmount += i + 1L;
        }

        if (prizeCount == 0) {
            draft.getWarnings().add("当前没有可用奖品，请手动圈选奖品。");
        } else {
            draft.getAssumptions().add("未指定奖品数量，共设置" + totalAmount + "份奖品");
        }
        return draft;
    }
}

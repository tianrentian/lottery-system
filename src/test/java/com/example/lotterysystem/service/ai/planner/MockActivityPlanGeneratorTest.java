package com.example.lotterysystem.service.ai.planner;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MockActivityPlanGeneratorTest {

    private final MockActivityPlanGenerator generator = new MockActivityPlanGenerator();

    @Test
    void shouldGenerateDeterministicDraftFromAvailablePrizes() {
        PrizeOption first = prize(1L, "耳机");
        PrizeOption second = prize(2L, "保温杯");
        PrizeOption third = prize(3L, "咖啡券");
        PrizeOption fourth = prize(4L, "笔记本");

        ActivityPlanDraft draft = generator.generate("年会抽奖", Arrays.asList(first, second, third, fourth));

        assertEquals("AI策划抽奖活动", draft.getActivityName());
        assertEquals(3, draft.getPrizes().size());
        assertEquals(1L, draft.getPrizes().get(0).getPrizeAmount());
        assertEquals(2L, draft.getPrizes().get(1).getPrizeAmount());
        assertEquals(3L, draft.getPrizes().get(2).getPrizeAmount());
        assertEquals("FIRST_PRIZE", draft.getPrizes().get(0).getPrizeTiers());
        assertEquals("THIRD_PRIZE", draft.getPrizes().get(2).getPrizeTiers());
        assertEquals(2, draft.getAssumptions().size());
    }

    @Test
    void shouldWarnWhenNoPrizeIsAvailable() {
        ActivityPlanDraft draft = generator.generate("年会抽奖", Collections.emptyList());

        assertEquals(0, draft.getPrizes().size());
        assertEquals(1, draft.getWarnings().size());
    }

    @Test
    void shouldRejectBlankPrompt() {
        assertThrows(IllegalArgumentException.class,
                () -> generator.generate(" ", Collections.emptyList()));
    }

    private PrizeOption prize(Long id, String name) {
        PrizeOption prize = new PrizeOption();
        prize.setPrizeId(id);
        prize.setName(name);
        return prize;
    }
}

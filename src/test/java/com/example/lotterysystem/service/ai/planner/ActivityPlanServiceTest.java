package com.example.lotterysystem.service.ai.planner;

import com.example.lotterysystem.common.exception.ServiceException;
import com.example.lotterysystem.common.utils.JacksonUtil;
import com.example.lotterysystem.common.utils.RedisUtil;
import com.example.lotterysystem.service.PrizeService;
import com.example.lotterysystem.service.dto.PrizeDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActivityPlanServiceTest {

    @Mock
    private PrizeService prizeService;
    @Mock
    private AiPlannerClient aiPlannerClient;
    @Mock
    private RedisUtil redisUtil;
    @InjectMocks
    private ActivityPlanService activityPlanService;

    @Test
    void generateShouldPassPrizeCatalogAndCacheDraftForThirtyMinutes() {
        when(prizeService.findAllForPlanning()).thenReturn(Collections.singletonList(prize(7L, "耳机", "99")));
        when(aiPlannerClient.plan(eq("公司年会"), anyList(), isNull(BigDecimal.class), isNull(String.class)))
                .thenReturn(readyPlan(7L));

        AiPlannerResponse response = activityPlanService.generate("公司年会", null, null, "session-1");

        assertEquals("session-1", response.getSessionId());
        assertEquals("READY", response.getStatus());
        verify(redisUtil).set(eq("AI_PLANNING:session-1"), anyString(), eq(1800L));
    }

    @Test
    void generateShouldMarkUnknownPrizeForManualConfirmation() {
        when(prizeService.findAllForPlanning()).thenReturn(Collections.singletonList(prize(7L, "耳机", "99")));
        when(aiPlannerClient.plan(eq("年会"), anyList(), isNull(BigDecimal.class), isNull(String.class)))
                .thenReturn(readyPlan(999L));

        AiPlannerResponse response = activityPlanService.generate("年会", null, null, "session-2");

        assertEquals("NEEDS_MANUAL", response.getStatus());
        assertEquals("PRIZE_NOT_FOUND", response.getIssues().get(0).getCode());
        verify(redisUtil).set(eq("AI_PLANNING:session-2"), anyString(), eq(1800L));
    }

    @Test
    void restoreShouldReadCachedDraftByNamespacedSessionKey() {
        AiPlannerResponse saved = readyPlan(7L);
        saved.setSessionId("session-3");
        when(redisUtil.get("AI_PLANNING:session-3"))
                .thenReturn(JacksonUtil.writeValueAsString(saved));

        AiPlannerResponse restored = activityPlanService.restore("session-3");

        assertEquals("session-3", restored.getSessionId());
        assertEquals("年会抽奖", restored.getDraft().getActivityName());
    }

    @Test
    void restoreShouldRejectMissingSession() {
        when(redisUtil.get("AI_PLANNING:missing")).thenReturn(null);

        assertThrows(ServiceException.class, () -> activityPlanService.restore("missing"));
    }

    private PrizeDTO prize(Long id, String name, String price) {
        PrizeDTO prize = new PrizeDTO();
        prize.setPrizeId(id);
        prize.setName(name);
        prize.setPrice(new BigDecimal(price));
        return prize;
    }

    private AiPlannerResponse readyPlan(Long prizeId) {
        ActivityPlanPrize planPrize = new ActivityPlanPrize();
        planPrize.setPrizeId(prizeId);
        planPrize.setPrizeAmount(1L);
        planPrize.setPrizeTiers("FIRST_PRIZE");

        ActivityPlanDraft draft = new ActivityPlanDraft();
        draft.setActivityName("年会抽奖");
        draft.setDescription("面向全体员工的年会抽奖");
        draft.setPrizes(Collections.singletonList(planPrize));

        AiPlannerResponse response = new AiPlannerResponse();
        response.setStatus("READY");
        response.setDraft(draft);
        return response;
    }
}

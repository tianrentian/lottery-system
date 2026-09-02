package com.example.lotterysystem.service.ai.planner;

import com.example.lotterysystem.service.PrizeService;
import com.example.lotterysystem.common.errorcode.ServiceErrorCodeConstants;
import com.example.lotterysystem.common.exception.ServiceException;
import com.example.lotterysystem.common.utils.JacksonUtil;
import com.example.lotterysystem.common.utils.RedisUtil;
import com.example.lotterysystem.service.dto.PrizeDTO;
import com.example.lotterysystem.service.enums.ActivityPrizeTiersEnum;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * AI 策划业务门面：准备奖品目录，并在返回页面前做一次 Java 侧安全校验。
 */
@Service
public class ActivityPlanService {

    private static final String AI_PLANNING_PREFIX = "AI_PLANNING:";
    private static final long AI_PLANNING_TIMEOUT_SECONDS = 30 * 60L;

    @Autowired
    private PrizeService prizeService;
    @Autowired
    private AiPlannerClient aiPlannerClient;
    @Autowired
    private RedisUtil redisUtil;

    public AiPlannerResponse generate(String prompt, BigDecimal hardBudget,
                                      String clarificationAnswer, String requestedSessionId) {
        List<PrizeOption> catalog = toPrizeOptions(prizeService.findAllForPlanning());
        AiPlannerResponse response = aiPlannerClient.plan(
                prompt, catalog, hardBudget, clarificationAnswer);
        validateReadyResponse(response, catalog, hardBudget);
        String sessionId = StringUtils.hasText(requestedSessionId)
                ? requestedSessionId.trim()
                : UUID.randomUUID().toString().replace("-", "");
        response.setSessionId(sessionId);
        cacheResponse(response);
        return response;
    }

    /**
     * 刷新页面后按会话恢复最近一次 AI 草稿，草稿最多保留30分钟。
     */
    public AiPlannerResponse restore(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            throw new ServiceException(ServiceErrorCodeConstants.AI_PLANNER_STATE_NOT_FOUND);
        }
        String content = redisUtil.get(cacheKey(sessionId));
        if (!StringUtils.hasText(content)) {
            throw new ServiceException(ServiceErrorCodeConstants.AI_PLANNER_STATE_NOT_FOUND);
        }
        try {
            return JacksonUtil.readValue(content, AiPlannerResponse.class);
        } catch (Exception e) {
            throw new ServiceException(ServiceErrorCodeConstants.AI_PLANNER_STATE_NOT_FOUND);
        }
    }

    private void cacheResponse(AiPlannerResponse response) {
        if (response == null || !StringUtils.hasText(response.getSessionId())) {
            return;
        }
        try {
            redisUtil.set(cacheKey(response.getSessionId()),
                    JacksonUtil.writeValueAsString(response),
                    AI_PLANNING_TIMEOUT_SECONDS);
        } catch (Exception e) {
            // Redis 只是恢复能力，不能阻断一次已经成功生成的 AI 方案。
        }
    }

    private String cacheKey(String sessionId) {
        return AI_PLANNING_PREFIX + sessionId;
    }

    private List<PrizeOption> toPrizeOptions(List<PrizeDTO> prizeDTOList) {
        List<PrizeOption> options = new ArrayList<>();
        if (prizeDTOList == null) {
            return options;
        }
        for (PrizeDTO prize : prizeDTOList) {
            PrizeOption option = new PrizeOption();
            option.setPrizeId(prize.getPrizeId());
            option.setName(prize.getName());
            option.setPrice(prize.getPrice());
            option.setDescription(prize.getDescription());
            options.add(option);
        }
        return options;
    }

    private void validateReadyResponse(
            AiPlannerResponse response,
            List<PrizeOption> catalog,
            BigDecimal hardBudget) {
        if (response == null || !"READY".equals(response.getStatus()) || response.getDraft() == null) {
            return;
        }
        List<AiPlannerIssue> issues = new ArrayList<>();
        ActivityPlanDraft draft = response.getDraft();
        if (!StringUtils.hasText(draft.getActivityName()) || draft.getActivityName().length() > 30) {
            issues.add(issue("activity_name", "NAME_INVALID", "活动名称不能为空且不能超过30字"));
        }
        if (!StringUtils.hasText(draft.getDescription()) || draft.getDescription().length() > 200) {
            issues.add(issue("description", "DESCRIPTION_INVALID", "活动描述不能为空且不能超过200字"));
        }

        Set<Long> catalogIds = new HashSet<>();
        for (PrizeOption prize : catalog) {
            catalogIds.add(prize.getPrizeId());
        }
        Set<Long> seenIds = new HashSet<>();
        BigDecimal totalCost = BigDecimal.ZERO;
        if (draft.getPrizes() == null || draft.getPrizes().isEmpty()) {
            issues.add(issue("prizes", "NO_PRIZE_SELECTED", "方案没有可用奖品，请人工圈选"));
        } else {
            for (int i = 0; i < draft.getPrizes().size(); i++) {
                ActivityPlanPrize prize = draft.getPrizes().get(i);
                String field = "prizes[" + i + "]";
                if (prize.getPrizeId() == null || !catalogIds.contains(prize.getPrizeId())) {
                    issues.add(issue(field + ".prize_id", "PRIZE_NOT_FOUND", "奖品不在可选目录中"));
                }
                if (prize.getPrizeId() != null && !seenIds.add(prize.getPrizeId())) {
                    issues.add(issue(field + ".prize_id", "DUPLICATE_PRIZE", "同一奖品不能重复配置"));
                }
                if (prize.getPrizeAmount() == null || prize.getPrizeAmount() <= 0) {
                    issues.add(issue(field + ".prize_amount", "AMOUNT_INVALID", "奖品数量必须大于0"));
                }
                if (ActivityPrizeTiersEnum.forName(prize.getPrizeTiers()) == null) {
                    issues.add(issue(field + ".prize_tiers", "TIER_INVALID", "奖项等级只能是一、二、三等奖"));
                }
                PrizeOption catalogPrize = findPrize(catalog, prize.getPrizeId());
                if (catalogPrize != null && catalogPrize.getPrice() != null
                        && prize.getPrizeAmount() != null && prize.getPrizeAmount() > 0) {
                    totalCost = totalCost.add(catalogPrize.getPrice()
                            .multiply(BigDecimal.valueOf(prize.getPrizeAmount())));
                }
            }
        }
        if (hardBudget != null && totalCost.compareTo(hardBudget) > 0) {
            issues.add(issue("prizes", "BUDGET_EXCEEDED", "方案预计费用超过预算上限"));
        }
        if (!issues.isEmpty()) {
            response.setStatus("NEEDS_MANUAL");
            response.setIssues(issues);
            if (response.getWarnings() == null) {
                response.setWarnings(new ArrayList<>());
            }
            if (!response.getWarnings().contains("AI方案已生成，部分配置需要人工确认。")) {
                response.getWarnings().add("AI方案已生成，部分配置需要人工确认。");
            }
        }
    }

    private PrizeOption findPrize(List<PrizeOption> catalog, Long prizeId) {
        if (prizeId == null) {
            return null;
        }
        for (PrizeOption prize : catalog) {
            if (prizeId.equals(prize.getPrizeId())) {
                return prize;
            }
        }
        return null;
    }

    private AiPlannerIssue issue(String field, String code, String message) {
        AiPlannerIssue issue = new AiPlannerIssue();
        issue.setField(field);
        issue.setCode(code);
        issue.setMessage(message);
        return issue;
    }
}

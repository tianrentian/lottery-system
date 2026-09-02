package com.example.lotterysystem.service.ai.planner;

import lombok.Data;

import java.io.Serializable;

/**
 * AI活动方案中的奖品配置。
 * 字段与创建活动接口的奖品配置保持一致，便于安全回填。
 */
@Data
public class ActivityPlanPrize implements Serializable {

    private Long prizeId;

    private Long prizeAmount;

    private String prizeTiers;
}

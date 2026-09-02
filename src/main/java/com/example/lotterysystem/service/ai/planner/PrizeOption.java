package com.example.lotterysystem.service.ai.planner;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * AI策划可使用的奖品目录项。
 * 只包含奖品规划所需的公开字段，不包含人员信息。
 */
@Data
public class PrizeOption implements Serializable {

    private Long prizeId;

    private String name;

    private BigDecimal price;

    private String description;
}

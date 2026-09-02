package com.example.lotterysystem.service.ai.planner;

import lombok.Data;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * AI策划可使用的奖品目录项。
 * 只包含奖品规划所需的公开字段，不包含人员信息。
 */
@Data
public class PrizeOption implements Serializable {

    @JsonProperty("prize_id")
    private Long prizeId;

    @JsonProperty("name")
    private String name;

    @JsonProperty("price")
    private BigDecimal price;

    @JsonProperty("description")
    private String description;
}

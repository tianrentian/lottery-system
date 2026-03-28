package com.example.lotterysystem.controller.param;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Date;
import java.util.List;

@Data
public class DrawPrizeParam {

    /**
     * 活动id
     */
    @NotNull(message = "活动id不能为空")
    private Long activityId;

    /**
     * 奖品id
     */
    @NotNull(message = "奖品id不能为空")
    private Long prizeId;

    /**
     * 中奖时间
     */
    @NotNull(message = "中奖时间不能为空")
    private Date winningTime;

    /**
     * 中奖者列表（由后端在 Service 层随机抽取后填充，前端不再传入）
     */
    private List<Winner> winnerList;

    @Data
    public static class Winner {
        /**
         * 中奖者id
         */
        private Long userId;

        /**
         * 中奖者姓名
         */
        private String userName;
    }
}
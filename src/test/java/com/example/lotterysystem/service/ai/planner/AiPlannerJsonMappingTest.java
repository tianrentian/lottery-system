package com.example.lotterysystem.service.ai.planner;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AiPlannerJsonMappingTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void mapsPythonSnakeCaseResponseToJavaDraft() throws Exception {
        String json = """
                {
                  "status":"READY",
                  "draft":{
                    "activity_name":"公司年会抽奖",
                    "description":"面向全体员工",
                    "participant_hint":"至少选择3人",
                    "prizes":[{"prize_id":7,"prize_amount":1,"prize_tiers":"FIRST_PRIZE"}]
                  },
                  "repair_attempts":1,
                  "clarification_options":[]
                }
                """;

        AiPlannerResponse response = objectMapper.readValue(json, AiPlannerResponse.class);

        assertEquals("READY", response.getStatus());
        assertNotNull(response.getDraft());
        assertEquals("公司年会抽奖", response.getDraft().getActivityName());
        assertEquals("至少选择3人", response.getDraft().getParticipantHint());
        assertEquals(Integer.valueOf(1), response.getRepairAttempts());
        assertEquals(Long.valueOf(7), response.getDraft().getPrizes().get(0).getPrizeId());
        assertEquals(Long.valueOf(1), response.getDraft().getPrizes().get(0).getPrizeAmount());
    }

    @Test
    void serializesPrizeCatalogWithPythonFieldNames() throws Exception {
        PrizeOption option = new PrizeOption();
        option.setPrizeId(7L);
        option.setName("耳机");
        option.setPrice(new BigDecimal("99.00"));

        String json = objectMapper.writeValueAsString(option);

        assertEquals(true, json.contains("\"prize_id\":7"));
        assertEquals(false, json.contains("\"prizeId\""));
    }
}

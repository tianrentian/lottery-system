package com.example.lotterysystem.service.ai.planner;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.POST;

class AiPlannerClientTest {

    @Test
    void postsCatalogToPythonPlannerAndMapsResponse() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("http://ai-planner.test/plan"))
                .andExpect(method(POST))
                .andExpect(header("Content-Type", MediaType.APPLICATION_JSON_VALUE))
                .andExpect(jsonPath("$.prompt").value("年会抽奖"))
                .andExpect(jsonPath("$.available_prizes[0].prize_id").value(7))
                .andRespond(withSuccess(
                        "{\"status\":\"READY\",\"draft\":{\"activity_name\":\"年会抽奖\"}}",
                        MediaType.APPLICATION_JSON));

        PrizeOption option = new PrizeOption();
        option.setPrizeId(7L);
        option.setName("耳机");
        AiPlannerResponse response = new AiPlannerClient(restTemplate, "http://ai-planner.test/")
                .plan("年会抽奖", Collections.singletonList(option), null, null);

        assertEquals("READY", response.getStatus());
        assertEquals("年会抽奖", response.getDraft().getActivityName());
        server.verify();
    }
}

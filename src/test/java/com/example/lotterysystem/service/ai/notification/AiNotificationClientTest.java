package com.example.lotterysystem.service.ai.notification;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AiNotificationClientTest {

    @Test
    void postsContextWithoutWinnerDataAndMapsTwoAudiences() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("http://ai-planner.test/notification-templates"))
                .andExpect(method(POST))
                .andExpect(jsonPath("$.activity_name").value("夏日抽奖"))
                .andExpect(jsonPath("$.prize_name").value("无线耳机"))
                .andExpect(jsonPath("$.winner_name").doesNotExist())
                .andRespond(withSuccess("""
                        {
                          "mail_subject":"恭喜中奖｜夏日抽奖",
                          "variants":[{
                            "personal_text":"恭喜{{winnerName}}获得无线耳机",
                            "group_text":"恭喜{{winnerName}}抽中无线耳机"
                          }]
                        }
                        """, MediaType.APPLICATION_JSON));

        NotificationTemplateRequest request = new NotificationTemplateRequest();
        request.setActivityName("夏日抽奖");
        request.setPrizeName("无线耳机");

        NotificationTemplateResponse response = new AiNotificationClient(
                restTemplate, "http://ai-planner.test/").generate(request);

        assertEquals("恭喜中奖｜夏日抽奖", response.getMailSubject());
        assertEquals("恭喜{{winnerName}}获得无线耳机",
                response.getVariants().get(0).getPersonalText());
        server.verify();
    }
}

package com.example.lotterysystem.service.ai.planner;

import com.example.lotterysystem.common.exception.ServiceException;
import com.example.lotterysystem.common.errorcode.ServiceErrorCodeConstants;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.List;

/** Java 到 Python AI Planner 的 HTTP 客户端。 */
@Service
public class AiPlannerClient {

    private final RestTemplate restTemplate;
    private final String baseUrl;

    @Autowired
    public AiPlannerClient(
            RestTemplateBuilder restTemplateBuilder,
            @Value("${ai.planner.base-url:http://localhost:8090}") String baseUrl) {
        this(restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(2))
                .setReadTimeout(Duration.ofSeconds(35))
                .build(), baseUrl);
    }

    AiPlannerClient(RestTemplate restTemplate, String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
    }

    public AiPlannerResponse plan(
            String prompt,
            List<PrizeOption> availablePrizes,
            java.math.BigDecimal hardBudget,
            String clarificationAnswer) {
        AiPlannerRequest request = new AiPlannerRequest();
        request.setPrompt(prompt);
        request.setAvailablePrizes(availablePrizes);
        request.setHardBudget(hardBudget);
        request.setClarificationAnswer(clarificationAnswer);
        try {
            ResponseEntity<AiPlannerResponse> response = restTemplate.postForEntity(
                    baseUrl + "/plan", request, AiPlannerResponse.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new ServiceException(ServiceErrorCodeConstants.AI_PLANNER_UNAVAILABLE);
            }
            return response.getBody();
        } catch (RestClientException e) {
            throw new ServiceException(ServiceErrorCodeConstants.AI_PLANNER_UNAVAILABLE);
        }
    }
}

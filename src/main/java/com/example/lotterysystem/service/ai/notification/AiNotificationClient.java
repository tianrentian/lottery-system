package com.example.lotterysystem.service.ai.notification;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/** Java 到 Python AI 通知接口的 HTTP 客户端。 */
@Service
public class AiNotificationClient {

    private final RestTemplate restTemplate;
    private final String baseUrl;

    @Autowired
    public AiNotificationClient(
            RestTemplateBuilder restTemplateBuilder,
            @Value("${ai.notification.base-url:${ai.planner.base-url:http://localhost:8090}}") String baseUrl) {
        this(restTemplateBuilder
                .requestFactory(SimpleClientHttpRequestFactory.class)
                .setConnectTimeout(Duration.ofSeconds(2))
                .setReadTimeout(Duration.ofSeconds(30))
                .build(), baseUrl);
    }

    AiNotificationClient(RestTemplate restTemplate, String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
    }

    public NotificationTemplateResponse generate(NotificationTemplateRequest request) {
        try {
            ResponseEntity<NotificationTemplateResponse> response = restTemplate.postForEntity(
                    baseUrl + "/notification-templates", request, NotificationTemplateResponse.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new IllegalStateException("AI通知服务返回空结果");
            }
            return response.getBody();
        } catch (RestClientException e) {
            throw new IllegalStateException("AI通知服务暂时不可用", e);
        }
    }
}

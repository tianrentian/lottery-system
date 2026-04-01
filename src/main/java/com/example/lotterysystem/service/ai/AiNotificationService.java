package com.example.lotterysystem.service.ai;

import dev.langchain4j.model.chat.ChatLanguageModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

@Service
public class AiNotificationService {

    private static final Logger logger = LoggerFactory.getLogger(AiNotificationService.class);

    @Autowired
    private ChatLanguageModel chatLanguageModel;

    /**
     * 调用大模型生成千人千面贺卡，带有指数退避防抖重试机制 (KISS 原则体现)
     * 当遭遇 429 Limit 或 502 等网络抖动时，按 1s, 2s, 4s 强制重试 3 次挽救连接。
     */
    @Retryable(retryFor = Exception.class, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public String generatePersonalizedNotice(String activityName, String prizeName, String userName) {
        logger.info("============== 开始向 DeepSeek 大模型发起生成请求，耗时网络 I/O 阻断开始 ==============");
        
        String prompt = String.format("你是一个热情激昂的抽奖活动运营官。用户【%s】在我们的大型活动【%s】中，" +
                "极其幸运地一发入魂抽中了奖品：【%s】！请为他写一段 80 字左右的极度夸张、拉仇恨、充满情绪价值和喜悦氛围的祝贺短片。" +
                "要求：语气热烈喜庆，有网感、带emoji，可以@到本人，直接输出正文，不要有废话和啰嗦的修饰结尾。",
                userName, activityName, prizeName);
        
        String response = chatLanguageModel.generate(prompt);
        logger.info("============== 大模型返回成功 =============");
        return response;
    }
}

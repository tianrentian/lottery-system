package com.example.lotterysystem.service.mq;

import cn.hutool.crypto.SecureUtil;
import cn.hutool.http.HttpRequest;
import com.example.lotterysystem.common.config.DirectRabbitConfig;
import com.example.lotterysystem.common.utils.JacksonUtil;
import com.example.lotterysystem.common.utils.MailUtil;
import com.example.lotterysystem.dao.dateobject.WinningRecordDO;
import com.example.lotterysystem.service.ai.AiNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
@RabbitListener(queues = DirectRabbitConfig.QUEUE_AI_NOTIFICATION)
public class AiNotificationReceiver {

    private static final Logger logger = LoggerFactory.getLogger(AiNotificationReceiver.class);

    private static final String LLM_CACHE_PREFIX = "LLM_NOTICE_CACHE:";

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private AiNotificationService aiNotificationService;

    @Autowired
    private ThreadPoolTaskExecutor threadPoolTaskExecutor;

    @Autowired
    private MailUtil mailUtil;

    @Value("${wechat.robot.webhook-url}")
    private String webhookUrl;

    @RabbitHandler
    public void process(Map<String, String> message) {
        String finalMsg = "";
        WinningRecordDO recordDO = null;
        try {
            String recordData = message.get("recordData");
            recordDO = JacksonUtil.readValue(recordData, WinningRecordDO.class);
            logger.info("=========== AI 通知削峰节点：开始处理中奖记录，用户：[{}] ===========", recordDO.getWinnerName());

            // 1. 上帝视角的无差全降本特征计算
            String featureKey = String.format("%s_%s", recordDO.getActivityId(), recordDO.getPrizeId());
            String redisKey = LLM_CACHE_PREFIX + SecureUtil.md5(featureKey);

            // 2. Redis 高级防火墙缓存拉取
            String cacheMsg = stringRedisTemplate.opsForValue().get(redisKey);
            if (cacheMsg != null) {
                logger.info("⚡ [特征降本壁垒生效] 瞬间命中 Redis 防火墙! 秒级复用已有优美贺词流向 [{}]", recordDO.getWinnerName());
                finalMsg = cacheMsg;
            } else {
                logger.info("☁ [无阻首发生成] 未命中缓存，穿透防御墙，正在执行缓慢的长连接 LLM 生成（最高触发重试 3 次）。。。");
                finalMsg = aiNotificationService.generatePersonalizedNotice(
                        recordDO.getActivityName(), recordDO.getPrizeName(), recordDO.getWinnerName());
                
                // 成功后，将劳动成果存入 Redis，设为 5 分钟生命周期。拦截后面的所有的羊毛或者高频同奖触发。
                stringRedisTemplate.opsForValue().set(redisKey, finalMsg, 5, TimeUnit.MINUTES);
                logger.info("✅ 新生成的霸榜贺词已安全存入 Redis 特征库。");
            }

        } catch (Exception e) {
            // KISS 极简反过度设计哲学：无视上面千疮百孔的网络和重试。一旦失败，Catch 块硬切
            logger.error("❌ 严重告警：大模型经过指数退避重试3次后网络依然彻底阻断！开始无缝切换至硬编码 Fallback 无感降级！");
            finalMsg = "恭喜！您极其豪运地在一场大放血抽奖中抽中了豪华大奖！由于此刻大促过于火热无暇书写超长喜报，特此向您飞奔发送极简得奖公告，奖品已经是您的啦！";
        }

        // --- 核心末端分离（架构原有的并发重排下沉复盘） ---
        // 压榨底线带宽性能：把串行的消费者任务拆分成由旧版本祖传的 threadPool 执行大范围并发下放。
        if (recordDO != null) {
            final String mailAddress = recordDO.getWinnerEmail();
            final String noticeContent = finalMsg;

            // ① 投向极度传统的异步个人信件流转系统
            threadPoolTaskExecutor.execute(() -> {
                logger.info("✉ 开始多线程处理 [{}] 的个人冷邮件投送", mailAddress);
                mailUtil.sendSampleMail(mailAddress, "🎉 重磅！无敌欧皇专属的大惊喜空投到了！", noticeContent);
            });

            // ② 投向引爆运营私域的【钉钉群集 WebHook】（高亮点替代短信功能）
            threadPoolTaskExecutor.execute(() -> {
                logger.info("🤖 启动钉钉私域社群拉仇恨狂潮广播...");
                try {
                    // 加入安全设置里您勾选的防垃圾机制关键词：“恭喜”（以便穿越防火墙直接钉死在群面板上）
                    String payload = "{\n" +
                            "    \"msgtype\": \"text\",\n" +
                            "    \"text\": {\n" +
                            "        \"content\": \"【喜报恭喜！】\\n" + noticeContent.replace("\"", "\\\"").replace("\n", "\\n") + "\"\n" +
                            "    }\n" +
                            "}";
                    HttpRequest.post(webhookUrl)
                            .header("Content-Type", "application/json")
                            .body(payload)
                            .timeout(3000)
                            .execute();
                    logger.info("🚀 喜报群推送引爆成功，请查收！");
                } catch (Exception error) {
                    logger.error("群广播下发遇到阻碍: {}", error.getMessage());
                }
            });
        }
    }
}

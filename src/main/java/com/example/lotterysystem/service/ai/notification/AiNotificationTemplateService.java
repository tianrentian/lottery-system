package com.example.lotterysystem.service.ai.notification;

import cn.hutool.crypto.SecureUtil;
import com.example.lotterysystem.common.utils.JacksonUtil;
import com.example.lotterysystem.dao.dateobject.ActivityDO;
import com.example.lotterysystem.dao.dateobject.PrizeDO;
import com.example.lotterysystem.dao.dateobject.WinningRecordDO;
import com.example.lotterysystem.dao.mapper.ActivityMapper;
import com.example.lotterysystem.dao.mapper.PrizeMapper;
import com.example.lotterysystem.service.enums.ActivityPrizeTiersEnum;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 负责通知模板的缓存、并发重建、兜底和中奖者姓名填充。 */
@Service
public class AiNotificationTemplateService {

    private static final Logger logger = LoggerFactory.getLogger(AiNotificationTemplateService.class);
    private static final String CACHE_PREFIX = "LLM_NOTICE_CACHE:";
    private static final String LOCK_PREFIX = "LLM_NOTICE_LOCK:";
    private static final String WINNER_PLACEHOLDER = "{{winnerName}}";
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{[^{}]+}}");
    private static final long LOCK_WAIT_SECONDS = 35;
    private static final long AI_CACHE_SECONDS = 30 * 60;
    private static final long FALLBACK_CACHE_SECONDS = 60;

    private final StringRedisTemplate redisTemplate;
    private final RedissonClient redissonClient;
    private final AiNotificationClient aiNotificationClient;
    private final ActivityMapper activityMapper;
    private final PrizeMapper prizeMapper;

    public AiNotificationTemplateService(
            StringRedisTemplate redisTemplate,
            RedissonClient redissonClient,
            AiNotificationClient aiNotificationClient,
            ActivityMapper activityMapper,
            PrizeMapper prizeMapper) {
        this.redisTemplate = redisTemplate;
        this.redissonClient = redissonClient;
        this.aiNotificationClient = aiNotificationClient;
        this.activityMapper = activityMapper;
        this.prizeMapper = prizeMapper;
    }

    public RenderedNotification resolve(WinningRecordDO record) {
        String feature = record.getActivityId() + "_" + record.getPrizeId();
        String digest = SecureUtil.md5(feature);
        String cacheKey = CACHE_PREFIX + digest;
        String lockKey = LOCK_PREFIX + digest;

        NotificationTemplateResponse templates;
        try {
            templates = readCache(cacheKey, record);
            if (templates == null) {
                templates = rebuildWithLock(cacheKey, lockKey, record);
            }
        } catch (Exception e) {
            // Redis 或 Redisson 故障时仍然返回本地业务文案，通知链路不依赖 AI 可用性。
            logger.error("AI通知模板缓存不可用，使用本地兜底，activityId={}，prizeId={}",
                    record.getActivityId(), record.getPrizeId(), e);
            templates = buildFallback(record);
        }
        return render(templates, record);
    }

    private NotificationTemplateResponse rebuildWithLock(
            String cacheKey,
            String lockKey,
            WinningRecordDO record) {
        RLock lock = redissonClient.getLock(lockKey);
        boolean locked = false;
        try {
            // 不指定固定租期，交给 Redisson 看门狗续期，避免模型响应较慢时锁提前失效。
            locked = lock.tryLock(LOCK_WAIT_SECONDS, TimeUnit.SECONDS);
            if (!locked) {
                NotificationTemplateResponse cached = readCache(cacheKey, record);
                return cached != null ? cached : buildFallback(record);
            }

            // 双重检查：等待锁期间，其他线程可能已经生成并写入缓存。
            NotificationTemplateResponse cached = readCache(cacheKey, record);
            if (cached != null) {
                return cached;
            }

            return generateAndCache(cacheKey, record);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("等待AI通知模板锁时线程被中断，activityId={}，prizeId={}",
                    record.getActivityId(), record.getPrizeId());
            return buildFallback(record);
        } catch (Exception e) {
            logger.error("生成AI通知模板失败，使用本地兜底，activityId={}，prizeId={}",
                    record.getActivityId(), record.getPrizeId(), e);
            return buildFallback(record);
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private NotificationTemplateResponse generateAndCache(String cacheKey, WinningRecordDO record) {
        NotificationTemplateResponse templates;
        long ttlSeconds;
        try {
            NotificationTemplateRequest request = buildRequest(record);
            templates = aiNotificationClient.generate(request);
            validateTemplates(templates, record);
            ttlSeconds = AI_CACHE_SECONDS;
            logger.info("AI通知模板生成成功，activityId={}，prizeId={}",
                    record.getActivityId(), record.getPrizeId());
        } catch (Exception e) {
            logger.warn("AI通知模板生成或校验失败，写入短期兜底缓存，activityId={}，prizeId={}，原因={}",
                    record.getActivityId(), record.getPrizeId(), e.getMessage());
            templates = buildFallback(record);
            ttlSeconds = FALLBACK_CACHE_SECONDS;
        }

        try {
            redisTemplate.opsForValue().set(
                    cacheKey, JacksonUtil.writeValueAsString(templates), ttlSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            // 缓存异常不能阻断已经准备好的通知。
            logger.warn("AI通知模板写入Redis失败，当前通知继续发送，cacheKey={}", cacheKey, e);
        }
        return templates;
    }

    private NotificationTemplateRequest buildRequest(WinningRecordDO record) {
        ActivityDO activity = activityMapper.selectById(record.getActivityId());
        PrizeDO prize = prizeMapper.selectById(record.getPrizeId());

        NotificationTemplateRequest request = new NotificationTemplateRequest();
        request.setActivityName(defaultText(record.getActivityName(), "抽奖活动"));
        request.setActivityDescription(activity == null
                ? "" : defaultText(activity.getDescription(), ""));
        request.setPrizeName(defaultText(record.getPrizeName(), "幸运奖品"));
        request.setPrizeDescription(prize == null
                ? "" : defaultText(prize.getDescription(), ""));
        request.setPrizeTier(tierMessage(record.getPrizeTier()));
        return request;
    }

    private NotificationTemplateResponse readCache(String cacheKey, WinningRecordDO record) {
        String content = redisTemplate.opsForValue().get(cacheKey);
        if (!StringUtils.hasText(content)) {
            return null;
        }
        try {
            NotificationTemplateResponse templates = JacksonUtil.readValue(
                    content, NotificationTemplateResponse.class);
            validateTemplates(templates, record);
            return templates;
        } catch (Exception e) {
            logger.warn("AI通知模板缓存已损坏，将重新生成，cacheKey={}", cacheKey);
            try {
                redisTemplate.delete(cacheKey);
            } catch (Exception deleteError) {
                logger.warn("删除损坏的AI通知模板缓存失败，cacheKey={}", cacheKey, deleteError);
            }
            return null;
        }
    }

    private void validateTemplates(
            NotificationTemplateResponse templates,
            WinningRecordDO record) {
        if (templates == null || !StringUtils.hasText(templates.getMailSubject())
                || templates.getMailSubject().length() > 80) {
            throw new IllegalArgumentException("缺少邮件标题");
        }
        String activityName = defaultText(record.getActivityName(), "抽奖活动");
        String prizeName = defaultText(record.getPrizeName(), "幸运奖品");
        if (!templates.getMailSubject().contains(activityName)) {
            throw new IllegalArgumentException("邮件标题必须包含活动名称");
        }

        List<NotificationVariant> variants = templates.getVariants();
        if (variants == null || variants.isEmpty() || variants.size() > 3) {
            throw new IllegalArgumentException("通知模板数量不正确");
        }
        for (NotificationVariant variant : variants) {
            validateText(variant.getPersonalText(), prizeName, false);
            validateText(variant.getGroupText(), prizeName, true);
            if (variant.getPersonalText().equals(variant.getGroupText())) {
                throw new IllegalArgumentException("个人通知与群通知不能相同");
            }
        }
    }

    private void validateText(String text, String prizeName, boolean groupText) {
        if (!StringUtils.hasText(text) || text.length() > 200 || !text.contains(prizeName)) {
            throw new IllegalArgumentException("通知正文内容不完整");
        }
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(text);
        if (!matcher.find() || !WINNER_PLACEHOLDER.equals(matcher.group()) || matcher.find()) {
            throw new IllegalArgumentException("中奖人占位符不正确");
        }
        if (groupText && !text.contains("恭喜")) {
            throw new IllegalArgumentException("群通知必须包含恭喜关键词");
        }
    }

    private NotificationTemplateResponse buildFallback(WinningRecordDO record) {
        String activityName = defaultText(record.getActivityName(), "抽奖活动");
        String prizeName = defaultText(record.getPrizeName(), "幸运奖品");
        String tier = tierMessage(record.getPrizeTier());
        String tierPrefix = StringUtils.hasText(tier) ? tier + "——" : "";

        NotificationVariant variant = new NotificationVariant();
        variant.setPersonalText("🎉 恭喜" + WINNER_PLACEHOLDER + "！你在「" + activityName
                + "」中抽中" + tierPrefix + prizeName + "，愿这份惊喜为今天再添一份快乐！");
        variant.setGroupText("🎉 幸运揭晓！恭喜" + WINNER_PLACEHOLDER + "在「" + activityName
                + "」中抽中" + tierPrefix + prizeName + "，掌声送给本轮幸运儿！");

        NotificationTemplateResponse response = new NotificationTemplateResponse();
        response.setMailSubject("恭喜中奖｜" + activityName);
        response.setVariants(Collections.singletonList(variant));
        return response;
    }

    private RenderedNotification render(
            NotificationTemplateResponse templates,
            WinningRecordDO record) {
        List<NotificationVariant> variants = templates.getVariants();
        int index = record.getWinnerId() == null
                ? 0
                : Math.floorMod(Long.hashCode(record.getWinnerId()), variants.size());
        NotificationVariant selected = variants.get(index);
        String winnerName = defaultText(record.getWinnerName(), "幸运用户");
        return new RenderedNotification(
                templates.getMailSubject(),
                selected.getPersonalText().replace(WINNER_PLACEHOLDER, winnerName),
                selected.getGroupText().replace(WINNER_PLACEHOLDER, winnerName));
    }

    private String tierMessage(String tierName) {
        ActivityPrizeTiersEnum tier = ActivityPrizeTiersEnum.forName(tierName);
        return tier == null ? "" : tier.getMessage();
    }

    private String defaultText(String text, String fallback) {
        return StringUtils.hasText(text) ? text : fallback;
    }
}

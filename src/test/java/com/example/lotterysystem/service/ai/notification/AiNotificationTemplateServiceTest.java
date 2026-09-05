package com.example.lotterysystem.service.ai.notification;

import com.example.lotterysystem.common.utils.JacksonUtil;
import com.example.lotterysystem.dao.dateobject.ActivityDO;
import com.example.lotterysystem.dao.dateobject.PrizeDO;
import com.example.lotterysystem.dao.dateobject.WinningRecordDO;
import com.example.lotterysystem.dao.mapper.ActivityMapper;
import com.example.lotterysystem.dao.mapper.PrizeMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiNotificationTemplateServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private RedissonClient redissonClient;
    @Mock
    private RLock distributedLock;
    @Mock
    private AiNotificationClient aiNotificationClient;
    @Mock
    private ActivityMapper activityMapper;
    @Mock
    private PrizeMapper prizeMapper;

    private AiNotificationTemplateService service;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        service = new AiNotificationTemplateService(
                redisTemplate, redissonClient, aiNotificationClient, activityMapper, prizeMapper);
    }

    @Test
    void rechecksCacheAfterAcquiringLockAndSkipsAiCall() throws Exception {
        WinningRecordDO record = record(40L, "小明");
        String cached = JacksonUtil.writeValueAsString(aiTemplates());
        when(valueOperations.get(anyString())).thenReturn(null, cached);
        when(redissonClient.getLock(anyString())).thenReturn(distributedLock);
        when(distributedLock.tryLock(anyLong(), eq(TimeUnit.SECONDS))).thenReturn(true);
        when(distributedLock.isHeldByCurrentThread()).thenReturn(true);

        RenderedNotification result = service.resolve(record);

        assertTrue(result.getPersonalText().contains("小明"));
        assertTrue(!result.getPersonalText().contains("{{winnerName}}"));
        verify(aiNotificationClient, never()).generate(org.mockito.ArgumentMatchers.any());
        verify(distributedLock).unlock();
    }

    @Test
    void concurrentMissesGenerateOnlyOneTemplateSet() throws Exception {
        Map<String, String> cache = new ConcurrentHashMap<>();
        ReentrantLock localLock = new ReentrantLock();
        when(valueOperations.get(anyString())).thenAnswer(invocation ->
                cache.get(invocation.getArgument(0, String.class)));
        doAnswer(invocation -> {
            cache.put(invocation.getArgument(0, String.class),
                    invocation.getArgument(1, String.class));
            return null;
        }).when(valueOperations).set(anyString(), anyString(), anyLong(), eq(TimeUnit.SECONDS));
        when(redissonClient.getLock(anyString())).thenReturn(distributedLock);
        when(distributedLock.tryLock(anyLong(), eq(TimeUnit.SECONDS))).thenAnswer(invocation ->
                localLock.tryLock(invocation.getArgument(0, Long.class), TimeUnit.SECONDS));
        when(distributedLock.isHeldByCurrentThread()).thenAnswer(invocation -> localLock.isHeldByCurrentThread());
        doAnswer(invocation -> {
            localLock.unlock();
            return null;
        }).when(distributedLock).unlock();
        when(activityMapper.selectById(10L)).thenReturn(activity());
        when(prizeMapper.selectById(20L)).thenReturn(prize());
        when(aiNotificationClient.generate(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> {
            Thread.sleep(80);
            return aiTemplates();
        });

        ExecutorService executor = Executors.newFixedThreadPool(4);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<java.util.concurrent.Future<RenderedNotification>> futures = Arrays.asList(
                    executor.submit(() -> resolveAfter(start, record(40L, "小明"))),
                    executor.submit(() -> resolveAfter(start, record(41L, "小红"))),
                    executor.submit(() -> resolveAfter(start, record(42L, "小李"))),
                    executor.submit(() -> resolveAfter(start, record(43L, "小王")))
            );
            start.countDown();
            List<RenderedNotification> results = futures.stream().map(future -> {
                try {
                    return future.get(3, TimeUnit.SECONDS);
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }).collect(Collectors.toList());

            assertEquals(4, results.size());
            assertTrue(results.stream().allMatch(item -> !item.getPersonalText().contains("{{winnerName}}")));
            verify(aiNotificationClient, times(1)).generate(org.mockito.ArgumentMatchers.any());
            verify(valueOperations, times(1))
                    .set(anyString(), anyString(), eq(1800L), eq(TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void aiFailureUsesDynamicFallbackAndCachesItBriefly() throws Exception {
        WinningRecordDO record = record(40L, "小明");
        when(valueOperations.get(anyString())).thenReturn(null);
        when(redissonClient.getLock(anyString())).thenReturn(distributedLock);
        when(distributedLock.tryLock(anyLong(), eq(TimeUnit.SECONDS))).thenReturn(true);
        when(distributedLock.isHeldByCurrentThread()).thenReturn(true);
        when(activityMapper.selectById(10L)).thenReturn(activity());
        when(prizeMapper.selectById(20L)).thenReturn(prize());
        when(aiNotificationClient.generate(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new IllegalStateException("服务不可用"));

        RenderedNotification result = service.resolve(record);

        assertEquals("恭喜中奖｜夏日抽奖", result.getMailSubject());
        assertTrue(result.getPersonalText().contains("小明"));
        assertTrue(result.getPersonalText().contains("二等奖——无线耳机"));
        assertTrue(result.getGroupText().contains("恭喜小明"));
        verify(valueOperations).set(anyString(), anyString(), eq(60L), eq(TimeUnit.SECONDS));
    }

    @Test
    void acceptsOneValidAiVariantAndCachesItForThirtyMinutes() throws Exception {
        WinningRecordDO record = record(40L, "小明");
        NotificationTemplateResponse response = new NotificationTemplateResponse();
        response.setMailSubject("恭喜中奖｜夏日抽奖");
        response.setVariants(List.of(variant(
                "夏日好运落在{{winnerName}}身边，你获得了无线耳机",
                "幸运名单揭晓，恭喜{{winnerName}}获得无线耳机")));
        when(valueOperations.get(anyString())).thenReturn(null);
        when(redissonClient.getLock(anyString())).thenReturn(distributedLock);
        when(distributedLock.tryLock(anyLong(), eq(TimeUnit.SECONDS))).thenReturn(true);
        when(distributedLock.isHeldByCurrentThread()).thenReturn(true);
        when(activityMapper.selectById(10L)).thenReturn(activity());
        when(prizeMapper.selectById(20L)).thenReturn(prize());
        when(aiNotificationClient.generate(org.mockito.ArgumentMatchers.any())).thenReturn(response);

        RenderedNotification result = service.resolve(record);

        assertTrue(result.getPersonalText().contains("小明"));
        verify(valueOperations).set(anyString(), anyString(), eq(1800L), eq(TimeUnit.SECONDS));
    }

    private RenderedNotification resolveAfter(CountDownLatch start, WinningRecordDO record)
            throws InterruptedException {
        start.await();
        return service.resolve(record);
    }

    private WinningRecordDO record(Long winnerId, String winnerName) {
        WinningRecordDO record = new WinningRecordDO();
        record.setActivityId(10L);
        record.setActivityName("夏日抽奖");
        record.setPrizeId(20L);
        record.setPrizeName("无线耳机");
        record.setPrizeTier("SECOND_PRIZE");
        record.setWinnerId(winnerId);
        record.setWinnerName(winnerName);
        record.setWinnerEmail("winner@example.com");
        return record;
    }

    private ActivityDO activity() {
        ActivityDO activity = new ActivityDO();
        activity.setId(10L);
        activity.setActivityName("夏日抽奖");
        activity.setDescription("为公司员工准备的夏日福利活动");
        return activity;
    }

    private PrizeDO prize() {
        PrizeDO prize = new PrizeDO();
        prize.setId(20L);
        prize.setName("无线耳机");
        prize.setDescription("适合通勤时听音乐");
        return prize;
    }

    private NotificationTemplateResponse aiTemplates() {
        NotificationTemplateResponse response = new NotificationTemplateResponse();
        response.setMailSubject("恭喜中奖｜夏日抽奖");
        response.setVariants(Arrays.asList(
                variant("{{winnerName}}，你在夏日抽奖中获得无线耳机A",
                        "恭喜{{winnerName}}在夏日抽奖中获得无线耳机A"),
                variant("{{winnerName}}，你在夏日抽奖中获得无线耳机B",
                        "恭喜{{winnerName}}在夏日抽奖中获得无线耳机B"),
                variant("{{winnerName}}，你在夏日抽奖中获得无线耳机C",
                        "恭喜{{winnerName}}在夏日抽奖中获得无线耳机C")
        ));
        return response;
    }

    private NotificationVariant variant(String personal, String group) {
        NotificationVariant variant = new NotificationVariant();
        variant.setPersonalText(personal);
        variant.setGroupText(group);
        return variant;
    }
}

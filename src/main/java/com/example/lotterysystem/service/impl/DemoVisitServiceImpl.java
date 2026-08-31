package com.example.lotterysystem.service.impl;

import com.example.lotterysystem.controller.param.DemoVisitEventParam;
import com.example.lotterysystem.dao.mapper.DemoVisitSessionMapper;
import com.example.lotterysystem.service.DemoVisitService;
import com.example.lotterysystem.service.dto.DemoVisitStatisticsDTO;
import com.example.lotterysystem.service.enums.DemoVisitEventType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.Date;

@Service
public class DemoVisitServiceImpl implements DemoVisitService {

    private static final ZoneId SHANGHAI_ZONE = ZoneId.of("Asia/Shanghai");
    private static final int RECENT_SESSION_LIMIT = 20;

    @Autowired
    private DemoVisitSessionMapper demoVisitSessionMapper;

    @Override
    public void reportEvent(DemoVisitEventParam param) {
        DemoVisitEventType eventType = param.getEventType();
        demoVisitSessionMapper.upsertEvent(
                param.getSessionId(),
                eventType == DemoVisitEventType.STAY_60_SECONDS ? 1 : 0,
                eventType == DemoVisitEventType.LOGIN_SUCCESS ? 1 : 0,
                eventType == DemoVisitEventType.DRAW_SUCCESS ? 1 : 0,
                eventType == DemoVisitEventType.PAGE_ERROR ? 1 : 0
        );
    }

    @Override
    public DemoVisitStatisticsDTO getStatistics(String range) {
        Date startTime = calculateStartTime(range);
        DemoVisitStatisticsDTO statistics = demoVisitSessionMapper.selectSummary(startTime);
        if (statistics == null) {
            statistics = new DemoVisitStatisticsDTO();
        }
        normalizeCounts(statistics);
        statistics.setDailyTrends(demoVisitSessionMapper.selectDailyTrends(startTime));
        statistics.setRecentSessions(
                demoVisitSessionMapper.selectRecentSessions(startTime, RECENT_SESSION_LIMIT));
        if (statistics.getDailyTrends() == null) {
            statistics.setDailyTrends(Collections.emptyList());
        }
        if (statistics.getRecentSessions() == null) {
            statistics.setRecentSessions(Collections.emptyList());
        }
        return statistics;
    }

    private Date calculateStartTime(String range) {
        String normalizedRange = range == null ? "ALL" : range.trim().toUpperCase();
        LocalDate today = LocalDate.now(SHANGHAI_ZONE);
        return switch (normalizedRange) {
            case "ALL" -> null;
            case "LAST_7_DAYS" -> Date.from(
                    today.minusDays(6).atStartOfDay(SHANGHAI_ZONE).toInstant());
            case "LAST_30_DAYS" -> Date.from(
                    today.minusDays(29).atStartOfDay(SHANGHAI_ZONE).toInstant());
            default -> throw new IllegalArgumentException("不支持的统计范围: " + range);
        };
    }

    private void normalizeCounts(DemoVisitStatisticsDTO statistics) {
        statistics.setOpenedSessions(valueOrZero(statistics.getOpenedSessions()));
        statistics.setEngagedSessions(valueOrZero(statistics.getEngagedSessions()));
        statistics.setLoginSessions(valueOrZero(statistics.getLoginSessions()));
        statistics.setDrawSessions(valueOrZero(statistics.getDrawSessions()));
        statistics.setErrorSessions(valueOrZero(statistics.getErrorSessions()));
        statistics.setTotalErrors(valueOrZero(statistics.getTotalErrors()));
    }

    private long valueOrZero(Long value) {
        return value == null ? 0L : value;
    }
}

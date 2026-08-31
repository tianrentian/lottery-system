package com.example.lotterysystem;

import com.example.lotterysystem.controller.param.DemoVisitEventParam;
import com.example.lotterysystem.dao.mapper.DemoVisitSessionMapper;
import com.example.lotterysystem.service.dto.DemoVisitStatisticsDTO;
import com.example.lotterysystem.service.enums.DemoVisitEventType;
import com.example.lotterysystem.service.impl.DemoVisitServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DemoVisitServiceTest {

    private static final ZoneId SHANGHAI_ZONE = ZoneId.of("Asia/Shanghai");
    private static final String SESSION_ID = "123e4567-e89b-42d3-a456-426614174000";

    @Mock
    private DemoVisitSessionMapper demoVisitSessionMapper;

    @InjectMocks
    private DemoVisitServiceImpl demoVisitService;

    @Test
    void shouldMapDrawEventToSingleSessionFlag() {
        DemoVisitEventParam param = event(DemoVisitEventType.DRAW_SUCCESS);

        demoVisitService.reportEvent(param);

        verify(demoVisitSessionMapper).upsertEvent(SESSION_ID, 0, 0, 1, 0);
    }

    @Test
    void shouldMapPageErrorToIncrement() {
        DemoVisitEventParam param = event(DemoVisitEventType.PAGE_ERROR);

        demoVisitService.reportEvent(param);

        verify(demoVisitSessionMapper).upsertEvent(SESSION_ID, 0, 0, 0, 1);
    }

    @Test
    void shouldQueryAllWithoutStartTime() {
        stubStatisticsQueries();

        demoVisitService.getStatistics("ALL");

        verify(demoVisitSessionMapper).selectSummary(null);
    }

    @Test
    void shouldQuerySevenNaturalDaysFromShanghaiMidnight() {
        stubStatisticsQueries();

        demoVisitService.getStatistics("LAST_7_DAYS");

        ArgumentCaptor<Date> captor = ArgumentCaptor.forClass(Date.class);
        verify(demoVisitSessionMapper).selectSummary(captor.capture());
        LocalDate actualStartDate = captor.getValue().toInstant()
                .atZone(SHANGHAI_ZONE).toLocalDate();
        assertEquals(LocalDate.now(SHANGHAI_ZONE).minusDays(6), actualStartDate);
        assertEquals(0, captor.getValue().toInstant().atZone(SHANGHAI_ZONE).getHour());
    }

    @Test
    void shouldRejectUnsupportedRange() {
        assertThrows(IllegalArgumentException.class,
                () -> demoVisitService.getStatistics("LAST_365_DAYS"));
    }

    private DemoVisitEventParam event(DemoVisitEventType eventType) {
        DemoVisitEventParam param = new DemoVisitEventParam();
        param.setSessionId(SESSION_ID);
        param.setEventType(eventType);
        return param;
    }

    private void stubStatisticsQueries() {
        when(demoVisitSessionMapper.selectSummary(nullable(Date.class)))
                .thenReturn(new DemoVisitStatisticsDTO());
        when(demoVisitSessionMapper.selectDailyTrends(nullable(Date.class)))
                .thenReturn(Collections.emptyList());
        when(demoVisitSessionMapper.selectRecentSessions(nullable(Date.class), anyInt()))
                .thenReturn(Collections.emptyList());
    }
}

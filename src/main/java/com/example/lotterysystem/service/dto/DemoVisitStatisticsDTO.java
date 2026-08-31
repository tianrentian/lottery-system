package com.example.lotterysystem.service.dto;

import lombok.Data;

import java.util.Date;
import java.util.List;

@Data
public class DemoVisitStatisticsDTO {

    private Long openedSessions;
    private Long engagedSessions;
    private Long loginSessions;
    private Long drawSessions;
    private Long errorSessions;
    private Long totalErrors;
    private Date lastVisitTime;
    private Date lastEngagedTime;
    private List<DailyTrendDTO> dailyTrends;
    private List<RecentSessionDTO> recentSessions;

    @Data
    public static class DailyTrendDTO {
        private String visitDate;
        private Long openedSessions;
        private Long engagedSessions;
        private Long drawSessions;
    }

    @Data
    public static class RecentSessionDTO {
        private Date firstVisitTime;
        private Date lastActiveTime;
        private Boolean stayed60Seconds;
        private Boolean loginSuccess;
        private Boolean drawSuccess;
        private Integer errorCount;
    }
}

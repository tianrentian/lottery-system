package com.example.lotterysystem.dao.mapper;

import com.example.lotterysystem.service.dto.DemoVisitStatisticsDTO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Date;
import java.util.List;

@Mapper
public interface DemoVisitSessionMapper {

    @Insert("insert into demo_visit_session (session_id, stayed_60_seconds," +
            " login_success, draw_success, error_count)" +
            " values (#{sessionId}, #{stayed60Seconds}, #{loginSuccess}," +
            " #{drawSuccess}, #{errorIncrement})" +
            " on duplicate key update" +
            " gmt_modified = CURRENT_TIMESTAMP," +
            " stayed_60_seconds = greatest(stayed_60_seconds, values(stayed_60_seconds))," +
            " login_success = greatest(login_success, values(login_success))," +
            " draw_success = greatest(draw_success, values(draw_success))," +
            " error_count = error_count + values(error_count)")
    int upsertEvent(@Param("sessionId") String sessionId,
                    @Param("stayed60Seconds") int stayed60Seconds,
                    @Param("loginSuccess") int loginSuccess,
                    @Param("drawSuccess") int drawSuccess,
                    @Param("errorIncrement") int errorIncrement);

    @Select({
            "<script>",
            "select count(*) as openedSessions,",
            "coalesce(sum(stayed_60_seconds = 1 or login_success = 1 or draw_success = 1), 0) as engagedSessions,",
            "coalesce(sum(login_success = 1), 0) as loginSessions,",
            "coalesce(sum(draw_success = 1), 0) as drawSessions,",
            "coalesce(sum(error_count > 0), 0) as errorSessions,",
            "coalesce(sum(error_count), 0) as totalErrors,",
            "max(gmt_create) as lastVisitTime,",
            "max(case when stayed_60_seconds = 1 or login_success = 1 or draw_success = 1",
            "then gmt_modified end) as lastEngagedTime",
            "from demo_visit_session",
            "<if test='startTime != null'>",
            "where gmt_create &gt;= #{startTime}",
            "</if>",
            "</script>"
    })
    DemoVisitStatisticsDTO selectSummary(@Param("startTime") Date startTime);

    @Select({
            "<script>",
            "select date_format(gmt_create, '%Y-%m-%d') as visitDate,",
            "count(*) as openedSessions,",
            "coalesce(sum(stayed_60_seconds = 1 or login_success = 1 or draw_success = 1), 0) as engagedSessions,",
            "coalesce(sum(draw_success = 1), 0) as drawSessions",
            "from demo_visit_session",
            "<if test='startTime != null'>",
            "where gmt_create &gt;= #{startTime}",
            "</if>",
            "group by visitDate",
            "order by visitDate asc",
            "</script>"
    })
    List<DemoVisitStatisticsDTO.DailyTrendDTO> selectDailyTrends(
            @Param("startTime") Date startTime);

    @Select({
            "<script>",
            "select gmt_create as firstVisitTime, gmt_modified as lastActiveTime,",
            "stayed_60_seconds as stayed60Seconds, login_success as loginSuccess,",
            "draw_success as drawSuccess, error_count as errorCount",
            "from demo_visit_session",
            "<if test='startTime != null'>",
            "where gmt_create &gt;= #{startTime}",
            "</if>",
            "order by gmt_create desc",
            "limit #{limit}",
            "</script>"
    })
    List<DemoVisitStatisticsDTO.RecentSessionDTO> selectRecentSessions(
            @Param("startTime") Date startTime,
            @Param("limit") int limit);
}

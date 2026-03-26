package com.example.lotterysystem.dao.mapper;

import com.example.lotterysystem.dao.dateobject.DlxMessageDO;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 死信消息持久化Mapper
 */
@Mapper
public interface DlxMessageMapper {

    /**
     * 插入一条死信消息记录
     */
    @Insert("insert into dlx_message (message_id, message_body, error_msg," +
            " retry_count, max_retry, status, next_retry_time)" +
            " values (#{messageId}, #{messageBody}, #{errorMsg}," +
            " #{retryCount}, #{maxRetry}, #{status}, #{nextRetryTime})")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(DlxMessageDO dlxMessageDO);

    /**
     * 查询待重试的消息：状态为PENDING且已到达下次重试时间
     */
    @Select("select * from dlx_message" +
            " where status = 'PENDING'" +
            " and next_retry_time <= NOW()" +
            " order by next_retry_time asc" +
            " limit #{limit}")
    List<DlxMessageDO> selectPendingMessages(@Param("limit") int limit);

    /**
     * 更新重试次数和下次重试时间
     */
    @Update("update dlx_message" +
            " set retry_count = #{retryCount}," +
            " status = #{status}," +
            " next_retry_time = #{nextRetryTime}" +
            " where id = #{id}")
    int updateRetryInfo(DlxMessageDO dlxMessageDO);

    /**
     * 更新消息状态
     */
    @Update("update dlx_message set status = #{status} where id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") String status);
}

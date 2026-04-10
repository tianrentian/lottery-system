package com.example.lotterysystem.service;

import com.example.lotterysystem.controller.param.CreateActivityParam;
import com.example.lotterysystem.controller.param.PageParam;
import com.example.lotterysystem.service.dto.ActivityDTO;
import com.example.lotterysystem.service.dto.ActivityDetailDTO;
import com.example.lotterysystem.service.dto.CreateActivityDTO;
import com.example.lotterysystem.service.dto.PageListDTO;

public interface ActivityService {
    /**
     * 创建活动
     * @param param
     *
     * @return
     */
    CreateActivityDTO createActivity(CreateActivityParam param);

    /**
     * 创建活动（带幂等性token校验）
     * @param param
     * @param idempotentToken 幂等性token
     * @return
     */
    CreateActivityDTO createActivity(CreateActivityParam param, String idempotentToken);

    /**
     * 生成幂等性token
     * @return token字符串
     */
    String generateIdempotentToken();

    /**
     * 翻页查询活动（摘要）列表
     *
     * @param param
     * @return
     */
    PageListDTO<ActivityDTO> findActivityList(PageParam param);

    /**
     * 获取活动详细属性
     *
     * @param activityId
     * @return
     */
    ActivityDetailDTO getActivityDetail(Long activityId);

    /**
     * 缓存活动详细信息（读取表数据再缓存）
     *
     * @param activityId
     */
    void cacheActivity(Long activityId);
}

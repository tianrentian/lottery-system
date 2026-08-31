package com.example.lotterysystem.service.activitystatus.impl;

import com.example.lotterysystem.common.errorcode.ServiceErrorCodeConstants;
import com.example.lotterysystem.common.exception.ServiceException;
import com.example.lotterysystem.service.ActivityService;
import com.example.lotterysystem.service.activitystatus.ActivityStatusManager;
import com.example.lotterysystem.service.activitystatus.operater.AbstractActivityOperator;
import com.example.lotterysystem.service.dto.ConvertActivityStatusDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

@Component
public class ActivityStatusManagerImpl implements ActivityStatusManager {

    private static final Logger logger = LoggerFactory.getLogger(ActivityStatusManagerImpl.class);

    @Autowired
    private final Map<String, AbstractActivityOperator> operatorMap = new HashMap<>();
    @Autowired
    private ActivityService activityService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handlerEvent(ConvertActivityStatusDTO convertActivityStatusDTO) {
        // 可解决这两个问题：1、活动状态扭转有依赖性，导致代码维护性差2、状态扭转条件可能会扩展，当前写法，扩展性差，维护性差

        if (CollectionUtils.isEmpty(operatorMap)) {
            logger.warn("operatorMap为空！");
            return;
        }
        // 数据复制：创建operatorMap的一个副本currMap，这是防止在处理过程中原数据被修改，保证操作的安全性。
        Map<String, AbstractActivityOperator> currMap = new HashMap<>(operatorMap);
        Boolean update = false;
        // 先处理：人员、奖品
        update = processConvertStatus(convertActivityStatusDTO, currMap, 1);

        // 后处理：活动
        update = processConvertStatus(convertActivityStatusDTO, currMap, 2) || update;

        // 更新缓存
        if (update) {
            activityService.cacheActivity(convertActivityStatusDTO.getActivityId());
        }

    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rollbackHandlerEvent(ConvertActivityStatusDTO convertActivityStatusDTO) {
        // operatorMap：活动、奖品、人员
        // 活动是否需要回滚？？ 绝对需要，原因：奖品都恢复成INIT，那么这个活动下的奖品绝对没抽完
        for (AbstractActivityOperator operator : operatorMap.values()) {
            operator.convert(convertActivityStatusDTO);
        }

        // 缓存更新
        activityService.cacheActivity(convertActivityStatusDTO.getActivityId());
    }

    /**
     * 扭转状态
     *
     * @param convertActivityStatusDTO
     * @param currMap
     * @param sequence
     * @return
     */
    private Boolean processConvertStatus(ConvertActivityStatusDTO convertActivityStatusDTO,
                                         Map<String, AbstractActivityOperator> currMap,
                                         int sequence) {
        Boolean update = false;
        // 遍历currMap
        Iterator<Map.Entry<String, AbstractActivityOperator>> iterator = currMap.entrySet().iterator();
        while (iterator.hasNext()) {
            AbstractActivityOperator operator = iterator.next().getValue();
            // Operator是否需要转换
            if (operator.sequence() != sequence
                    || !operator.needConvert(convertActivityStatusDTO)) {
                continue;
            }

            // 需要转换：转换
            if (!operator.convert(convertActivityStatusDTO)) {
                logger.error("{}转换失败！", operator.getClass().getName());
                throw new ServiceException(ServiceErrorCodeConstants.ACTIVITY_STATUS_CONVERT_ERROR);
            }

            // currMap 删除当前Operator
            iterator.remove();
            update = true;

        }

        // 返回
        return update;

    }
}
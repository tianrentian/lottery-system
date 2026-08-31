package com.example.lotterysystem.service.activitystatus.operater;

import com.example.lotterysystem.dao.dateobject.ActivityDO;
import com.example.lotterysystem.dao.mapper.ActivityMapper;
import com.example.lotterysystem.dao.mapper.ActivityPrizeMapper;
import com.example.lotterysystem.service.dto.ConvertActivityStatusDTO;
import com.example.lotterysystem.service.enums.ActivityPrizeStatusEnum;
import com.example.lotterysystem.service.enums.ActivityStatusEnum;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ActivityOperator extends AbstractActivityOperator {

    @Autowired
    private ActivityMapper activityMapper;
    @Autowired
    private ActivityPrizeMapper activityPrizeMapper;

    @Override
    public Integer sequence() {
        return 2;
    }

    @Override
    public Boolean needConvert(ConvertActivityStatusDTO convertActivityStatusDTO) {
        Long activityId = convertActivityStatusDTO.getActivityId();
        ActivityStatusEnum targetStatus = convertActivityStatusDTO.getTargetActivityStatus();
        if (activityId == null
                || targetStatus == null) {
            return false;
        }

        ActivityDO activityDO = activityMapper.selectById(activityId);
        if (activityDO == null) {
            return false;
        }

        // 当前活动状态与传入状态一致，不用处理
        if (targetStatus.name().equalsIgnoreCase(activityDO.getStatus())) {
            return false;
        }

        // 只有全部奖品都已完成时，活动才能完成。PROCESSING 仍表示处理中，不能提前结束活动。
        int count = activityPrizeMapper.countUncompletedPrize(
                activityId, ActivityPrizeStatusEnum.COMPLETED.name());
        if (count > 0) {
            return false;
        }

        return true;
    }

    @Override
    public Boolean convert(ConvertActivityStatusDTO convertActivityStatusDTO) {
        try {
            activityMapper.updateStatus(convertActivityStatusDTO.getActivityId(),
                    convertActivityStatusDTO.getTargetActivityStatus().name());
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}

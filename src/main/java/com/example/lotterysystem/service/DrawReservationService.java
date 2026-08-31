package com.example.lotterysystem.service;

import com.example.lotterysystem.common.errorcode.ServiceErrorCodeConstants;
import com.example.lotterysystem.common.exception.ServiceException;
import com.example.lotterysystem.controller.param.DrawPrizeParam;
import com.example.lotterysystem.dao.mapper.ActivityPrizeMapper;
import com.example.lotterysystem.dao.mapper.ActivityUserMapper;
import com.example.lotterysystem.service.enums.ActivityPrizeStatusEnum;
import com.example.lotterysystem.service.enums.ActivityUserStatusEnum;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 在消息投递前同步预占本次抽奖涉及的人员和奖品。
 * 最终中奖确认、中奖记录和通知仍由 MQ 消费端处理。
 */
@Service
public class DrawReservationService {

    @Autowired
    private ActivityPrizeMapper activityPrizeMapper;
    @Autowired
    private ActivityUserMapper activityUserMapper;

    @Transactional(rollbackFor = Exception.class)
    public void reserve(DrawPrizeParam param) {
        List<Long> userIds = winnerIds(param);
        int reservedPrize = activityPrizeMapper.updateStatusIfCurrent(
                param.getActivityId(), param.getPrizeId(),
                ActivityPrizeStatusEnum.INIT.name(), ActivityPrizeStatusEnum.PROCESSING.name());
        if (reservedPrize != 1) {
            throw new ServiceException(ServiceErrorCodeConstants.DRAW_PRIZE_IS_PROCESSING);
        }

        int reservedUsers = activityUserMapper.batchUpdateStatusIfCurrent(
                param.getActivityId(), userIds,
                ActivityUserStatusEnum.INIT.name(), ActivityUserStatusEnum.PROCESSING.name());
        if (reservedUsers != userIds.size()) {
            throw new ServiceException(ServiceErrorCodeConstants.DRAW_PRIZE_IS_PROCESSING);
        }
    }

    /**
     * 仅释放仍属于本次抽奖的 PROCESSING 状态，不影响已经完成的抽奖结果。
     */
    @Transactional(rollbackFor = Exception.class)
    public void release(DrawPrizeParam param) {
        List<Long> userIds = winnerIds(param);
        activityUserMapper.batchUpdateStatusIfCurrent(
                param.getActivityId(), userIds,
                ActivityUserStatusEnum.PROCESSING.name(), ActivityUserStatusEnum.INIT.name());
        activityPrizeMapper.updateStatusIfCurrent(
                param.getActivityId(), param.getPrizeId(),
                ActivityPrizeStatusEnum.PROCESSING.name(), ActivityPrizeStatusEnum.INIT.name());
    }

    private List<Long> winnerIds(DrawPrizeParam param) {
        if (param == null || CollectionUtils.isEmpty(param.getWinnerList())) {
            throw new ServiceException(ServiceErrorCodeConstants.ACTIVITY_USER_ERROR);
        }
        return param.getWinnerList().stream()
                .map(DrawPrizeParam.Winner::getUserId)
                .collect(Collectors.toList());
    }
}

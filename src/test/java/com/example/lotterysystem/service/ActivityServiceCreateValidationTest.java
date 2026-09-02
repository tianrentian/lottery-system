package com.example.lotterysystem.service;

import com.example.lotterysystem.common.exception.ServiceException;
import com.example.lotterysystem.controller.param.CreateActivityParam;
import com.example.lotterysystem.controller.param.CreatePrizeByActivityParam;
import com.example.lotterysystem.controller.param.CreateUserByActivityParam;
import com.example.lotterysystem.dao.mapper.ActivityMapper;
import com.example.lotterysystem.dao.mapper.ActivityPrizeMapper;
import com.example.lotterysystem.dao.mapper.ActivityUserMapper;
import com.example.lotterysystem.dao.mapper.PrizeMapper;
import com.example.lotterysystem.dao.mapper.UserMapper;
import com.example.lotterysystem.common.utils.RedisUtil;
import com.example.lotterysystem.service.impl.ActivityServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActivityServiceCreateValidationTest {

    @Mock
    private UserMapper userMapper;
    @Mock
    private PrizeMapper prizeMapper;
    @Mock
    private ActivityMapper activityMapper;
    @Mock
    private ActivityPrizeMapper activityPrizeMapper;
    @Mock
    private ActivityUserMapper activityUserMapper;
    @Mock
    private RedisUtil redisUtil;
    @InjectMocks
    private ActivityServiceImpl activityService;

    @Test
    void createShouldKeepOriginalRuleThatUsersCoverAllPrizeCopies() {
        CreateActivityParam param = createParam(2L, 1L);
        when(userMapper.selectExistByIds(Collections.singletonList(1L)))
                .thenReturn(Collections.singletonList(1L));
        when(prizeMapper.selectExistByIds(Collections.singletonList(1L)))
                .thenReturn(Collections.singletonList(1L));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> activityService.createActivity(param));

        assertEquals(303, exception.getCode());
        verify(activityMapper, never()).insert(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void createShouldRejectPrizeOutsideCurrentCatalogBeforeWritingActivity() {
        CreateActivityParam param = createParamWithPrizeIds(7L, 999L);
        when(userMapper.selectExistByIds(Collections.singletonList(1L)))
                .thenReturn(Collections.singletonList(1L));
        when(prizeMapper.selectExistByIds(Arrays.asList(7L, 999L)))
                .thenReturn(Collections.singletonList(7L));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> activityService.createActivity(param));

        assertEquals(302, exception.getCode());
        verify(activityMapper, never()).insert(org.mockito.ArgumentMatchers.any());
    }

    private CreateActivityParam createParam(long prizeAmount, long prizeId) {
        CreatePrizeByActivityParam prize = new CreatePrizeByActivityParam();
        prize.setPrizeId(prizeId);
        prize.setPrizeAmount(prizeAmount);
        prize.setPrizeTiers("FIRST_PRIZE");

        CreateUserByActivityParam user = new CreateUserByActivityParam();
        user.setUserId(1L);
        user.setUserName("测试用户");

        CreateActivityParam param = new CreateActivityParam();
        param.setActivityName("测试活动");
        param.setDescription("测试活动描述");
        param.setActivityPrizeList(Collections.singletonList(prize));
        param.setActivityUserList(Collections.singletonList(user));
        return param;
    }

    private CreateActivityParam createParamWithPrizeIds(long firstPrizeId, long secondPrizeId) {
        CreateActivityParam param = createParam(1L, firstPrizeId);
        CreatePrizeByActivityParam invalidPrize = new CreatePrizeByActivityParam();
        invalidPrize.setPrizeId(secondPrizeId);
        invalidPrize.setPrizeAmount(1L);
        invalidPrize.setPrizeTiers("SECOND_PRIZE");
        param.setActivityPrizeList(Arrays.asList(param.getActivityPrizeList().get(0), invalidPrize));
        return param;
    }
}

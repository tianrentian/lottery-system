package com.example.lotterysystem.service;

import com.example.lotterysystem.common.exception.ServiceException;
import com.example.lotterysystem.controller.param.DrawPrizeParam;
import com.example.lotterysystem.dao.mapper.ActivityPrizeMapper;
import com.example.lotterysystem.dao.mapper.ActivityUserMapper;
import com.example.lotterysystem.service.enums.ActivityPrizeStatusEnum;
import com.example.lotterysystem.service.enums.ActivityUserStatusEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DrawReservationServiceTest {

    @Mock
    private ActivityPrizeMapper activityPrizeMapper;
    @Mock
    private ActivityUserMapper activityUserMapper;
    @InjectMocks
    private DrawReservationService drawReservationService;

    private DrawPrizeParam param;

    @BeforeEach
    void setUp() {
        DrawPrizeParam.Winner firstWinner = new DrawPrizeParam.Winner();
        firstWinner.setUserId(101L);
        firstWinner.setUserName("测试候选人101");

        DrawPrizeParam.Winner secondWinner = new DrawPrizeParam.Winner();
        secondWinner.setUserId(102L);
        secondWinner.setUserName("测试候选人102");

        param = new DrawPrizeParam();
        param.setActivityId(10L);
        param.setPrizeId(20L);
        param.setWinningTime(new Date());
        param.setWinnerList(Arrays.asList(firstWinner, secondWinner));
    }

    @Test
    void reserveShouldMovePrizeAndAllWinnersToProcessing() {
        when(activityPrizeMapper.updateStatusIfCurrent(
                10L, 20L,
                ActivityPrizeStatusEnum.INIT.name(),
                ActivityPrizeStatusEnum.PROCESSING.name())).thenReturn(1);
        when(activityUserMapper.batchUpdateStatusIfCurrent(
                10L, Arrays.asList(101L, 102L),
                ActivityUserStatusEnum.INIT.name(),
                ActivityUserStatusEnum.PROCESSING.name())).thenReturn(2);

        assertDoesNotThrow(() -> drawReservationService.reserve(param));
    }

    @Test
    void reserveShouldRejectWhenAnyWinnerWasAlreadyReserved() {
        when(activityPrizeMapper.updateStatusIfCurrent(
                10L, 20L,
                ActivityPrizeStatusEnum.INIT.name(),
                ActivityPrizeStatusEnum.PROCESSING.name())).thenReturn(1);
        when(activityUserMapper.batchUpdateStatusIfCurrent(
                10L, Arrays.asList(101L, 102L),
                ActivityUserStatusEnum.INIT.name(),
                ActivityUserStatusEnum.PROCESSING.name())).thenReturn(1);

        assertThrows(ServiceException.class, () -> drawReservationService.reserve(param));
    }

    @Test
    void releaseShouldOnlyRestoreProcessingReservationToInit() {
        drawReservationService.release(param);

        verify(activityUserMapper).batchUpdateStatusIfCurrent(
                10L, Arrays.asList(101L, 102L),
                ActivityUserStatusEnum.PROCESSING.name(),
                ActivityUserStatusEnum.INIT.name());
        verify(activityPrizeMapper).updateStatusIfCurrent(
                10L, 20L,
                ActivityPrizeStatusEnum.PROCESSING.name(),
                ActivityPrizeStatusEnum.INIT.name());
    }
}

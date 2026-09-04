package com.example.lotterysystem.controller.handler;

import com.example.lotterysystem.common.exception.ControllerException;
import com.example.lotterysystem.common.exception.ServiceException;
import com.example.lotterysystem.common.pojo.CommonResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void serviceExceptionShouldKeepBusinessCodeAndReadableMessage() {
        CommonResult<?> result = handler.serviceException(
                new ServiceException(303, "参与人员数量不能少于奖品总数"));

        assertEquals(303, result.getCode());
        assertEquals("参与人员数量不能少于奖品总数", result.getMsg());
    }

    @Test
    void controllerExceptionShouldKeepBusinessCodeAndReadableMessage() {
        CommonResult<?> result = handler.controllerException(
                new ControllerException(403, "无权执行此操作"));

        assertEquals(403, result.getCode());
        assertEquals("无权执行此操作", result.getMsg());
    }

    @Test
    void unknownExceptionShouldNotExposeInternalDetails() {
        CommonResult<?> result = handler.Exception(
                new RuntimeException("SQL insert into activity failed at ActivityMapper.java"));

        assertEquals(500, result.getCode());
        assertEquals("系统异常", result.getMsg());
        assertFalse(result.getMsg().contains("SQL"));
        assertFalse(result.getMsg().contains("ActivityMapper"));
    }
}

package com.example.lotterysystem.controller.handler;

import com.example.lotterysystem.common.errorcode.GlobalErrorCodeConstants;
import com.example.lotterysystem.common.exception.ControllerException;
import com.example.lotterysystem.common.exception.ServiceException;
import com.example.lotterysystem.common.pojo.CommonResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice // 可以捕获全局的异常
public class GlobalExceptionHandler {

    private final static Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(value = ServiceException.class)
    public CommonResult<?> serviceException(ServiceException e) {
        // 打错误日志
        logger.error("serviceException:", e);
        // 业务异常只返回预先定义的错误码和可读提示
        return CommonResult.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(value = ControllerException.class)
    public CommonResult<?> controllerException(ControllerException e) {
        // 打错误日志
        logger.error("controllerException:", e);
        // 控制层异常只返回预先定义的错误码和可读提示
        return CommonResult.error(e.getCode(), e.getMessage());
    }
    @ExceptionHandler(value = Exception.class)
    public CommonResult<?> Exception(Exception e) {
        // 打错误日志
        logger.error("服务异常:", e);
        // 未知异常的具体内容仅保留在服务端日志，避免泄露 SQL、类名等内部信息
        return CommonResult.error(GlobalErrorCodeConstants.INTERNAL_SERVER_ERROR);
    }
}

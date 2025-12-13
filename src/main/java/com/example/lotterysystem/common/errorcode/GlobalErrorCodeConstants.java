package com.example.lotterysystem.common.errorcode;

public interface GlobalErrorCodeConstants {
    ErrorCode SUCCESS = new ErrorCode(200,"成功!");

    // ========== 服务端错误段 ==========

    ErrorCode INTERNAL_SERVER_ERROR = new ErrorCode(500,"系统异常");
    ErrorCode NOT_IMPLEMENTED = new ErrorCode(501, "功能未实现/未开启");
    ErrorCode ERROR_CONFIGURATION = new ErrorCode(502, "错误的配置项");

    ErrorCode UNKNOWN = new ErrorCode(999,"未知错误");
}

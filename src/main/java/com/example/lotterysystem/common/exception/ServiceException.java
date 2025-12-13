package com.example.lotterysystem.common.exception;

import com.example.lotterysystem.common.errorcode.ErrorCode;
import lombok.Data;
import lombok.EqualsAndHashCode;

// @Data 生成自己的 equals  hashcode
// 不写@EqualsAndHashCode(callSuper = true)  可能会出现问题
@Data
@EqualsAndHashCode(callSuper = true)
public class ServiceException extends RuntimeException{
    /**
     * 业务错误码
     *
     * @see com.example.lotterysystem.common.errorcode.ServiceErrorCodeConstants;
     */
    private Integer code;

    /**
     * 错误提⽰
     */
    private String message;

    /**
     * 空构造⽅法，避免反序列化问题
     */
    public  ServiceException() {

    }

    public ServiceException(Integer code,String message) {
        this.code = code;
        this.message = message;
    }

    public  ServiceException(ErrorCode errorCode) {
        this.code = errorCode.getCode();
        this.message = errorCode.getMsg();
    }
}

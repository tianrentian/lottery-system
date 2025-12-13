package com.example.lotterysystem.common.errorcode;

import lombok.Data;

@Data  //  为了少写一些get和set方法，
public class ErrorCode {
    // 错误码
    private final Integer code;

    // 错误提⽰
    private final String msg;

    public ErrorCode(Integer code, String msg) {
        this.code = code;
        this.msg = msg;
    }
}

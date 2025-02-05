package com.dd.yunpicturebackend.exception;

import lombok.Getter;

/**
 * 自定义异常
 */
@Getter
public class BusinessException extends RuntimeException {
    /**
     * 错误码
     */
    private final Integer code;
    public BusinessException(Integer code, String message) {
        super(message);
        this.code = code;
    }
    public BusinessException(ErrorCode errorcode) {
        super(errorcode.getMessage());
        this.code = errorcode.getCode();
    }
    public BusinessException(ErrorCode errorcode, String message) {
        super(message);
        this.code = errorcode.getCode();
    }
}

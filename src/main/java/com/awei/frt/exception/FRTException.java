package com.awei.frt.exception;

/**
 * FRT基础异常类
 * 所有FRT相关异常的基类
 */
public class FRTException extends Exception {
    
    public FRTException(String message) {
        super(message);
    }
    
    public FRTException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public FRTException(Throwable cause) {
        super(cause);
    }
}
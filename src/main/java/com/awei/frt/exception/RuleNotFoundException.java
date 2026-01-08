package com.awei.frt.exception;

/**
 * 规则未找到异常
 * 当指定的规则文件不存在时抛出
 */
public class RuleNotFoundException extends FRTException {
    
    public RuleNotFoundException(String message) {
        super(message);
    }
    
    public RuleNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
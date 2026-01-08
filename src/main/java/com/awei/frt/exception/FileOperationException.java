package com.awei.frt.exception;

/**
 * 文件操作异常
 * 当文件操作失败时抛出
 */
public class FileOperationException extends FRTException {
    
    public FileOperationException(String message) {
        super(message);
    }
    
    public FileOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}
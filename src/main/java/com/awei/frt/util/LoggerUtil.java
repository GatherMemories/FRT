package com.awei.frt.util;

import com.awei.frt.core.builder.ConfigLoader;
import com.awei.frt.model.Config;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 日志工具类
 * 用于记录控制台所有输出和异常信息，并按日期组织日志文件
 */
public class LoggerUtil {

    /** 单例实例 */
    private static LoggerUtil instance;

    /** 线程安全锁，确保多线程环境下的日志写入安全 */
    private static final ReentrantLock lock = new ReentrantLock();

    /** 系统配置信息，包含日志路径等参数 */
    private final Config config;

    /** 原始的标准输出流 */
    private final PrintStream originalOut;

    /** 原始的标准错误输出流 */
    private final PrintStream originalErr;

    /** 双重输出流（控制台 + 日志文件）的标准输出 */
    private PrintStream teeOut;

    /** 双重输出流（控制台 + 日志文件）的标准错误输出 */
    private PrintStream teeErr;

    /** 当前日志文件的完整路径 */
    private String currentLogFilePath;

    private LoggerUtil(Config config) {
        this.config = config;
        this.originalOut = System.out;
        this.originalErr = System.err;
        initializeLogger();
    }

    /**
     * 获取LoggerUtil实例
     */
    public static LoggerUtil getInstance(Config config) {
        if (config == null) {
            config = new Config();
        }
        if (instance == null) {
            lock.lock();
            try {
                if (instance == null) {
                    instance = new LoggerUtil(config);
                }
            } finally {
                lock.unlock();
            }
        }
        return instance;
    }


    /**
     * 初始化日志系统
     */
    private void initializeLogger() {
        try {
            // 创建日志目录
            Path logDir = config.getLogPath();
            if (!logDir.isAbsolute()) {
                logDir = config.getBaseDirectory().resolve(logDir);
            }

            if (!Files.exists(logDir)) {
                Files.createDirectories(logDir);
            }

            // 设置当前日志文件路径
            currentLogFilePath = getCurrentLogFilePath();

            // 创建TeeOutputStream，同时输出到控制台和日志文件
            TeeOutputStream teeOutStream = new TeeOutputStream(originalOut, getLogOutputStream());
            TeeOutputStream teeErrStream = new TeeOutputStream(originalErr, getLogOutputStream());

            teeOut = new PrintStream(teeOutStream, true);
            teeErr = new PrintStream(teeErrStream, true);

            // 重定向System.out和System.err
            System.setOut(teeOut);
            System.setErr(teeErr);
            System.out.println("\n");
            logInfo("日志系统初始化完成，日志文件路径: " + currentLogFilePath);

        } catch (Exception e) {
            // 如果日志初始化失败，回退到原始输出流
            System.err.println("日志系统初始化失败: " + e.getMessage());
            teeOut = originalOut;
            teeErr = originalErr;
        }
    }

    /**
     * 获取当前日志文件路径
     */
    private String getCurrentLogFilePath() {
        SimpleDateFormat yearFormat = new SimpleDateFormat("yyyy");
        SimpleDateFormat monthFormat = new SimpleDateFormat("MM");
        SimpleDateFormat dayFormat = new SimpleDateFormat("dd");

        Date now = new Date();
        String year = yearFormat.format(now);
        String month = monthFormat.format(now);
        String day = dayFormat.format(now);

        Path logDir = config.getLogPath();
        if (!logDir.isAbsolute()) {
            logDir = config.getBaseDirectory().resolve(logDir);
        }

        logDir = logDir.resolve(year + "-" + month + "-" + day + ".log");

        return logDir.toString();
    }

    /**
     * 获取日志文件输出流
     */
    private OutputStream getLogOutputStream() {
        try {
            Path logFile = Paths.get(currentLogFilePath);
            return Files.newOutputStream(logFile,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND,
                StandardOpenOption.WRITE);
        } catch (IOException e) {
            System.err.println("无法创建日志文件输出流: " + e.getMessage());
            return new OutputStream() {
                @Override
                public void write(int b) throws IOException {
                    // 空实现，忽略写入
                }
            };
        }
    }

    /**
     * 记录信息级别日志
     */
    public void logInfo(String message) {
        log("INFO", message, null);
    }

    /**
     * 记录警告级别日志
     */
    public void logWarn(String message) {
        log("WARN", message, null);
    }

    /**
     * 记录错误级别日志
     */
    public void logError(String message) {
        log("ERROR", message, null);
    }

    /**
     * 记录错误级别日志（带异常）
     */
    public void logError(String message, Throwable throwable) {
        log("ERROR", message, throwable);
    }

    /**
     * 记录调试级别日志
     */
    public void logDebug(String message) {
        if ("DEBUG".equals(config.getLogLevel())) {
            log("DEBUG", message, null);
        }
    }

    /**
     * 记录日志的通用方法
     */
    private void log(String level, String message, Throwable throwable) {
        lock.lock();
        try {
            // 检查是否需要切换日志文件（日期变化）
            String newLogFilePath = getCurrentLogFilePath();
            if (!newLogFilePath.equals(currentLogFilePath)) {
                currentLogFilePath = newLogFilePath;
                // 重新设置输出流
                resetOutputStreams();
            }

            // 格式化日志消息
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            String timestamp = dateFormat.format(new Date());
            String logMessage = String.format("[%s] [%s] %s", timestamp, level, message);

            // 写入日志
            PrintStream targetStream = level.equals("ERROR") ? teeErr : teeOut;
            targetStream.println(logMessage);

            // 如果有异常，记录异常堆栈
            if (throwable != null) {
                throwable.printStackTrace(targetStream);
            }

        } catch (Exception e) {
            // 如果日志写入失败，回退到原始输出
            originalErr.println("日志记录失败: " + e.getMessage());
            if (throwable != null) {
                originalErr.println("原始异常: " + message);
                throwable.printStackTrace(originalErr);
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * 重置输出流（当日志文件路径变化时）
     */
    private void resetOutputStreams() {
        try {
            // 关闭旧的输出流
            if (teeOut != null && teeOut != originalOut) {
                teeOut.close();
            }
            if (teeErr != null && teeErr != originalErr) {
                teeErr.close();
            }

            // 创建新的输出流
            TeeOutputStream teeOutStream = new TeeOutputStream(originalOut, getLogOutputStream());
            TeeOutputStream teeErrStream = new TeeOutputStream(originalErr, getLogOutputStream());

            teeOut = new PrintStream(teeOutStream, true);
            teeErr = new PrintStream(teeErrStream, true);

            // 重新设置System.out和System.err
            System.setOut(teeOut);
            System.setErr(teeErr);

        } catch (Exception e) {
            System.err.println("重置日志输出流失败: " + e.getMessage());
        }
    }

    /**
     * 关闭日志系统
     */
    public void close() {
        lock.lock();
        try {
            // 恢复原始输出流
            System.setOut(originalOut);
            System.setErr(originalErr);

            // 关闭输出流
            if (teeOut != null && teeOut != originalOut) {
                teeOut.close();
            }
            if (teeErr != null && teeErr != originalErr) {
                teeErr.close();
            }

            logInfo("日志系统已关闭");

        } finally {
            lock.unlock();
        }
    }

    /**
     * 自定义输出流，同时输出到两个目标
     */
    private static class TeeOutputStream extends OutputStream {
        private final OutputStream out1;
        private final OutputStream out2;

        public TeeOutputStream(OutputStream out1, OutputStream out2) {
            this.out1 = out1;
            this.out2 = out2;
        }

        @Override
        public void write(int b) throws IOException {
            try {
                out1.write(b);
            } catch (IOException e) {
                // 如果第一个流写入失败，尝试第二个流
                try {
                    out2.write(b);
                } catch (IOException ignored) {
                    // 忽略异常
                }
                throw e;
            }

            try {
                out2.write(b);
            } catch (IOException ignored) {
                // 忽略第二个流的异常，保证至少一个流能正常工作
            }
        }

        @Override
        public void write(byte[] b) throws IOException {
            write(b, 0, b.length);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            try {
                out1.write(b, off, len);
            } catch (IOException e) {
                // 如果第一个流写入失败，尝试第二个流
                try {
                    out2.write(b, off, len);
                } catch (IOException ignored) {
                    // 忽略异常
                }
                throw e;
            }

            try {
                out2.write(b, off, len);
            } catch (IOException ignored) {
                // 忽略第二个流的异常
            }
        }

        @Override
        public void flush() throws IOException {
            try {
                out1.flush();
            } catch (IOException ignored) {
                // 忽略异常
            }

            try {
                out2.flush();
            } catch (IOException ignored) {
                // 忽略异常
            }
        }

        @Override
        public void close() throws IOException {
            try {
                out1.close();
            } catch (IOException ignored) {
                // 忽略异常
            }

            try {
                out2.close();
            } catch (IOException ignored) {
                // 忽略异常
            }
        }
    }
}

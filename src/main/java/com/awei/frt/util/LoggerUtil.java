package com.awei.frt.util;

import com.awei.frt.model.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * 日志工具类
 * 使用SLF4J和Logback实现日志功能
 */
public class LoggerUtil {

    private static LoggerUtil instance;

    private final Logger logger;
    private final Logger fileOnlyLogger;
    private final Logger stdoutLogger;
    private final Logger stderrLogger;
    private final Config config;
    private final PrintStream originalOut;
    private final PrintStream originalErr;
    private static boolean initialized = false;

    private volatile boolean captureSystemOutput = true;

    private static final Pattern LOG_PATTERN = Pattern.compile("^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2} \\[.*\\] (INFO|WARN|ERROR|DEBUG|TRACE)");

    private LoggerUtil(Config config) {
        this.config = config;
        this.originalOut = System.out;
        this.originalErr = System.err;
        this.logger = LoggerFactory.getLogger(LoggerUtil.class);
        this.fileOnlyLogger = LoggerFactory.getLogger("FILE_ONLY");
        this.stdoutLogger = LoggerFactory.getLogger("System.out");
        this.stderrLogger = LoggerFactory.getLogger("System.err");
    }

    public static LoggerUtil getInstance(Config config) {
        if (config == null) {
            config = new Config();
        }
        if (instance == null) {
            synchronized (LoggerUtil.class) {
                if (instance == null) {
                    instance = new LoggerUtil(config);
                }
            }
        }
        if (!initialized) {
            instance.initializeLogger();
            initialized = true;
        }
        return instance;
    }

    private void initializeLogger() {
        Charset charset = StandardCharsets.UTF_8;
        System.setOut(new PrintStream(new LoggingOutputStream(stdoutLogger, LoggingOutputStream.Level.INFO, originalOut, charset), true, charset));
        System.setErr(new PrintStream(new LoggingOutputStream(stderrLogger, LoggingOutputStream.Level.ERROR, originalErr, charset), true, charset));

        logger.info("日志系统初始化完成");
    }

    /**
     * 开启 System.out/System.err 记录到日志
     */
    public void enableSystemOutputCapture() {
        this.captureSystemOutput = true;
    }

    /**
     * 关闭 System.out/System.err 记录到日志
     */
    public void disableSystemOutputCapture() {
        this.captureSystemOutput = false;
    }

    /**
     * 设置 System.out/System.err 是否记录到日志
     * @param capture true: 记录到日志, false: 不记录到日志
     */
    public void setSystemOutputCapture(boolean capture) {
        this.captureSystemOutput = capture;
    }

    /**
     * 获取 System.out/System.err 是否记录到日志的状态
     * @return true: 正在记录, false: 未记录
     */
    public boolean isSystemOutputCaptureEnabled() {
        return this.captureSystemOutput;
    }

    /**
     * 记录信息级别日志（控制台+文件）
     */
    public void logInfo(String message) {
        logger.info(message);
    }

    /**
     * 记录信息级别日志（仅文件）
     */
    public void logInfoFileOnly(String message) {
        fileOnlyLogger.info(message);
    }

    /**
     * 记录警告级别日志（控制台+文件）
     */
    public void logWarn(String message) {
        logger.warn(message);
    }

    /**
     * 记录警告级别日志（仅文件）
     */
    public void logWarnFileOnly(String message) {
        fileOnlyLogger.warn(message);
    }

    /**
     * 记录错误级别日志（控制台+文件）
     */
    public void logError(String message) {
        logger.error(message);
    }

    /**
     * 记录错误级别日志（仅文件）
     */
    public void logErrorFileOnly(String message) {
        fileOnlyLogger.error(message);
    }

    /**
     * 记录错误级别日志（带异常，控制台+文件）
     */
    public void logError(String message, Throwable throwable) {
        logger.error(message, throwable);
    }

    /**
     * 记录错误级别日志（带异常，仅文件）
     */
    public void logErrorFileOnly(String message, Throwable throwable) {
        fileOnlyLogger.error(message, throwable);
    }

    /**
     * 记录调试级别日志（控制台+文件）
     */
    public void logDebug(String message) {
        logger.debug(message);
    }

    /**
     * 记录调试级别日志（仅文件）
     */
    public void logDebugFileOnly(String message) {
        fileOnlyLogger.debug(message);
    }

    public void close() {
        System.setOut(originalOut);
        System.setErr(originalErr);
        logger.info("日志系统已关闭");
        initialized = false;
    }

    private static class LoggingOutputStream extends OutputStream {
        private final Logger logger;
        private final Level level;
        private final PrintStream originalStream;
        private final Charset charset;
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        enum Level {
            INFO, ERROR
        }

        LoggingOutputStream(Logger logger, Level level, PrintStream originalStream, Charset charset) {
            this.logger = logger;
            this.level = level;
            this.originalStream = originalStream;
            this.charset = charset;
        }

        @Override
        public void write(int b) {
            originalStream.write(b);
            buffer.write(b);
            if (b == '\n') {
                flushBuffer();
            }
        }

        @Override
        public void write(byte[] b, int off, int len) {
            originalStream.write(b, off, len);
            buffer.write(b, off, len);
            for (int i = off; i < off + len; i++) {
                if (b[i] == '\n') {
                    flushBuffer();
                    break;
                }
            }
        }

        @Override
        public void flush() {
            originalStream.flush();
            if (buffer.size() > 0) {
                flushBuffer();
            }
        }

        private void flushBuffer() {
            if (buffer.size() > 0) {
                String message = buffer.toString(charset).trim();
                if (!message.isEmpty() && !LOG_PATTERN.matcher(message).find()) {
                    if (instance != null && instance.captureSystemOutput) {
                        if (level == Level.INFO) {
                            logger.info(message);
                        } else {
                            logger.error(message);
                        }
                    }
                }
                buffer.reset();
            }
        }
    }
}

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
    private static boolean closed = false;

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
        // close() 后不再重新接管 System.out/err（否则后台线程日志会把已还原的流二次包装，
        // 且 originalOut 指向的旧流与当前流不一致）；日志仍可写入（logback 直接持文件 appender）
        if (!initialized && !closed) {
            synchronized (LoggerUtil.class) {
                if (!initialized && !closed) {
                    instance.initializeLogger();
                    initialized = true;
                }
            }
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
     * 记录信息级别日志（仅文件）
     */
    public void logInfoFileOnly(String message) {
        fileOnlyLogger.info(message);
    }

    /**
     * 记录警告级别日志（仅文件）
     */
    public void logWarnFileOnly(String message) {
        fileOnlyLogger.warn(message);
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
     * 记录调试级别日志（仅文件）
     */
    public void logDebugFileOnly(String message) {
        fileOnlyLogger.debug(message);
    }

    // ==================== 静态便捷方法（统一异常收口用） ====================

    /**
     * 统一记录异常（控制台+文件，含完整堆栈）
     * 替代散落的 e.printStackTrace() / System.err.println
     * @param message 上下文描述（可为空）
     * @param throwable 异常对象
     */
    public static void logException(String message, Throwable throwable) {
        if (throwable == null) {
            return;
        }
        LoggerUtil util = getInstance(null);
        // 控制台/UI 只输出简洁提示：优先调用方给的中文上下文；
        // 否则用简短异常类名+消息（不用 toString() 的全限定名，避免 java.lang.xxx 刷屏）。
        // 完整堆栈由 logback FILE appender 的 %ex 记录到日志文件，供排查使用。
        String brief = (message != null && !message.isBlank())
                ? message
                : throwable.getClass().getSimpleName()
                        + (throwable.getMessage() != null ? ": " + throwable.getMessage() : "");
        util.logger.error(brief, throwable);
    }

    /**
     * 统一记录异常（控制台+文件，含完整堆栈）
     * @param throwable 异常对象
     */
    public static void logException(Throwable throwable) {
        logException(null, throwable);
    }

    /**
     * 统一记录错误消息（控制台+文件）
     * 替代散落的 System.err.println
     * @param message 错误消息
     */
    public static void logErrorMsg(String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        getInstance(null).logger.error(message);
    }

    /**
     * 统一记录信息级别日志（控制台+文件）
     * 业务关键事件（操作开始/完成/统计等）请使用本方法替代 System.out.println
     */
    public static void logInfo(String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        getInstance(null).logger.info(message);
    }

    /**
     * 统一记录调试级别日志（控制台+文件）
     * 细碎过程日志（如单文件被白名单过滤跳过）使用本方法，默认不显示，
     * 需在配置/日志中开 DEBUG 级别才可见，避免正常操作刷屏。
     */
    public static void logDebug(String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        getInstance(null).logger.debug(message);
    }

    /**
     * 统一记录警告级别日志（控制台+文件）
     */
    public static void logWarn(String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        getInstance(null).logger.warn(message);
    }

    /**
     * 统一记录错误级别日志（控制台+文件）
     */
    public static void logError(String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        getInstance(null).logger.error(message);
    }

    /**
     * 按配置的日志级别动态设置 logback 中 com.awei.frt 包日志器的级别
     * （config.json 的 logLevel 字段真正生效的入口）。
     *
     * 背景：logback.xml 对 com.awei.frt 与 root 均硬编码 INFO，导致配置里的
     * DEBUG/WARN/ERROR 从不生效（logDebug 输出永不可见）。本方法在配置加载成功后
     * 调用一次即可让级别立即生效；调用时机早于 LoggerUtil 初始化也安全
     * （logback 工厂可独立工作，且 initializeLogger 不会覆盖已设级别）。
     *
     * @param level 配置中的日志级别（INFO/DEBUG/WARN/ERROR/TRACE，大小写不敏感）；
     *              null/空白/非法值 = 不修改（保持 logback.xml 的 INFO）
     */
    public static void applyLogLevel(String level) {
        if (level == null || level.isBlank()) {
            return;
        }
        try {
            String upper = level.trim().toUpperCase(java.util.Locale.ROOT);
            ch.qos.logback.classic.Level target = ch.qos.logback.classic.Level.toLevel(upper, null);
            if (target == null) {
                return; // 非法级别，保持默认
            }
            // 动态设置 com.awei.frt 包日志器（logback.xml 已为其装配 CONSOLE+FILE appender，
            // additivity=false；子树日志器未单独设级别时继承该级别）
            org.slf4j.Logger pkgLogger = LoggerFactory.getLogger("com.awei.frt");
            if (pkgLogger instanceof ch.qos.logback.classic.Logger lb) {
                lb.setLevel(target);
            }
        } catch (Exception e) {
            // 设置失败不影响主流程（保持默认级别）
        }
    }

    public void close() {
        System.setOut(originalOut);
        System.setErr(originalErr);
        logger.info("日志系统已关闭");
        initialized = false;
        closed = true;
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
            // 审查 low-7：一次批量写入可能含多行（多个换行）。必须"边写缓冲边按换行切分"，
            // 不能整段先入 buffer 再 flush（那样首行后的行仍与首行合并成一条日志）。
            int end = off + len;
            int chunkStart = off;
            for (int i = off; i < end; i++) {
                if (b[i] == '\n') {
                    buffer.write(b, chunkStart, i - chunkStart + 1); // 含换行
                    flushBuffer();
                    chunkStart = i + 1;
                }
            }
            if (chunkStart < end) {
                buffer.write(b, chunkStart, end - chunkStart); // 无换行的尾部留待下次
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

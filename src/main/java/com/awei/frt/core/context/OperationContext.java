package com.awei.frt.core.context;

import com.awei.frt.core.builder.BackupFileLoader;
import com.awei.frt.model.Config;
import com.awei.frt.model.OperationRecord;
import com.awei.frt.model.ProcessingResult;

import java.nio.file.Path;
import java.util.Map;

import com.awei.frt.util.LoggerUtil;

/**
 * 操作上下文
 * 管理操作的状态和执行结果
 */
public class OperationContext {
    private final Config config;
    private final Path basePath;              // 基准路径，用于计算相对路径
    private final Path targetBasePath;        // 目标基准路径，文件操作的目标目录

    private RuleInheritanceContext ruleInheritanceContext; // 规则继承上下文，管理规则继承关系
    private final ProcessingResult processingResult;       // 处理结果对象，汇总处理结果
    private boolean dryRun = false;                        // 预览模式：只收集操作计划，不执行文件 IO、不落盘会话记录
    private ProgressCallback progressCallback;             // 进度回调（null = 不上报）
    private int progressTotal = 0;                         // 总文件数
    private int progressDone = 0;                          // 已处理文件数

    // 操作类型（用于 ProcessingResult-->OperationRecord-->operationType）
    public static final String OPERATION_RENAME = "operation_rename";
    public static final String OPERATION_ADD = "operation_add";
    public static final String OPERATION_REPLACE = "operation_replace";
    public static final String OPERATION_DELETE = "operation_delete";

    /**
     * 构造函数，初始化操作上下文
     * @param config 配置对象
     */
    public OperationContext(Config config) {
        this.config = config;
        this.basePath = config.getBaseDirectory();
        this.targetBasePath = basePath.resolve(config.getTargetPath());
        this.ruleInheritanceContext = new RuleInheritanceContext(); // 初始化默认规则继承上下文
        this.processingResult = new ProcessingResult();
    }

    /**
     * 获取配置对象
     */
    public Config getConfig() {
        return config;
    }

    /**
     * 获取目标路径
     * @param relativePath 相对路径
     * @return 标准化的目标路径
     */
    public Path getTargetPath(String relativePath) {
        return targetBasePath.resolve(relativePath).normalize();
    }

    /**
     * 获取基准路径
     * @return 基准路径
     */
    public Path getBasePath() {
        return basePath;
    }

    /**
     * 打印处理统计信息
     */
    public void printStatistics() {
        System.out.println("-----------------------------------------");
        LoggerUtil.logInfo("[STATS] 处理统计: 成功 " + getSuccessCount() + " 个文件"
                + (getSkipCount() > 0 ? ", 跳过 " + getSkipCount() + " 个文件" : "")
                + (getErrorCount() > 0 ? ", 失败 " + getErrorCount() + " 个文件" : ""));
        System.out.println("-----------------------------------------");
    }


    /**
     * 获取规则继承上下文
     * @return 规则继承上下文
     */
    public RuleInheritanceContext getRuleInheritanceContext() {
        return ruleInheritanceContext;
    }

    /**
     * 设置规则继承上下文
     * @param ruleInheritanceContext 规则继承上下文
     */
    public void setRuleInheritanceContext(RuleInheritanceContext ruleInheritanceContext) {
        this.ruleInheritanceContext = ruleInheritanceContext;
    }

    /**
     * 获取成功操作计数
     * @return 成功操作计数
     */
    public int getSuccessCount() {
        return this.processingResult.getSuccessCount();
    }

    /**
     * 获取跳过操作计数
     * @return 跳过操作计数
     */
    public int getSkipCount() {
        return this.processingResult.getSkipCount();
    }

    /**
     * 获取错误操作计数
     * @return 错误操作计数
     */
    public int getErrorCount() {
        return this.processingResult.getErrorCount();
    }

    /**
     * 记录一次操作并实时落盘（异常中断后可恢复）
     * @param record 操作记录
     */
    public void recordOperation(OperationRecord record) {
        processingResult.addOperationRecord(record);
        // 每次操作后增量追加会话记录（JSON Lines 一行一条，防止异常中断导致记录丢失）
        // 预览模式（dryRun）不落盘，避免把"计划"当成"已执行"写入恢复记录
        if (!dryRun) {
            BackupFileLoader.appendSessionRecord(record);
        }
    }

    /**
     * 是否预览模式（只收集操作计划，不真正改动文件）
     */
    public boolean isDryRun() {
        return dryRun;
    }

    /**
     * 设置预览模式
     */
    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
    }

    /**
     * 绑定进度回调（真实执行阶段使用；预览阶段不绑定）
     * @param callback 回调，null 表示不上报
     * @param total    总文件数
     */
    public void setProgressCallback(ProgressCallback callback, int total) {
        this.progressCallback = callback;
        this.progressTotal = Math.max(0, total);
        this.progressDone = 0;
    }

    /**
     * 上报一次进度（每个文件叶子节点处理时调用一次）
     * @param current 当前处理的文件相对路径
     */
    public void reportProgress(String current) {
        progressDone++;
        if (progressCallback != null) {
            progressCallback.onProgress(progressDone, progressTotal, current == null ? "" : current);
        }
    }

    /**
     * 获取当前生效规则中的策略扩展参数（replacements 键值对）
     * @param key 参数名
     * @return 参数值，未配置返回 null
     */
    public String getRuleParam(String key) {
        RuleInheritanceContext ric = getRuleInheritanceContext();
        if (ric == null || ric.getRuleChain() == null) {
            return null;
        }
        Map<String, String> replacements = ric.getRuleChain().getReplacements();
        return replacements == null ? null : replacements.get(key);
    }

    /**
     * 获取处理结果对象
     * @return 处理结果对象
     */
    public ProcessingResult getProcessingResult() {
        return processingResult;
    }

    /**
     * 获取相对路径
     * @param path 路径
     * @return 相对路径
     */
    public Path getRelativePath(Path path) {
        try {
            return basePath.relativize(path).normalize();
        } catch (Exception e) {
            // 如果无法相对化，则返回原始路径
            return path;
        }
    }
}

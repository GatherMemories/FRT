package com.awei.frt.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 恢复结果模型
 * 存储文件恢复操作的总体结果
 */
public class RestoreResult {
    private LocalDateTime restoreTime;      // 恢复时间
    private int successCount;               // 成功恢复的文件数
    private int failureCount;               // 恢复失败的文件数
    private int skipCount;                  // 用户选择跳过（保留当前内容，未恢复）的文件数
    private int rollbackCount;              // 回滚的文件数
    private boolean partialRestore;         // 是否部分恢复（是否有失败）
    private List<String> failureMessages;  // 失败信息列表

    public RestoreResult() {
        this.restoreTime = LocalDateTime.now();
        this.successCount = 0;
        this.failureCount = 0;
        this.skipCount = 0;
        this.rollbackCount = 0;
        this.partialRestore = false;
        this.failureMessages = new ArrayList<>();
    }

    // Getter 和 Setter 方法
    public LocalDateTime getRestoreTime() {
        return restoreTime;
    }

    public void setRestoreTime(LocalDateTime restoreTime) {
        this.restoreTime = restoreTime;
    }

    public int getSuccessCount() {
        return successCount;
    }

    public void setSuccessCount(int successCount) {
        this.successCount = successCount;
    }

    public int getFailureCount() {
        return failureCount;
    }

    public void setFailureCount(int failureCount) {
        this.failureCount = failureCount;
    }

    public int getRollbackCount() {
        return rollbackCount;
    }

    public void setRollbackCount(int rollbackCount) {
        this.rollbackCount = rollbackCount;
    }

    public int getSkipCount() {
        return skipCount;
    }

    public void setSkipCount(int skipCount) {
        this.skipCount = skipCount;
    }

    public boolean isPartialRestore() {
        return partialRestore;
    }

    public void setPartialRestore(boolean partialRestore) {
        this.partialRestore = partialRestore;
    }

    public List<String> getFailureMessages() {
        return failureMessages;
    }

    public void setFailureMessages(List<String> failureMessages) {
        this.failureMessages = failureMessages != null ? failureMessages : new ArrayList<>();
    }

    /**
     * 增加成功计数
     */
    public void incrementSuccess() {
        this.successCount++;
    }

    /**
     * 增加失败计数
     * @param message 失败信息
     */
    public void incrementFailure(String message) {
        this.failureCount++;
        this.partialRestore = true;
        if (message != null) {
            this.failureMessages.add(message);
        }
    }

    /**
     * 增加"用户跳过"计数：目标被用户改动/改名、用户明确选择保留时调用。
     * 跳过不是失败——不计入 failureCount、不加入 failureMessages，
     * 以便上层区分"真正失败"与"用户有意保留"，且跳过项不会进入回滚清单
     * （避免回滚覆盖用户刻意保留的文件）。
     */
    public void incrementSkip() {
        this.skipCount++;
    }

    /**
     * 增加回滚计数
     */
    public void incrementRollback() {
        this.rollbackCount++;
    }

    /**
     * 判断恢复是否完全成功：无失败、无回滚、且无用户跳过。
     * 跳过的文件（用户选择保留）没有恢复到操作前状态，因此也不算"完全成功"；
     * 但它区别于失败——不计入 failureCount/failureMessages，也不进入回滚清单。
     */
    public boolean isFullSuccess() {
        return failureCount == 0 && rollbackCount == 0 && skipCount == 0;
    }

    @Override
    public String toString() {
        return "RestoreResult{" +
                "restoreTime=" + restoreTime +
                ", successCount=" + successCount +
                ", failureCount=" + failureCount +
                ", rollbackCount=" + rollbackCount +
                ", partialRestore=" + partialRestore +
                '}';
    }
}

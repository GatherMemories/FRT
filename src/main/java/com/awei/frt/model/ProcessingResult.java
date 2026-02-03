package com.awei.frt.model;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 处理结果模型
 * 存储文件处理操作的总体结果
 */
public class ProcessingResult {
    private LocalDateTime resultTime;  // 处理结果时间
    private int successCount;          // 成功处理的文件数
    private int skipCount;             // 跳过的文件数
    private int errorCount;            // 错误的文件数
    private List<OperationRecord> operationRecords; // 操作记录列表
    private boolean success;           // 整体操作是否成功
    private Path resultPath;           // 结果文件路径

    public ProcessingResult() {
        this.resultTime = LocalDateTime.now();
        this.successCount = 0;
        this.skipCount = 0;
        this.errorCount = 0;
        this.operationRecords = new ArrayList<>();
        this.success = true;
    }

    // Getter 和 Setter 方法
    public Path getResultPath() {
        return resultPath;
    }

    public void setResultPath(Path resultPath) {
        this.resultPath = resultPath;
    }

    public LocalDateTime getResultTime() {
        return resultTime;
    }

    public void setResultTime(LocalDateTime resultTime) {
        this.resultTime = resultTime;
    }

    public int getSuccessCount() {
        return successCount;
    }

    public void setSuccessCount(int successCount) {
        this.successCount = successCount;
    }

    public int getSkipCount() {
        return skipCount;
    }

    public void setSkipCount(int skipCount) {
        this.skipCount = skipCount;
    }

    public int getErrorCount() {
        return errorCount;
    }

    public void setErrorCount(int errorCount) {
        this.errorCount = errorCount;
        this.success = errorCount == 0;
    }

    public List<OperationRecord> getOperationRecords() {
        return operationRecords;
    }

    public void setOperationRecords(List<OperationRecord> operationRecords) {
        this.operationRecords = operationRecords != null ? operationRecords : new ArrayList<>();
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    /**
     * 添加操作记录
     */
    public void addOperationRecord(OperationRecord record) {
        operationRecords.add(record);

        // 根据记录更新计数
        if (record.isSuccess()) {
            successCount++;
        } else {
            errorCount++;
            success = false;
        }
    }

    @Override
    public String toString() {
        return "ProcessingResult{" +
                "successCount=" + successCount +
                ", skipCount=" + skipCount +
                ", errorCount=" + errorCount +
                ", totalRecords=" + operationRecords.size() +
                ", success=" + success +
                '}';
    }
}

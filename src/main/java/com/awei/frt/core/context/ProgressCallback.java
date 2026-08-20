package com.awei.frt.core.context;

/**
 * 处理进度回调（服务层 → UI 进度条）
 * 由服务在真实执行阶段逐文件上报，UI 侧（SwingWorker）用于刷新进度条与状态栏。
 */
@FunctionalInterface
public interface ProgressCallback {

    /**
     * 上报一次进度
     * @param processed 已处理文件数
     * @param total     总文件数（0 表示未知）
     * @param current   当前处理的文件相对路径（可为空字符串）
     */
    void onProgress(int processed, int total, String current);
}

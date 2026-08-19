package com.awei.frt;

import com.awei.frt.core.builder.ConfigLoader;

import java.nio.file.Path;

/**
 * 测试辅助：把备份路径隔离到临时目录，避免测试写入真实 testDic/backup
 */
public final class TestSupport {

    // 真实备份路径（类加载时初始化 ConfigLoader 后捕获）
    private static final Path REAL_BACKUP;

    static {
        ConfigLoader.getConfig();
        REAL_BACKUP = ConfigLoader.getBackupPath();
    }

    private TestSupport() {
    }

    /** 把备份路径指向临时目录（隔离） */
    public static void isolateBackup(Path tempRoot) {
        ConfigLoader.setBackupPathForTesting(tempRoot.resolve("backup"));
    }

    /** 恢复真实备份路径 */
    public static void restoreBackupPath() {
        ConfigLoader.setBackupPathForTesting(REAL_BACKUP);
    }
}

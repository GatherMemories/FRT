package com.awei.frt.service;

import com.awei.frt.core.builder.BackupFileLoader;
import com.awei.frt.model.Config;
import com.awei.frt.model.ProcessingResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Scanner;

/**
 * 恢复服务
 * 用于从备份中恢复文件
 */
public class RestoreService {

    private final Config config;
    private final Scanner scanner;
    private final Map<String, Path> backupFiles;

    public RestoreService(Config config, Scanner scanner) {
        this.config = config;
        this.scanner = scanner;
        backupFiles = BackupFileLoader.getBackupFiles();
    }


}

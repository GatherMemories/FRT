package com.awei.frt.core.builder;

import com.awei.frt.core.uitls.FileSignUtil;
import com.awei.frt.model.Config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * @Author: mou_ren
 * @Date: 2026/1/24 22:17
 * @Description: 备份文件加载器
 */
public class BackupFileLoader {
    // 加载的备份文件列表
    private static Map<String, Path> backupFiles;

    public static Map<String, Path> getBackupFiles() {
        if (backupFiles == null) {
            Config config = ConfigLoader.getConfig();
            if (config == null) {
                return null;
            }
            backupFiles = loadBackupFiles(config
                    .getBaseDirectory()
                    .resolve(config.getBackupPath()).normalize());
        }
        return backupFiles;
    }

    /**
     * 加载备份文件列表
     * @param backupPath 备份目录路径
     */
    public static Map<String, Path>loadBackupFiles(Path backupPath) {
        if (Files.exists(backupPath)) {
            // 清空旧数据，避免重复加载
            backupFiles.clear();
            try (Stream<Path> paths = Files.walk(backupPath)) {
                paths.filter(Files::isRegularFile) // 只保留文件
                        .forEach(filePath -> {
                            if (backupFiles == null) {
                                backupFiles = new HashMap<>();
                            }
                            String fileMd5 = FileSignUtil.getFileMd5(filePath); // 获取文件的MD5特征码
                            backupFiles.put(fileMd5, filePath);
                        });
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        }
        return backupFiles;
    }

    /**
     * 增加备份文件
     * @param filePath 文件路径
     * @return 是否成功
     */
    public static boolean addBackupFile(Path filePath) {
        try {
            if (Files.isRegularFile(filePath)) {
                // 检查文件是否已存在于备份文件列表中（存在更改为新路径）
                String fileMd5 = FileSignUtil.getFileMd5(filePath);
                if (backupFiles.containsKey(fileMd5)) {
                    backupFiles.put(fileMd5, filePath);
                    return true;
                }

                // 备份文件
                Config config = ConfigLoader.getConfig();
                Path backupPath = config.getBaseDirectory().resolve(config.getBackupPath());
                Files.copy(filePath, backupPath.resolve(filePath.getFileName()), StandardCopyOption.REPLACE_EXISTING);
                backupFiles.put(fileMd5, filePath);
                return true;
            }
            return false;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 删除备份文件
     * @param filePath 文件路径
     * @return 是否成功
     */
    public static boolean deleteBackupFile(Path filePath) {
        try {
            if (Files.isRegularFile(filePath)) {
                String fileMd5 = FileSignUtil.getFileMd5(filePath);
                if (backupFiles.containsKey(fileMd5)) {
                    Files.delete(filePath);
                    backupFiles.remove(fileMd5);
                    return true;
                }
            }
            return false;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }
}

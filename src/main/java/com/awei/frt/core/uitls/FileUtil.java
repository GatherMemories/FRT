package com.awei.frt.core.uitls;

/**
 * @deprecated 包名拼写修正为 {@code com.awei.frt.core.utils}（原 uitls 系笔误）。
 * 本类仅保留旧包名作为<b>二进制兼容转发层</b>：早期版本发布的外部策略插件
 * （plugins/*.jar）编译时引用的 {@code com.awei.frt.core.uitls.FileUtil} 仍能加载执行；
 * 新代码请改用 {@link com.awei.frt.core.utils.FileUtil}。后续大版本可直接移除。
 */
@Deprecated
public final class FileUtil {

    private FileUtil() {
    }

    @Deprecated
    public static boolean addFile(java.nio.file.Path sourcePath, java.nio.file.Path targetPath,
                                  com.awei.frt.model.OperationRecord record) {
        return com.awei.frt.core.utils.FileUtil.addFile(sourcePath, targetPath, record);
    }

    @Deprecated
    public static boolean addFile(java.nio.file.Path sourcePath, java.nio.file.Path targetPath,
                                  com.awei.frt.model.OperationRecord record, boolean dryRun) {
        return com.awei.frt.core.utils.FileUtil.addFile(sourcePath, targetPath, record, dryRun);
    }

    @Deprecated
    public static boolean replaceFile(java.nio.file.Path sourcePath, java.nio.file.Path targetPath,
                                      com.awei.frt.model.OperationRecord record) {
        return com.awei.frt.core.utils.FileUtil.replaceFile(sourcePath, targetPath, record);
    }

    @Deprecated
    public static boolean replaceFile(java.nio.file.Path sourcePath, java.nio.file.Path targetPath,
                                      com.awei.frt.model.OperationRecord record, boolean dryRun) {
        return com.awei.frt.core.utils.FileUtil.replaceFile(sourcePath, targetPath, record, dryRun);
    }

    @Deprecated
    public static boolean deleteFile(java.nio.file.Path filePath, com.awei.frt.model.OperationRecord record) {
        return com.awei.frt.core.utils.FileUtil.deleteFile(filePath, record);
    }

    @Deprecated
    public static boolean deleteFile(java.nio.file.Path filePath, com.awei.frt.model.OperationRecord record,
                                     boolean dryRun) {
        return com.awei.frt.core.utils.FileUtil.deleteFile(filePath, record, dryRun);
    }
}

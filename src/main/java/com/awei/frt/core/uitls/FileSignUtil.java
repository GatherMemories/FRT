package com.awei.frt.core.uitls;

/**
 * @deprecated 包名拼写修正为 {@code com.awei.frt.core.utils}（原 uitls 系笔误）。
 * 本类仅保留旧包名作为<b>二进制兼容转发层</b>：早期版本发布的外部策略插件
 * （plugins/*.jar）编译时引用的 {@code com.awei.frt.core.uitls.FileSignUtil} 仍能加载执行；
 * 新代码请改用 {@link com.awei.frt.core.utils.FileSignUtil}。后续大版本可直接移除。
 */
@Deprecated
public final class FileSignUtil {

    private FileSignUtil() {
    }

    @Deprecated
    public static String getFileMd5(java.nio.file.Path path) {
        return com.awei.frt.core.utils.FileSignUtil.getFileMd5(path);
    }

    @Deprecated
    public static String getFileMd5(java.io.File file) {
        return com.awei.frt.core.utils.FileSignUtil.getFileMd5(file);
    }

    @Deprecated
    public static String getFileSha256(java.nio.file.Path path) {
        return com.awei.frt.core.utils.FileSignUtil.getFileSha256(path);
    }

    @Deprecated
    public static void clearCache() {
        com.awei.frt.core.utils.FileSignUtil.clearCache();
    }

    @Deprecated
    public static boolean hashEquals(String hash1, String hash2) {
        return com.awei.frt.core.utils.FileSignUtil.hashEquals(hash1, hash2);
    }
}

package com.awei.frt.core.uitls;

/**
 * @Author: mou_ren
 * @Date: 2026/1/18 17:06
 */
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 文件特征码工具类 - 获取文件唯一MD5/SHA256指纹
 * 你的builder包文件操作的完美配套工具
 */
public class FileSignUtil {
    // 定义哈希算法名称
    private static final String ALGORITHM_MD5 = "MD5";
    private static final String ALGORITHM_SHA256 = "SHA-256";
    // 读取文件的缓冲区大小，8KB，性能最优
    private static final int BUFFER_SIZE = 8 * 1024;

    /**
     * 核心方法：获取文件的【MD5唯一特征码】(32位16进制字符串)
     * @param path 文件Path对象（你业务中主要用这个）
     * @return 32位MD5特征码，文件不存在/异常返回null
     */
    public static String getFileMd5(Path path) {
        return getFileHash(path, ALGORITHM_MD5);
    }

    /**
     * 核心方法：获取文件的【SHA256唯一特征码】(64位16进制字符串)
     * @param path 文件Path对象
     * @return 64位SHA256特征码，文件不存在/异常返回null
     */
    public static String getFileSha256(Path path) {
        return getFileHash(path, ALGORITHM_SHA256);
    }

    /**
     * 兼容File对象的重载方法（适配旧代码）
     */
    public static String getFileMd5(File file) {
        return getFileMd5(file.toPath());
    }

    // 底层通用哈希计算逻辑
    private static String getFileHash(Path path, String algorithm) {
        // 校验文件是否存在+是否是文件
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            return null;
        }
        try (InputStream in = Files.newInputStream(path)) {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            byte[] buffer = new byte[BUFFER_SIZE];
            int len;
            // 流式读取文件，边读边计算，不占内存
            while ((len = in.read(buffer)) != -1) {
                digest.update(buffer, 0, len);
            }
            // 将哈希字节数组转成16进制字符串（核心转换）
            return bytesToHex(digest.digest());
        } catch (NoSuchAlgorithmException | IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    // 工具方法：字节数组转16进制字符串（固定写法，不用改）
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xFF & b);
            if (hex.length() == 1) {
                sb.append('0');
            }
            sb.append(hex);
        }
        return sb.toString();
    }

    // hash字串比较
    public static boolean hashEquals(String hash1, String hash2) {
        if (hash1 == null || hash2 == null) {
            return false;
        }
        return hash1.equals(hash2);
    }
}

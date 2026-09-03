package com.awei.frt.core.utils;

/**
 * @Author: mou_ren
 * @Date: 2026/1/18 17:06
 */
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.awei.frt.util.LoggerUtil;

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
    // 哈希结果 LRU 缓存（key=算法|路径|mtime|size，文件变化后 key 失效自动重算；上限 1024 条）
    private static final int CACHE_MAX_SIZE = 1024;
    private static final Map<String, String> HASH_CACHE = Collections.synchronizedMap(
            new LinkedHashMap<>(128, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                    return size() > CACHE_MAX_SIZE;
                }
            });

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

    // 底层通用哈希计算逻辑（带 LRU 缓存：key 含 mtime+size，文件变化自动失效）
    private static String getFileHash(Path path, String algorithm) {
        // 校验文件是否存在+是否是文件
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            return null;
        }
        String key = algorithm + "|" + fileCacheKey(path);
        String cached = HASH_CACHE.get(key);
        if (cached != null) {
            return cached;
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
            String hash = bytesToHex(digest.digest());
            HASH_CACHE.put(key, hash);
            return hash;
        } catch (NoSuchAlgorithmException | IOException e) {
            LoggerUtil.logException("计算文件特征码失败: " + path, e);
            return null;
        }
    }

    /**
     * 缓存 key：路径 + 最后修改时间 + 大小（内容变更但三者不变时视为未变，属可接受的近似）
     */
    private static String fileCacheKey(Path path) {
        try {
            return path.toString() + "|" + Files.getLastModifiedTime(path).toMillis() + "|" + Files.size(path);
        } catch (IOException e) {
            return path.toString();
        }
    }

    /**
     * 清空哈希缓存（测试用 / 文件批量变更后手动失效）
     */
    public static void clearCache() {
        HASH_CACHE.clear();
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

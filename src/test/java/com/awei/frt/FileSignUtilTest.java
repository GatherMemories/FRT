package com.awei.frt;

import com.awei.frt.core.utils.FileSignUtil;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 文件特征码测试：LRU 缓存命中 + 内容变更后缓存自动失效
 */
class FileSignUtilTest {

    @Test
    void md5CacheAndInvalidation() throws IOException {
        Path f = Files.createTempFile("md5-cache", ".txt");
        try {
            Files.writeString(f, "hello");
            String h1 = FileSignUtil.getFileMd5(f);
            String h2 = FileSignUtil.getFileMd5(f);
            assertNotNull(h1);
            assertEquals(h1, h2, "同文件重复计算应命中缓存（结果一致）");

            // 内容变化（mtime/size 变化）→ key 失效，重新计算
            Files.writeString(f, "hello world");
            String h3 = FileSignUtil.getFileMd5(f);
            assertNotEquals(h1, h3, "内容变化后应重新计算哈希");
        } finally {
            Files.deleteIfExists(f);
            FileSignUtil.clearCache();
        }
    }
}

package com.awei.frt.core.strategy;

import com.awei.frt.core.context.OperationContext;
import com.awei.frt.core.uitls.GlobMatcher;
import com.awei.frt.util.LoggerUtil;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 压缩包内文件内容匹配策略
 * 命中条件：zip/jar 内存在"条目名匹配 patterns 且文本内容包含 contentContains 关键词（任一）"的条目。
 * replacements 参数：
 *   contentContains=关键词1,关键词2   必填；任一关键词命中即匹配（英文逗号分隔）
 *   caseSensitive=false              可选；默认区分大小写
 * 读取条目文本限制 1MB（超出视为不匹配，防止大二进制包拖慢扫描）。
 */
public class ZipEntryContentStrategy extends ZipEntryBaseStrategy {

    private static final int MAX_ENTRY_TEXT = 1024 * 1024; // 1MB

    @Override
    public String getStrategyType() {
        return "ZipEntryContent";
    }

    @Override
    public String getDescription() {
        return "压缩包内文件内容匹配策略（读取 zip/jar 条目文本，contentContains 参数）";
    }

    @Override
    protected boolean matchesZipContent(Path zipPath, OperationContext context) {
        String contains = context.getRuleParam("contentContains");
        if (contains == null || contains.isBlank()) {
            LoggerUtil.logWarn("[ZipEntryContent] 未配置 contentContains 参数（replacements），策略不生效: " + zipPath.getFileName());
            return false;
        }
        List<String> keywords = Arrays.stream(contains.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        if (keywords.isEmpty()) {
            LoggerUtil.logWarn("[ZipEntryContent] contentContains 参数为空，策略不生效: " + zipPath.getFileName());
            return false;
        }
        boolean caseSensitive = !"false".equalsIgnoreCase(context.getRuleParam("caseSensitive"));
        List<String> patterns = context.getRuleInheritanceContext().getRuleChain().getPatterns();
        List<String> excludes = context.getRuleInheritanceContext().getRuleChain().getExcludePatterns();

        try (ZipFile zip = new ZipFile(zipPath.toFile())) {
            java.util.Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                String entryName = entry.getName();
                if (!GlobMatcher.matchesAny(entryName, patterns, caseSensitive)) {
                    continue;
                }
                if (excludes != null && !excludes.isEmpty()
                        && GlobMatcher.matchesAny(entryName, excludes, caseSensitive)) {
                    continue;
                }
                String text = readEntryText(zip, entry);
                if (text == null) {
                    continue;
                }
                String haystack = caseSensitive ? text : text.toLowerCase(Locale.ROOT);
                for (String kw : keywords) {
                    String needle = caseSensitive ? kw : kw.toLowerCase(Locale.ROOT);
                    if (haystack.contains(needle)) {
                        return true;
                    }
                }
            }
            return false;
        } catch (IOException e) {
            LoggerUtil.logWarn("读取压缩包失败（已跳过）: " + zipPath.getFileName() + " - " + e.getMessage());
            return false;
        }
    }

    /**
     * 读取条目文本内容（限制大小；非文本/超限返回 null）
     */
    private String readEntryText(ZipFile zip, ZipEntry entry) throws IOException {
        if (entry.getSize() > MAX_ENTRY_TEXT) {
            return null;
        }
        try (InputStream in = zip.getInputStream(entry)) {
            byte[] bytes = in.readAllBytes();
            if (bytes.length > MAX_ENTRY_TEXT) {
                return null;
            }
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }
}

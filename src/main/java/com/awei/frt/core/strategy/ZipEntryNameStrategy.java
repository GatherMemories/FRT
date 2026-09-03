package com.awei.frt.core.strategy;

import com.awei.frt.core.context.OperationContext;
import com.awei.frt.core.utils.GlobMatcher;
import com.awei.frt.util.LoggerUtil;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 压缩包内文件名匹配策略
 * 命中条件：zip/jar 内部存在任意条目名匹配 patterns（白名单）且不匹配 excludePatterns（黑名单）。
 * 例：patterns=["META-INF/*.toml"] → 只处理含 mods.toml 的压缩包；patterns=["*.class"] → 含 class 文件的包。
 * 支持通配符 * ?（统一 GlobMatcher）；空 patterns = 匹配所有压缩包。
 */
public class ZipEntryNameStrategy extends ZipEntryBaseStrategy {

    @Override
    public String getStrategyType() {
        return "ZipEntryName";
    }

    @Override
    public String getDescription() {
        return "压缩包内文件名匹配策略（zip/jar 内部条目名，支持通配符）";
    }

    @Override
    protected boolean matchesZipContent(Path zipPath, OperationContext context) {
        boolean caseSensitive = !"false".equalsIgnoreCase(context.getRuleParam("caseSensitive"));
        List<String> patterns = context.getRuleInheritanceContext().getRuleChain().getPatterns();
        List<String> excludes = context.getRuleInheritanceContext().getRuleChain().getExcludePatterns();

        try (ZipFile zip = new ZipFile(zipPath.toFile())) {
            java.util.Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                String entryName = entries.nextElement().getName();
                if (!GlobMatcher.matchesAny(entryName, patterns, caseSensitive)) {
                    continue;
                }
                if (excludes != null && !excludes.isEmpty()
                        && GlobMatcher.matchesAny(entryName, excludes, caseSensitive)) {
                    continue;
                }
                return true;
            }
            return false;
        } catch (IOException e) {
            LoggerUtil.logWarn("读取压缩包失败（已跳过）: " + zipPath.getFileName() + " - " + e.getMessage());
            return false;
        }
    }
}

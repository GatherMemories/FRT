package com.awei.frt.core.strategy;

import com.awei.frt.core.context.OperationContext;
import com.awei.frt.core.mod.ModInfo;
import com.awei.frt.core.mod.ModMetadataParser;
import com.awei.frt.core.node.FileNode;
import com.awei.frt.core.node.FolderNode;
import com.awei.frt.core.utils.FileSignUtil;
import com.awei.frt.core.utils.FileUtil;
import com.awei.frt.model.OperationRecord;
import com.awei.frt.util.LoggerUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipException;

/**
 * McMod 模组策略：按 modId 匹配 jar，处理目录级别的模组增/删/改。
 * 目录级策略：一个目录内多个 mod 各自分派，因此 stopOnHandled=false，
 * 三个钩子都会按序执行（各自内部按 modId 遍历分派）。
 */
public class McModStrategy extends AbstractOperationStrategy {

    // 模组元数据解析 LRU 缓存（key=jar路径|mtime|size；大 mods 目录重复解析耗时，文件变化自动失效）
    private static final int MOD_INFO_CACHE_MAX = 512;
    private static final Map<String, List<ModInfo>> MOD_INFO_CACHE = Collections.synchronizedMap(
            new LinkedHashMap<>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, List<ModInfo>> eldest) {
                    return size() > MOD_INFO_CACHE_MAX;
                }
            });

    @Override
    public String getStrategyType() {
        return "McMod";
    }

    @Override
    public String getDescription() {
        return "Minecraft 模组策略（按 modId 匹配 jar）";
    }

    @Override
    protected boolean accepts(FileNode node, OperationContext context) {
        return node.isDirectory();
    }

    @Override
    protected boolean stopOnHandled() {
        return false;
    }

    /**
     * 新增：目标层没有该 modId 时添加
     */
    @Override
    protected boolean doAdd(FileNode node, OperationContext context) {
        boolean any = false;
        Map<String, ModInfo> currentModInfoMap = getModInfo(node.getPath());
        Map<String, ModInfo> targetModInfoMap = getModInfo(context.getTargetPath(node.getRelativePath()));
        for (String modId : currentModInfoMap.keySet()) {
            ModInfo currentModInfo = currentModInfoMap.get(modId);
            if (targetModInfoMap.containsKey(modId)) {
                continue;
            }
            OperationRecord record = newRecord(context);
            Path targetFilePath = context.getTargetPath(node.getRelativePath())
                    .resolve(currentModInfo.getPath().getFileName()).normalize();
            boolean ok = FileUtil.addFile(currentModInfo.getPath(), targetFilePath, record, context.isDryRun());
            context.recordOperation(record);
            if (!context.isDryRun()) {
                LoggerUtil.logInfo("+ " + currentModInfo.getPath().getFileName() + " (" + currentModInfo.getVersion() + ") " + (ok ? "成功" : "失败"));
            }
            if (ok) {
                // 标记源 jar 文件节点已处理：链中后续文件级策略（如 FileSameName 空 patterns）
                // 不应再按文件名复制同一 jar（否则同一文件被新增+替换两次）
                markModFilesHandled(node, currentModInfo.getPath());
            }
            any = true;
        }
        if (any) {
            node.setHandled(true); // 目录级策略消费整个文件夹（链中后续策略跳过该目录）
        }
        return any;
    }

    /**
     * 替换：目标层已有同 modId 的 mod 时替换
     * 策略扩展参数：
     *   onlyIfVersionChanged=true 时，目标已有同版本模组则跳过替换（版本字符串比较）
     *   onlyIfContentSame=true 时，目标文件与源文件内容（MD5）相同则跳过替换（更准确，可识别同版本重打包的内容变化）
     */
    @Override
    protected boolean doReplace(FileNode node, OperationContext context) {
        boolean any = false;
        boolean onlyIfVersionChanged = Boolean.parseBoolean(context.getRuleParam("onlyIfVersionChanged"));
        boolean onlyIfContentSame = Boolean.parseBoolean(context.getRuleParam("onlyIfContentSame"));

        Map<String, ModInfo> currentModInfoMap = getModInfo(node.getPath());
        Map<String, ModInfo> targetModInfoMap = getModInfo(context.getTargetPath(node.getRelativePath()));
        Path entryTargetPath = context.getTargetPath(node.getRelativePath());

        for (String modId : currentModInfoMap.keySet()) {
            ModInfo currentModInfo = currentModInfoMap.get(modId);
            ModInfo targetModInfo = targetModInfoMap.get(modId);
            if (targetModInfo == null) {
                continue;
            }
            // 跳过 doAdd 刚新增的 mod（同一 UPDATE 操作里 doAdd 先执行：
            // 目标原本无该 mod → 新增落盘后，doReplace 再看到"目标已有"→ 先增后替重复写入）
            if (isModFileHandled(node, currentModInfo.getPath())) {
                continue;
            }

            Path sourceFilePath = currentModInfo.getPath();
            // 落盘目标 = 目标侧该 modId 的<b>原文件路径</b>（保持目标文件名不变），而不是按源文件名推导——
            // 同 modId 升级时源/目标文件名往往不同（版本号变化，如 A-1.0.jar → A-1.1.jar）：
            // ① 若按源文件名解析，目标侧通常不存在该文件，replaceFile 会因"目标路径不存在"而失败；
            // ② 即使成功，新文件旁旧 jar 残留 → 目标目录同 modId 双 jar（Minecraft 重复加载风险，审查 H2）。
            // 写回目标原路径 = 覆盖旧文件，目录内 modId 与文件一一对应；恢复时按记录的目标路径还原旧版。
            Path targetFilePath = targetModInfo.getPath();

            // 参数 onlyIfContentSame=true：源与目标文件 MD5 相同则跳过替换（内容一致无需更新）
            if (onlyIfContentSame && isFileContentSame(sourceFilePath, targetFilePath)) {
                LoggerUtil.logInfo("~ " + currentModInfo.getPath().getFileName() + " (" + currentModInfo.getVersion() + ") 内容相同(MD5)，跳过替换");
                // McMod 已判定该 mod 无需更新（消费），链中后续策略同样不应再按文件名处理
                markModFilesHandled(node, sourceFilePath);
                continue;
            }
            // 参数 onlyIfVersionChanged=true：目标已是相同版本则跳过替换
            if (onlyIfVersionChanged && currentModInfo.getVersion().equals(targetModInfo.getVersion())) {
                LoggerUtil.logInfo("~ " + currentModInfo.getPath().getFileName() + " (" + currentModInfo.getVersion() + ") 版本相同，跳过替换");
                markModFilesHandled(node, sourceFilePath);
                continue;
            }

            OperationRecord record = newRecord(context);
            boolean ok = FileUtil.replaceFile(sourceFilePath, targetFilePath, record, context.isDryRun());
            context.recordOperation(record);
            if (!context.isDryRun()) {
                LoggerUtil.logInfo("= " + currentModInfo.getPath().getFileName() + " (" + currentModInfo.getVersion() + ") " +
                        "--> " + targetModInfo.getPath().getFileName() + " (" + targetModInfo.getVersion() + ") " + (ok ? "成功" : "失败"));
            }
            if (ok) {
                markModFilesHandled(node, sourceFilePath);
            }
            any = true;
        }
        if (any) {
            node.setHandled(true);
        }
        return any;
    }

    /**
     * 删除：目标层存在该 modId 时删除目标侧实际文件
     */
    @Override
    protected boolean doDelete(FileNode node, OperationContext context) {
        boolean any = false;
        Map<String, ModInfo> currentModInfoMap = getModInfo(node.getPath());
        Map<String, ModInfo> targetModInfoMap = getModInfo(context.getTargetPath(node.getRelativePath()));
        for (String modId : currentModInfoMap.keySet()) {
            ModInfo currentModInfo = currentModInfoMap.get(modId);
            ModInfo targetModInfo = targetModInfoMap.get(modId);
            if (targetModInfo == null) {
                // 目标无对应 mod：无需删除（可能已被删过/从未同步），提示原因避免用户误以为没生效
                if (!context.isDryRun()) {
                    LoggerUtil.logInfo("~ " + currentModInfo.getPath().getFileName() + " (" + currentModInfo.getVersion()
                            + ") 目标无对应 mod，无需删除");
                }
                continue;
            }
            OperationRecord record = newRecord(context);
            Path deleteFilePath = targetModInfo.getPath();
            boolean ok = FileUtil.deleteFile(deleteFilePath, record, context.isDryRun());
            context.recordOperation(record);
            if (!context.isDryRun()) {
                LoggerUtil.logInfo("- " + deleteFilePath.getFileName() + " (" + targetModInfo.getVersion() + ") " + (ok ? "成功" : "失败"));
            }
            if (ok) {
                // 删除目标侧 mod 后，update 侧同名 jar 也不应被文件级策略重新复制回去
                markModFilesHandled(node, currentModInfo.getPath());
            }
            any = true;
        }
        if (any) {
            node.setHandled(true);
        }
        return any;
    }

    /**
     * 判断源与目标文件内容是否完全相同（MD5 比较）
     * 任一文件不存在或计算失败时返回 false（保守：不确定就执行替换）
     */
    private boolean isFileContentSame(Path sourcePath, Path targetPath) {
        String sourceMd5 = FileSignUtil.getFileMd5(sourcePath);
        String targetMd5 = FileSignUtil.getFileMd5(targetPath);
        return sourceMd5 != null && sourceMd5.equals(targetMd5);
    }

    /**
     * 判断目录下与 mod jar 同名的文件节点是否已被标记为已处理（doAdd 刚新增时标记）。
     * 用于 doReplace 跳过"同一 UPDATE 操作里 doAdd 刚新增"的 mod，避免先增后替重复写入。
     */
    private boolean isModFileHandled(FileNode node, Path modJarPath) {
        if (!(node instanceof FolderNode) || modJarPath == null) {
            return false;
        }
        String fileName = modJarPath.getFileName().toString();
        for (FileNode child : ((FolderNode) node).getChildren()) {
            if (!child.isDirectory() && child.getName().equals(fileName)) {
                return child.isHandled();
            }
        }
        return false;
    }

    /**
     * 标记目录下与 mod jar 同名的文件节点为已处理（handled）。
     * 目的：McMod 是目录级策略，已按 modId 对该 jar 执行了增/删/改；
     * 若不标记，链中后续文件级策略（如 FileSameName 空 patterns 匹配所有）
     * 会在文件级再次按文件名处理同一 jar（预览显示同一文件两次操作，
     * 真实执行变为"新增+替换"的重复写入）。无效 mod 的 jar（如 app.jar）
     * 不被 McMod 处理，不会被标记，仍由文件级策略正常处理。
     */
    private void markModFilesHandled(FileNode node, Path modJarPath) {
        if (!(node instanceof FolderNode) || modJarPath == null) {
            return;
        }
        String fileName = modJarPath.getFileName().toString();
        for (FileNode child : ((FolderNode) node).getChildren()) {
            if (!child.isDirectory() && child.getName().equals(fileName)) {
                child.setHandled(true);
                return;
            }
        }
    }

    // 获取文件夹里的所有mod信息
    private Map<String, ModInfo> getModInfo(Path entryPath) {
        Map<String, ModInfo> modInfoMap = new HashMap<>();
        if (entryPath == null || !Files.isDirectory(entryPath)) {
            // 目录不存在（如首次更新/目标被清理）：视为无 mod，不报 ERROR 噪音
            return modInfoMap;
        }
        try (Stream<Path> fileStream = Files.list(entryPath)) {
            fileStream
                    .filter(file -> file.toString().endsWith(".jar"))
                    .forEach(file -> {
                // 解析缓存 key：jar 未变化（mtime+size 相同）时复用上次解析结果，
                // 避免大 mods 目录反复解压。在 try 之前计算一次——正常/异常路径共用
                // 同一 key（异常兜底缓存空结果，文件本会话内变化前不再重复解析/告警）
                String cacheKey = cacheKeyOf(file);
                try {
                    List<ModInfo> modInfos = MOD_INFO_CACHE.get(cacheKey);
                    if (modInfos == null) {
                        // 自研解析器：自动检测平台（NeoForge/Forge/Fabric/Quilt/旧版Forge），
                        // 版本占位符自动兜底（MANIFEST.MF -> 文件名）
                        modInfos = ModMetadataParser.parseJar(file);
                        MOD_INFO_CACHE.put(cacheKey, modInfos);
                        if (modInfos.isEmpty()) {
                            // 首次解析失败/不支持：警告一次（带完整路径，区分 update/target 同名 jar），
                            // 结果已入缓存——同一 jar 在 doAdd/doReplace/doDelete 等多次钩子扫描时
                            // 只警告一次，后续命中缓存空结果静默跳过（不刷屏）
                            LoggerUtil.logWarn("未找到支持的模组元数据（已跳过）: " + file);
                            return;
                        }
                    } else if (modInfos.isEmpty()) {
                        // 缓存命中的空结果（同文件本会话已解析过）：
                        // 静默跳过，避免 doAdd/doReplace 多个钩子重复扫同一 jar 时反复警告
                        return;
                    }
                    for (ModInfo modInfo : modInfos) {
                        modInfoMap.put(modInfo.getId(), modInfo);
                    }
                }
                catch (Exception e) {
                    // 兜底：单个 jar 解析失败只跳过该 jar，不影响整个更新流程；
                    // 失败也入缓存（空结果），同一 jar 重复扫描不再反复解析/警告。
                    // 仅捕获 Exception——OOM/ThreadDeath 等 Error 让上层处理，不吞
                    MOD_INFO_CACHE.putIfAbsent(cacheKey, List.of());
                    if (!(e instanceof ZipException)) {
                        LoggerUtil.logWarn("读取 mod 文件失败（已跳过）: " + file + " - " + e.getMessage());
                    }
                }
            });
        } catch (IOException e) {
            LoggerUtil.logException("读取文件夹失败: " + entryPath, e);
        }
        return modInfoMap;
    }

    /**
     * 构造 mod 解析缓存 key（path|mtime|size）：文件系统读取失败时退化为
     * 仅路径前缀，保证后续调用仍命中同一 key（不会因 stat 抖动无限重试）。
     */
    private static String cacheKeyOf(Path file) {
        try {
            return file.toString() + "|" + Files.getLastModifiedTime(file).toMillis() + "|" + Files.size(file);
        } catch (IOException e) {
            return "unreadable|" + file.toString();
        }
    }
}

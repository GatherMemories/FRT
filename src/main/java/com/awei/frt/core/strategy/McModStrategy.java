package com.awei.frt.core.strategy;

import com.awei.frt.core.context.OperationContext;
import com.awei.frt.core.mod.ModInfo;
import com.awei.frt.core.mod.ModMetadataParser;
import com.awei.frt.core.node.FileNode;
import com.awei.frt.core.uitls.FileSignUtil;
import com.awei.frt.core.uitls.FileUtil;
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

            Path sourceFilePath = currentModInfo.getPath();
            Path targetFilePath = entryTargetPath.resolve(currentModInfo.getPath().getFileName()).normalize();

            // 参数 onlyIfContentSame=true：源与目标文件 MD5 相同则跳过替换（内容一致无需更新）
            if (onlyIfContentSame && isFileContentSame(sourceFilePath, targetFilePath)) {
                LoggerUtil.logInfo("~ " + currentModInfo.getPath().getFileName() + " (" + currentModInfo.getVersion() + ") 内容相同(MD5)，跳过替换");
                continue;
            }
            // 参数 onlyIfVersionChanged=true：目标已是相同版本则跳过替换
            if (onlyIfVersionChanged && currentModInfo.getVersion().equals(targetModInfo.getVersion())) {
                LoggerUtil.logInfo("~ " + currentModInfo.getPath().getFileName() + " (" + currentModInfo.getVersion() + ") 版本相同，跳过替换");
                continue;
            }

            OperationRecord record = newRecord(context);
            boolean ok = FileUtil.replaceFile(sourceFilePath, targetFilePath, record, context.isDryRun());
            context.recordOperation(record);
            if (!context.isDryRun()) {
                LoggerUtil.logInfo("= " + currentModInfo.getPath().getFileName() + " (" + currentModInfo.getVersion() + ") " +
                        "--> " + targetModInfo.getPath().getFileName() + " (" + targetModInfo.getVersion() + ") " + (ok ? "成功" : "失败"));
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
            ModInfo targetModInfo = targetModInfoMap.get(modId);
            if (targetModInfo == null) {
                continue;
            }
            OperationRecord record = newRecord(context);
            Path deleteFilePath = targetModInfo.getPath();
            boolean ok = FileUtil.deleteFile(deleteFilePath, record, context.isDryRun());
            context.recordOperation(record);
            if (!context.isDryRun()) {
                LoggerUtil.logInfo("- " + deleteFilePath.getFileName() + " (" + targetModInfo.getVersion() + ") " + (ok ? "成功" : "失败"));
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

    // 获取文件夹里的所有mod信息
    private Map<String, ModInfo> getModInfo(Path entryPath) {
        Map<String, ModInfo> modInfoMap = new HashMap<>();
        try (Stream<Path> fileStream = Files.list(entryPath)) {
            fileStream
                    .filter(file -> file.toString().endsWith(".jar"))
                    .forEach(file -> {
                try {
                    // 解析缓存：jar 未变化（mtime+size 相同）时复用上次解析结果，避免大 mods 目录反复解压
                    String cacheKey = file.toString() + "|" + Files.getLastModifiedTime(file).toMillis() + "|" + Files.size(file);
                    List<ModInfo> modInfos = MOD_INFO_CACHE.get(cacheKey);
                    if (modInfos == null) {
                        // 自研解析器：自动检测平台（NeoForge/Forge/Fabric/Quilt/旧版Forge），
                        // 版本占位符自动兜底（MANIFEST.MF -> 文件名）
                        modInfos = ModMetadataParser.parseJar(file);
                        MOD_INFO_CACHE.put(cacheKey, modInfos);
                    }
                    if (modInfos.isEmpty()) {
                        // 带完整路径：update 与 target 目录可能各有一个同名 jar（如 app.jar），
                        // 只显示文件名会误以为"同一个文件被扫了两次"
                        LoggerUtil.logWarn("未找到支持的模组元数据（已跳过）: " + file);
                        return;
                    }
                    for (ModInfo modInfo : modInfos) {
                        modInfoMap.put(modInfo.getId(), modInfo);
                    }
                }
                catch (Throwable e) {
                    // 兜底：单个 jar 解析失败只跳过该 jar，不影响整个更新流程
                    if(!(e instanceof ZipException)){
                        LoggerUtil.logWarn("读取 mod 文件失败（已跳过）: " + file + " - " + e);
                    }
                }
            });
        } catch (IOException e) {
            LoggerUtil.logException("读取文件夹失败: " + entryPath, e);
        }
        return modInfoMap;
    }
}

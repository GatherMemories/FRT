package com.awei.frt.core.strategy;

import com.awei.frt.core.context.OperationContext;
import com.awei.frt.core.mod.ModInfo;
import com.awei.frt.core.mod.ModMetadataParser;
import com.awei.frt.core.node.FileNode;
import com.awei.frt.core.uitls.FileSignUtil;
import com.awei.frt.core.uitls.FileUtil;
import com.awei.frt.model.OperationRecord;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipException;

/**
 * @Author: mou_ren
 * @Date: 2026/1/10 20:04
 */
public class McModStrategy implements OperationStrategy{
    // 一层一层目录里的文件处理
    @Override
    public void execute(FileNode node, OperationContext context, String[] operationType) {
        if (node == null || context == null) {
            return;
        }
        // 文件直接返回
        if (!node.isDirectory()) {
            return;
        }
        String strategyType = context.getRuleInheritanceContext().getRuleChain().getStrategyType();


        // 获取对应层目标文件夹
        Path entryTargetPath = context.getTargetPath(node.getRelativePath());

        // 判断操作类型
        boolean addType = Arrays.stream(operationType).anyMatch(type -> type.equals(OperationContext.OPERATION_ADD));
        boolean replaceType = Arrays.stream(operationType).anyMatch(type -> type.equals(OperationContext.OPERATION_REPLACE));
        boolean deleteType = Arrays.stream(operationType).anyMatch(type -> type.equals(OperationContext.OPERATION_DELETE));
        // 获取当前层文件夹下所有文件
        Map<String, ModInfo> currentModInfoMap = getModInfo(node.getPath());
        // 获取目标层文件夹下所有文件
        Map<String, ModInfo> targetModInfoMap = getModInfo(entryTargetPath);

        // 处理逻辑（仅记录操作，不进行 增、删、改，之后统一操作，方便 “补偿式事务”）
        // 策略扩展参数：onlyIfVersionChanged=true 时，目标已有同版本模组则跳过替换
        boolean onlyIfVersionChanged = Boolean.parseBoolean(context.getRuleParam("onlyIfVersionChanged"));
        for (String modId : currentModInfoMap.keySet()) {
            ModInfo currentModInfo = currentModInfoMap.get(modId);
            ModInfo targetModInfo = targetModInfoMap.get(modId);

            // 源文件和目标文件绝对路径
            Path sourceFilePath = currentModInfo.getPath();
            Path targetFilePath = entryTargetPath.resolve(currentModInfo.getPath().getFileName()).normalize();

            // 创建操作记录对象，设置基础值
            OperationRecord operationRecord = new OperationRecord();
            operationRecord.setStrategyType(strategyType);


            // 如果目标层没有该mod，则新增
            if (addType && targetModInfo == null) {

                boolean b = FileUtil.addFile(sourceFilePath, targetFilePath, operationRecord);
                context.recordOperation(operationRecord);
                System.out.println("+ " + currentModInfo.getPath().getFileName() + " (" + currentModInfo.getVersion() + ") " + (b ? "成功" : "失败"));
                continue;
            }
            // 如果目标层有该mod，则替换（目标层不存在该mod时跳过，避免NPE）
            if (replaceType && targetModInfo != null && currentModInfo.getId().equals(targetModInfo.getId())) {
                // 参数 onlyIfVersionChanged=true：目标已是相同版本则跳过替换
                if (onlyIfVersionChanged && currentModInfo.getVersion().equals(targetModInfo.getVersion())) {
                    System.out.println("~ " + currentModInfo.getPath().getFileName() + " (" + currentModInfo.getVersion() + ") 版本相同，跳过替换");
                    continue;
                }

                boolean b = FileUtil.replaceFile(sourceFilePath, targetFilePath, operationRecord);
                context.recordOperation(operationRecord);
                System.out.println("= " + currentModInfo.getPath().getFileName() + " (" + currentModInfo.getVersion() + ") " +
                        "--> " + targetModInfo.getPath().getFileName() + " (" + targetModInfo.getVersion() + ") " + (b ? "成功" : "失败"));
                continue;
            }
            // 删除操作：目标层存在该mod才删除（删除目标侧实际文件，不存在则无需处理）
            if (deleteType && targetModInfo != null) {
                Path deleteFilePath = targetModInfo.getPath();
                boolean b = FileUtil.deleteFile(deleteFilePath, operationRecord);
                context.recordOperation(operationRecord);
                System.out.println("- " + deleteFilePath.getFileName() + " (" + targetModInfo.getVersion() + ") " + (b ? "成功" : "失败"));
                continue;
            }
        }
    }


    // 获取文件夹里的所有mod信息
    private Map<String, ModInfo> getModInfo(Path entryPath) {
        Map<String, ModInfo> modInfoMap = new HashMap<>();
        try (Stream<Path> fileStream = Files.list(entryPath)) {
            fileStream
                    .filter(file -> file.toString().endsWith(".jar"))
                    .forEach(file -> {
                try {
                    // 自研解析器：自动检测平台（NeoForge/Forge/Fabric/Quilt/旧版Forge），
                    // 版本占位符自动兜底（MANIFEST.MF -> 文件名）
                    List<ModInfo> modInfos = ModMetadataParser.parseJar(file);
                    if (modInfos.isEmpty()) {
                        System.out.println("未找到支持的模组元数据（已跳过）: " + file.getFileName());
                        return;
                    }
                    for (ModInfo modInfo : modInfos) {
                        modInfoMap.put(modInfo.getId(), modInfo);
                    }
                }
                catch (Throwable e) {
                    // 兜底：单个 jar 解析失败只跳过该 jar，不影响整个更新流程
                    if(!(e instanceof ZipException)){
                        System.err.println("读取 mod 文件失败（已跳过）: " + file.getFileName() + " - " + e);
                    }
                }
            });
        } catch (IOException e) {
            System.err.println("读取文件夹失败：" + entryPath);
            e.printStackTrace();
        }
        return modInfoMap;
    }

}

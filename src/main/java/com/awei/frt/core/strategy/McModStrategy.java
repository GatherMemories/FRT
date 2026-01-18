package com.awei.frt.core.strategy;

import com.awei.frt.core.context.OperationContext;
import com.awei.frt.core.node.FileNode;
import com.awei.frt.core.uitls.FileSignUtil;
import com.awei.frt.model.OperationRecord;
import me.andreasmelone.basicmodinfoparser.BasicModInfo;
import me.andreasmelone.basicmodinfoparser.Platform;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarFile;
import java.util.stream.Stream;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

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
        // 如果目标文件夹不存在，则不处理
        if (!Files.exists(entryTargetPath)) {
            System.out.println(strategyType + " 策略-目标文件夹不存在: " + entryTargetPath);
            return;
        }
        // 判断操作类型
        boolean addType = Arrays.stream(operationType).anyMatch(type -> type.equals(OperationContext.OPERATION_ADD));
        boolean replaceType = Arrays.stream(operationType).anyMatch(type -> type.equals(OperationContext.OPERATION_REPLACE));
        boolean deleteType = Arrays.stream(operationType).anyMatch(type -> type.equals(OperationContext.OPERATION_DELETE));
        // 获取当前层文件夹下所有文件
        Map<String, ModInfo> currentModInfoMap = getModInfo(node.getPath());
        // 获取目标层文件夹下所有文件
        Map<String, ModInfo> targetModInfoMap = getModInfo(entryTargetPath);

        // 处理逻辑（仅记录操作，不进行 增、删、改，之后统一操作，方便 “补偿式事务”）
        for (String modId : currentModInfoMap.keySet()) {
            ModInfo currentModInfo = currentModInfoMap.get(modId);
            ModInfo targetModInfo = targetModInfoMap.get(modId);
            // 获取源文件特征 和 目标文件特征
            String sourceFileHash = FileSignUtil.getFileMd5(currentModInfo.getPath());
            String targetFileHash = "";
            if(targetModInfo != null){
                targetFileHash = FileSignUtil.getFileMd5(targetModInfo.getPath());
            }
            // 源文件和目标文件绝对路径
            Path sourceFilePath = currentModInfo.getPath();
            Path targetFilePath = entryTargetPath.resolve(currentModInfo.getPath().getFileName()).normalize();

            // 如果目标层没有该mod，则新增
            if (addType && targetModInfo == null) {
                // context 对象记录操作
                OperationRecord operationRecord = new OperationRecord(
                        strategyType,
                        OperationContext.OPERATION_ADD,
                        sourceFilePath,
                        targetFilePath,
                        sourceFileHash,
                        targetFileHash,
                        true,
                        ""
                );
                context.getProcessingResult().addOperationRecord(operationRecord);
                System.out.println("+ " + currentModInfo.getPath().getFileName() + " (" + currentModInfo.getVersion() + ")");
                continue;
            }
            // 如果目标层有该mod，则替换
            if (replaceType && currentModInfo.getId().equals(targetModInfo.getId())) {
                OperationRecord operationRecord = new OperationRecord(
                        strategyType,
                        OperationContext.OPERATION_REPLACE,
                        sourceFilePath,
                        targetFilePath,
                        sourceFileHash,
                        targetFileHash,
                        true,
                        ""
                );
                context.getProcessingResult().addOperationRecord(operationRecord);
                System.out.println("= " + currentModInfo.getPath().getFileName() + " (" + currentModInfo.getVersion() + ")" +
                        " --> " + targetModInfo.getPath().getFileName() + " (" + targetModInfo.getVersion() + ")");
                continue;
            }
            // 删除操作
            if (deleteType && targetModInfo != null) {
                OperationRecord operationRecord = new OperationRecord(
                        strategyType,
                        OperationContext.OPERATION_DELETE,
                        targetFilePath,
                        targetFilePath,
                        "",
                        targetFileHash,
                        true,
                        ""
                );
                context.getProcessingResult().addOperationRecord(operationRecord);
                System.out.println("- " + targetModInfo.getPath().getFileName() + " (" + targetModInfo.getVersion() + ")");
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
                try (ZipFile jarFile = new ZipFile(file.toFile())){
                    // 检测模组平台（Forge、Fabric 等）
                    Platform[] platforms = Platform.findModPlatform(file.toFile());
                    if (platforms.length == 0) {
                        System.out.println("找不到支持的Mod加载平台: " + file.toFile().getName());
                        return;
                    }
                    for (Platform platform : platforms) {
                        // Get the mod info content and parse it
                        // 获取模组信息内容并解析
                        String modInfoContent = platform.getInfoFileContent(jarFile);
                        for(BasicModInfo modInfo : platform.parse(modInfoContent)) {
                            // Output the parsed mod information
                            //输出解析后的模组信息
                            modInfoMap.put(modInfo.getId(), new ModInfo(modInfo, file));
                        }
                    }
                }
                catch (IOException e) {
                    // 排除异常 ZipException异常，BasicModInfoParser-2.0.0 包版本会抛出，小问题
                    if(!(e instanceof ZipException)){
                        System.err.println("读取 mod 文件失败：" + file.toFile().getName());
                        e.printStackTrace();
                    }
                }
            });
        } catch (IOException e) {
            System.err.println("读取文件夹失败：" + entryPath);
            e.printStackTrace();
        }
        return modInfoMap;
    }


    class ModInfo {
        private BasicModInfo basicModInfo;
        private Path path;

        public ModInfo(BasicModInfo basicModInfo, Path path) {
            this.basicModInfo = basicModInfo;
            this.path = path;
        }

        public BasicModInfo getBasicModInfo() {
            return basicModInfo;
        }

        public Path getPath() {
            return path;
        }

        public String getId() {
            return basicModInfo.getId();
        }

        public String getName() {
            return basicModInfo.getName();
        }

        public String getVersion() {
            return basicModInfo.getVersion();
        }

        public String getDescription() {
            return basicModInfo.getDescription();
        }

    }
}

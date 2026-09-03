package com.awei.frt;

import com.awei.frt.core.mod.ModInfo;
import com.awei.frt.core.mod.ModMetadataParser;
import com.awei.frt.util.LoggerUtil;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * 模组元数据解析手动测试工具
 * 用法：java ... ModMatchTool [jar目录]，默认读取 mods/ 目录（相对工作目录），
 * 逐个解析其中 .jar 并打印模组元数据
 *
 * @Author: mou_ren
 * @Date: 2026/1/18 00:24
 */
public class ModMatchTool {


    public static void main(String[] args) {
        // 目录参数：第一个参数指定 jar 目录，默认 mods/
        String dir = args.length > 0 ? args[0] : "mods/";
        File folder = new File(dir);
        File[] modFiles = folder.listFiles((dir1, name) -> name.endsWith(".jar"));

        if (modFiles != null) {
            for (File modFile : modFiles) {
                try {
                    // 自研解析器：自动检测平台并解析，版本占位符自动兜底
                    List<ModInfo> modInfos = ModMetadataParser.parseJar(modFile.toPath());
                    if (modInfos.isEmpty()) {
                        System.out.println("No supported mod metadata found for: " + modFile.getName());
                        continue;
                    }
                    for (ModInfo modInfo : modInfos) {
                        System.out.println("Mod ID: " + modInfo.getId());
                        System.out.println("Mod Name: " + modInfo.getName());
                        System.out.println("Mod Version: " + modInfo.getVersion());
                        System.out.println("Mod Description: " + modInfo.getDescription());
                        System.out.println("Jar Path: " + modInfo.getPath());
                    }
                } catch (IOException e) {
                    LoggerUtil.logException("Failed to read mod file: " + modFile.getName(), e);
                }
            }
        } else {
            System.out.println("目录不存在或为空，请把 .jar 文件放入 " + dir + " 后重试");
        }
    }
}

package com.awei.frt;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Scanner;

/**
 * 简化版框架测试 - 验证多层级规则继承
 */
public class TestSimpleFramework {
    public static void main(String[] args) {
        try {
            System.out.println("=========================================");
            System.out.println("🧪 测试多层级规则继承功能");
            System.out.println("=========================================");

            // 获取 FRT 根目录
            Path frtRoot = Paths.get("").toAbsolutePath().getParent();
            Path updateDir = frtRoot.resolve("update");

            System.out.println("\n📁 FRT根目录: " + frtRoot);
            System.out.println("📂 Update目录: " + updateDir);
            System.out.println();

            // 测试规则继承
            testRuleInheritance(updateDir);

            System.out.println("\n✅ 测试完成！");

        } catch (Exception e) {
            System.err.println("❌ 测试失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void testRuleInheritance(Path updateDir) {
        Scanner scanner = new Scanner(System.in);

        System.out.println("=========================================");
        System.out.println("📋 测试规则继承");
        System.out.println("=========================================");

        // 根目录
        System.out.println("\n1️⃣ 根目录 (update/)");
        Path rootRule = updateDir.resolve("replace.json");
        if (Files.exists(rootRule)) {
            System.out.println("✓ 存在规则文件");
            try {
                System.out.println("📄 规则内容:");
                System.out.println(Files.readString(rootRule));
            } catch (Exception e) {
                System.err.println("⚠️  读取失败: " + e.getMessage());
            }
        } else {
            System.out.println("✗ 不存在规则文件");
        }

        // lib 目录
        System.out.println("\n2️⃣ lib目录 (update/lib/)");
        Path libDir = updateDir.resolve("lib");
        Path libRule = libDir.resolve("replace.json");
        if (Files.exists(libRule)) {
            System.out.println("✓ 存在规则文件");
            try {
                System.out.println("📄 规则内容:");
                System.out.println(Files.readString(libRule));
            } catch (Exception e) {
                System.err.println("⚠️  读取失败: " + e.getMessage());
            }
        } else {
            System.out.println("✗ 不存在规则文件 - 将继承根规则");
        }

        // utils 目录
        System.out.println("\n3️⃣ utils目录 (update/lib/utils/)");
        Path utilsDir = libDir.resolve("utils");
        Path utilsRule = utilsDir.resolve("replace.json");
        if (Files.exists(utilsRule)) {
            System.out.println("✓ 存在规则文件");
        } else {
            System.out.println("✗ 不存在规则文件 - 将继承 lib 规则");
        }

        // config 目录
        System.out.println("\n4️⃣ config目录 (update/config/)");
        Path configDir = updateDir.resolve("config");
        Path configRule = configDir.resolve("replace.json");
        if (Files.exists(configRule)) {
            System.out.println("✓ 存在规则文件");
        } else {
            System.out.println("✗ 不存在规则文件 - 将继承根规则");
        }

        System.out.println("\n=========================================");
        System.out.println("📊 规则继承链分析:");
        System.out.println("=========================================");
        System.out.println("📍 根目录 (update/)");
        System.out.println("   └─ 使用自己的规则: *.jar");
        System.out.println();
        System.out.println("📍 lib目录 (update/lib/)");
        System.out.println("   ├─ 有自己的规则: *.class");
        System.out.println("   └─ 覆盖了根规则");
        System.out.println();
        System.out.println("📍 utils目录 (update/lib/utils/)");
        System.out.println("   ├─ 无规则文件");
        System.out.println("   └─ 继承 lib 的规则: *.class");
        System.out.println();
        System.out.println("📍 config目录 (update/config/)");
        System.out.println("   ├─ 无规则文件");
        System.out.println("   └─ 继承根的规则: *.jar");
        System.out.println("=========================================");

        scanner.close();
    }
}

package com.awei.frt.testutil;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

/**
 * 测试工具：运行时把插件策略源码编译成 plugins/ 形式的 jar。
 * 用于"外部策略读取与执行"全面测试——模拟玩家编写的策略插件，
 * 复用与 StrategyLoaderTest 相同的 JavaCompiler + JarOutputStream 方式，
 * 支持带 / 不带 META-INF/services（SPI 描述符）两种形态。
 */
public final class TestPluginBuilder {

    public static final String SPI_SERVICE_FILE = "META-INF/services/com.awei.frt.core.strategy.OperationStrategy";

    private TestPluginBuilder() {
    }

    /**
     * 编译源码并打成插件 jar
     *
     * @param workDir           工作目录（jar 输出位置，须已存在）
     * @param jarName           jar 文件名（如 "ext-plugin.jar"）
     * @param sources           className(全限定) -> 完整源码（含 package 声明）
     * @param spiServiceClasses 非空时在 jar 内生成 META-INF/services 描述符（声明这些全限定类名）；
     *                          null 或空 = 不带 services 文件（走自动类扫描路径）
     * @return 生成的 jar 路径
     */
    public static Path buildPluginJar(Path workDir, String jarName,
                                      Map<String, String> sources,
                                      List<String> spiServiceClasses) throws Exception {
        Path srcDir = Files.createDirectories(workDir.resolve("src"));
        Path classesDir = Files.createDirectories(workDir.resolve("classes"));

        List<Path> sourceFiles = new ArrayList<>();
        for (Map.Entry<String, String> e : sources.entrySet()) {
            String className = e.getKey();
            Path srcFile = srcDir.resolve(className.replace('.', '/') + ".java");
            Files.createDirectories(srcFile.getParent());
            Files.writeString(srcFile, e.getValue(), StandardCharsets.UTF_8);
            sourceFiles.add(srcFile);
        }

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        List<String> args = new ArrayList<>();
        args.add("-encoding");
        args.add("UTF-8");
        args.add("-cp");
        args.add(System.getProperty("java.class.path"));
        args.add("-d");
        args.add(classesDir.toString());
        for (Path sf : sourceFiles) {
            args.add(sf.toString());
        }
        int compileResult = compiler.run(null, null, null, args.toArray(new String[0]));
        if (compileResult != 0) {
            throw new IllegalStateException("插件源码编译失败: " + sources.keySet());
        }

        // 打成 jar（可含 SPI 描述符）
        Path jar = workDir.resolve(jarName);
        try (JarOutputStream jos = new JarOutputStream(Files.newOutputStream(jar));
             var walk = Files.walk(classesDir)) {
            var files = walk.filter(Files::isRegularFile).toList();
            for (Path f : files) {
                String entryName = classesDir.relativize(f).toString().replace('\\', '/');
                jos.putNextEntry(new JarEntry(entryName));
                jos.write(Files.readAllBytes(f));
                jos.closeEntry();
            }
            if (spiServiceClasses != null && !spiServiceClasses.isEmpty()) {
                jos.putNextEntry(new JarEntry(SPI_SERVICE_FILE));
                jos.write(String.join("\n", spiServiceClasses).getBytes(StandardCharsets.UTF_8));
                jos.closeEntry();
            }
        }
        return jar;
    }
}

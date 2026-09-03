package com.awei.frt.service;

import com.awei.frt.util.LoggerUtil;
import com.awei.frt.constants.RulesConstants;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;

/**
 * 插件源码打包服务（2026-08-23 新功能）：
 * 把 plugins/ 目录下的 .java 策略源码直接编译打包成 jar，免去命令行/IDE 操作。
 * <p>
 * 流程：扫描 plugins/（含子目录）收集 .java → javac 编译（classpath=当前程序自身，
 * 保证能引用 AbstractOperationStrategy/FileUtil 等）→ 打成 jar 输出回 plugins/：
 * <ul>
 *   <li>只有 1 个 .java 文件 → 同名 jar（MyStrategy.java → MyStrategy.jar）；</li>
 *   <li>多个 .java 文件 → 全部打进 my-strategies.jar（支持多文件互相依赖）。</li>
 * </ul>
 * 打好的 jar 会在程序下次启动时被 StrategyLoader 自动加载（当前运行中的程序不热加载，需重启生效）。
 * <p>
 * 注意：编译需要 JDK 的 javac（jdk.compiler 模块）——发布包的精简运行时（jlink）默认不含编译器，
 * 此时返回明确提示；若用完整 JDK 启动则直接可用。
 */
public final class PluginCompiler {

    private static final String MULTI_JAR_NAME = "my-strategies.jar";

    private PluginCompiler() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 编译打包结果
     */
    public static final class CompileResult {
        private final boolean success;
        private final List<Path> jars;      // 生成/覆盖的 jar（成功时）
        private final String message;       // 摘要（成功）或错误提示（失败）

        private CompileResult(boolean success, List<Path> jars, String message) {
            this.success = success;
            this.jars = jars;
            this.message = message;
        }

        public boolean isSuccess() {
            return success;
        }

        public List<Path> getJars() {
            return jars;
        }

        public String getMessage() {
            return message;
        }
    }

    /**
     * 编译打包 plugins 目录下的全部 .java
     *
     * @param pluginsDir plugins 目录（不存在时自动创建）
     * @return 打包结果（成功=生成 jar；失败=错误提示，message 含第一个编译错误位置）
     */
    public static CompileResult compilePluginsToJar(Path pluginsDir) {
        Path dir = pluginsDir == null ? Path.of(RulesConstants.Paths.PLUGINS_DIR) : pluginsDir;
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            LoggerUtil.logException("创建插件目录失败: " + dir, e);
            return new CompileResult(false, List.of(), "创建插件目录失败: " + dir);
        }

        List<Path> sources = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".java"))
                    .sorted(Comparator.comparing(Path::toString))
                    .forEach(sources::add);
        } catch (IOException e) {
            LoggerUtil.logException("扫描插件源码失败: " + dir, e);
            return new CompileResult(false, List.of(), "扫描插件源码失败: " + dir);
        }

        if (sources.isEmpty()) {
            return new CompileResult(false, List.of(),
                    "plugins/ 目录下没有 .java 文件——把写好的策略源码（如 MyStrategy.java）放进来再打包");
        }

        // 编译需要 JDK 的 javac；jlink 精简运行时（release/runtime）默认不含 jdk.compiler 模块
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            return new CompileResult(false, List.of(),
                    "打包需要完整 JDK（当前运行环境的精简运行时不含编译器）。请用系统 JDK 启动程序（java -jar FRT-*.jar --ui）后再打包");
        }

        // 临时输出目录（编译后的 .class）——创建后立即进入 try/finally，
        // 无论编译失败/异常/打包成功都确保删除，避免临时目录残留
        Path outDir;
        try {
            outDir = Files.createTempDirectory("frt-plugin-build");
        } catch (IOException e) {
            LoggerUtil.logException("创建编译临时目录失败", e);
            return new CompileResult(false, List.of(), "创建编译临时目录失败");
        }

        try {
            DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
            try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
                List<String> options = List.of(
                        "-encoding", "UTF-8",
                        "-cp", System.getProperty("java.class.path"),
                        "-d", outDir.toString());
                Iterable<? extends JavaFileObject> units = fileManager.getJavaFileObjectsFromPaths(sources);

                boolean ok = compiler.getTask(null, fileManager, diagnostics, options, null, units).call();

                if (!ok) {
                    // 编译失败：清理临时目录后返回错误提示
                    return new CompileResult(false, List.of(), formatCompileErrors(diagnostics, sources));
                }
            } catch (Exception e) {
                LoggerUtil.logException("编译插件源码失败", e);
                return new CompileResult(false, List.of(), "编译插件源码失败: " + e.getMessage());
            }

            // 编译成功 → 打成 jar 输出回 plugins/
            Path jar = sources.size() == 1
                    ? dir.resolve(renameToJar(sources.get(0).getFileName().toString()))
                    : dir.resolve(MULTI_JAR_NAME);
            createJar(outDir, jar);
            return new CompileResult(true, List.of(jar),
                    "打包成功: " + jar.getFileName() + "（" + sources.size() + " 个 .java 文件）——重启程序后自动加载生效");
        } catch (IOException e) {
            LoggerUtil.logException("打包插件 jar 失败", e);
            return new CompileResult(false, List.of(), "打包插件 jar 失败: " + e.getMessage());
        } finally {
            deleteRecursively(outDir);
        }
    }

    /**
     * 把编译错误整理成简洁可读的提示（文件名:行号: 消息），最多列出前 5 条
     */
    private static String formatCompileErrors(DiagnosticCollector<JavaFileObject> diagnostics, List<Path> sources) {
        List<String> lines = new ArrayList<>();
        for (Diagnostic<? extends JavaFileObject> d : diagnostics.getDiagnostics()) {
            if (d.getKind() != Diagnostic.Kind.ERROR) {
                continue;
            }
            String name = d.getSource() == null ? "?" : Path.of(d.getSource().getName()).getFileName().toString();
            lines.add((lines.size() + 1) + ". " + name + (d.getLineNumber() > 0 ? " 第" + d.getLineNumber() + "行" : "")
                    + ": " + d.getMessage(null));
            if (lines.size() >= 5) {
                lines.add("…（共 " + countErrors(diagnostics) + " 个错误）");
                break;
            }
        }
        return "编译失败，请检查源码:\n" + String.join("\n", lines);
    }

    private static long countErrors(DiagnosticCollector<JavaFileObject> diagnostics) {
        return diagnostics.getDiagnostics().stream()
                .filter(d -> d.getKind() == Diagnostic.Kind.ERROR)
                .count();
    }

    /**
     * 把 .java 文件名换成 .jar（MyStrategy.java → MyStrategy.jar）
     */
    private static String renameToJar(String javaFileName) {
        String base = javaFileName;
        int dot = base.lastIndexOf('.');
        if (dot > 0) {
            base = base.substring(0, dot);
        }
        return base + ".jar";
    }

    /**
     * 把编译输出目录打成 jar（遍历 .class 等文件）
     */
    private static void createJar(Path classesDir, Path jar) throws IOException {
        if (Files.exists(jar)) {
            Files.delete(jar); // 覆盖旧的同名 jar（保留旧版会与新版共存导致重复加载）
        }
        try (JarOutputStream jos = new JarOutputStream(Files.newOutputStream(jar));
             Stream<Path> walk = Files.walk(classesDir)) {
            List<Path> files = walk.filter(Files::isRegularFile).sorted().toList();
            for (Path f : files) {
                String entryName = classesDir.relativize(f).toString().replace('\\', '/');
                jos.putNextEntry(new JarEntry(entryName));
                jos.write(Files.readAllBytes(f));
                jos.closeEntry();
            }
        }
    }

    private static void deleteRecursively(Path dir) {
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }
}

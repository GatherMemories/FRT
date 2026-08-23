package com.awei.frt;

import com.awei.frt.factory.StrategyFactory;
import com.awei.frt.factory.StrategyLoader;
import com.awei.frt.service.PluginCompiler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 插件源码打包服务测试（2026-08-23 新功能）：
 * - 单个 .java → 同名 jar，含编译产物，StrategyLoader 可加载注册
 * - 多个 .java → my-strategies.jar（支持多文件互相依赖）
 * - 编译错误 → 返回错误提示、不产出 jar
 * - 无源码 / 覆盖旧 jar / UTF-8 中文注释 等边界
 */
class PluginCompilerTest {

    @TempDir
    Path tempDir;

    private Path pluginsDir() throws Exception {
        return Files.createDirectories(tempDir.resolve("plugins"));
    }

    @Test
    void singleSourceBuildsSameNameJarAndRegisters() throws Exception {
        Path plugins = pluginsDir();
        Files.writeString(plugins.resolve("MyStrategy.java"), """
            package com.example;

            import com.awei.frt.core.context.OperationContext;
            import com.awei.frt.core.node.FileNode;
            import com.awei.frt.core.strategy.AbstractOperationStrategy;

            // 中文注释：验证 UTF-8 编译
            public class MyStrategy extends AbstractOperationStrategy {
                @Override
                public String getStrategyType() { return "BuiltMyStrategy"; }
                @Override
                public String getDescription() { return "打包功能生成的策略"; }
                @Override
                protected boolean doAdd(FileNode node, OperationContext context) { return false; }
                @Override
                protected boolean doReplace(FileNode node, OperationContext context) { return false; }
                @Override
                protected boolean doDelete(FileNode node, OperationContext context) { return false; }
            }
            """, StandardCharsets.UTF_8);

        PluginCompiler.CompileResult r = PluginCompiler.compilePluginsToJar(plugins);

        assertTrue(r.isSuccess(), r.getMessage());
        assertEquals(1, r.getJars().size());
        Path jar = plugins.resolve("MyStrategy.jar");
        assertTrue(Files.exists(jar), "应生成同名 jar");
        try (JarFile jf = new JarFile(jar.toFile())) {
            assertTrue(jf.getEntry("com/example/MyStrategy.class") != null, "jar 应包含编译后的 class");
        }
        // 端到端：打包出的 jar 能被 StrategyLoader 加载并注册
        StrategyLoader.loadPluginJar(jar);
        assertTrue(StrategyFactory.isSupported("BuiltMyStrategy"), "打包生成的策略应可注册");
        assertEquals("BuiltMyStrategy", StrategyFactory.createStrategy("BuiltMyStrategy").getStrategyType());
    }

    @Test
    void multipleSourcesBuildSingleJarWithDependencies() throws Exception {
        Path plugins = pluginsDir();
        // 两个文件互相依赖：Helper 被 Strategy 引用
        Files.writeString(plugins.resolve("Helper.java"), """
            package com.example.helper;

            public final class Helper {
                public static boolean isDat(String name) {
                    return name.endsWith(".dat");
                }
            }
            """, StandardCharsets.UTF_8);
        Files.writeString(plugins.resolve("DatOnlyStrategy.java"), """
            package com.example.helper;

            import com.awei.frt.core.context.OperationContext;
            import com.awei.frt.core.node.FileNode;
            import com.awei.frt.core.strategy.AbstractOperationStrategy;

            public class DatOnlyStrategy extends AbstractOperationStrategy {
                @Override
                public String getStrategyType() { return "DatOnlyStrategy"; }
                @Override
                protected boolean accepts(FileNode node, OperationContext context) {
                    return !node.isDirectory() && Helper.isDat(node.getName());
                }
                @Override
                protected boolean doAdd(FileNode node, OperationContext context) { return false; }
                @Override
                protected boolean doReplace(FileNode node, OperationContext context) { return false; }
                @Override
                protected boolean doDelete(FileNode node, OperationContext context) { return false; }
            }
            """, StandardCharsets.UTF_8);

        PluginCompiler.CompileResult r = PluginCompiler.compilePluginsToJar(plugins);

        assertTrue(r.isSuccess(), r.getMessage());
        Path jar = plugins.resolve("my-strategies.jar");
        assertTrue(Files.exists(jar), "多文件应打进 my-strategies.jar");
        try (JarFile jf = new JarFile(jar.toFile())) {
            assertTrue(jf.getEntry("com/example/helper/Helper.class") != null);
            assertTrue(jf.getEntry("com/example/helper/DatOnlyStrategy.class") != null);
        }
        StrategyLoader.loadPluginJar(jar);
        assertTrue(StrategyFactory.isSupported("DatOnlyStrategy"));
    }

    @Test
    void compileErrorReturnsMessageAndNoJar() throws Exception {
        Path plugins = pluginsDir();
        Files.writeString(plugins.resolve("BrokenStrategy.java"), """
            package com.example;

            import com.awei.frt.core.strategy.AbstractOperationStrategy;

            public class BrokenStrategy extends AbstractOperationStrategy {
                @Override
                public String getStrategyType() { return "BrokenStrategy"; }
                @Override
                protected boolean doAdd(com.awei.frt.core.node.FileNode node,
                        com.awei.frt.core.context.OperationContext context) { return noSuchMethod(); }
                @Override
                protected boolean doReplace(com.awei.frt.core.node.FileNode node,
                        com.awei.frt.core.context.OperationContext context) { return false; }
                @Override
                protected boolean doDelete(com.awei.frt.core.node.FileNode node,
                        com.awei.frt.core.context.OperationContext context) { return false; }
            }
            """, StandardCharsets.UTF_8);

        PluginCompiler.CompileResult r = PluginCompiler.compilePluginsToJar(plugins);

        assertFalse(r.isSuccess(), "编译错误应返回失败");
        assertTrue(r.getMessage().contains("编译失败"), "错误提示应说明编译失败: " + r.getMessage());
        assertTrue(r.getMessage().contains("第"), "错误提示应带行号: " + r.getMessage());
        assertFalse(Files.exists(plugins.resolve("BrokenStrategy.jar")), "编译失败不得产出 jar");
    }

    @Test
    void noSourceFilesReturnsHint() throws Exception {
        Path plugins = pluginsDir();
        PluginCompiler.CompileResult r = PluginCompiler.compilePluginsToJar(plugins);
        assertFalse(r.isSuccess());
        assertTrue(r.getMessage().contains("没有 .java"), r.getMessage());
    }

    @Test
    void rebuildOverwritesExistingJar() throws Exception {
        Path plugins = pluginsDir();
        Files.writeString(plugins.resolve("V1Strategy.java"), """
            package com.example;

            import com.awei.frt.core.context.OperationContext;
            import com.awei.frt.core.node.FileNode;
            import com.awei.frt.core.strategy.AbstractOperationStrategy;

            public class V1Strategy extends AbstractOperationStrategy {
                @Override
                public String getStrategyType() { return "V1Strategy"; }
                @Override
                protected boolean doAdd(FileNode node, OperationContext context) { return false; }
                @Override
                protected boolean doReplace(FileNode node, OperationContext context) { return false; }
                @Override
                protected boolean doDelete(FileNode node, OperationContext context) { return false; }
            }
            """, StandardCharsets.UTF_8);
        // 预置一个旧 jar（模拟上次打包产物）
        Files.writeString(plugins.resolve("V1Strategy.jar"), "stale jar content");

        PluginCompiler.CompileResult r = PluginCompiler.compilePluginsToJar(plugins);

        assertTrue(r.isSuccess(), r.getMessage());
        try (JarFile jf = new JarFile(plugins.resolve("V1Strategy.jar").toFile())) {
            assertTrue(jf.getEntry("com/example/V1Strategy.class") != null, "旧 jar 应被覆盖为真实编译产物");
        }
    }

    @Test
    void missingPluginsDirIsCreated() {
        Path plugins = tempDir.resolve("not-exists-plugins");
        PluginCompiler.CompileResult r = assertDoesNotThrow(() -> PluginCompiler.compilePluginsToJar(plugins));
        assertFalse(r.isSuccess(), "空目录应提示无源码（目录被自动创建）");
        assertTrue(Files.isDirectory(plugins), "目录不存在时应自动创建");
    }
}

package com.awei.frt;

import com.awei.frt.core.strategy.OperationStrategy;
import com.awei.frt.factory.StrategyFactory;
import com.awei.frt.factory.StrategyLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 外部策略动态加载（功能升级2）测试：
 * - classpath SPI 方式：test 资源里的 META-INF/services 描述符
 * - plugins/ 插件 jar 方式：运行时编译一个策略类打成 jar，走 URLClassLoader + 自动类扫描
 * - 类型冲突保护：外部策略不能覆盖内置类型
 */
class StrategyLoaderTest {

    @TempDir
    Path tempDir;

    @Test
    void classpathSpiStrategyIsRegistered() {
        // 静态初始化（StrategyFactory 首次触达）时已通过 classpath SPI 加载
        StrategyLoader.loadFromClasspath();
        assertTrue(StrategyFactory.isSupported("ExternalPluginStrategy"),
                "classpath SPI 应注册测试插件策略");
        OperationStrategy s = StrategyFactory.createStrategy("ExternalPluginStrategy");
        assertNotNull(s);
        assertEquals("ExternalPluginStrategy", s.getStrategyType());
        assertFalse(StrategyFactory.getDescription("ExternalPluginStrategy").isEmpty());
    }

    @Test
    void pluginJarAutoScanRegistersStrategy() throws Exception {
        // 1. 运行时编译一个策略类（模拟玩家写的插件）
        Path srcDir = Files.createDirectories(tempDir.resolve("src"));
        Path classesDir = Files.createDirectories(tempDir.resolve("classes"));
        Path srcFile = srcDir.resolve("dynplugin/DynamicPlugin.java");
        Files.createDirectories(srcFile.getParent());
        Files.writeString(srcFile, """
            package dynplugin;

            import com.awei.frt.core.context.OperationContext;
            import com.awei.frt.core.node.FileNode;
            import com.awei.frt.core.strategy.OperationStrategy;

            public class DynamicPlugin implements OperationStrategy {
                public DynamicPlugin() {
                }

                @Override
                public String getStrategyType() {
                    return "DynamicPlugin";
                }

                @Override
                public String getDescription() {
                    return "动态编译加载的插件策略";
                }

                @Override
                public void execute(FileNode node, OperationContext context, String[] operationType) {
                }
            }
            """);

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        int compileResult = compiler.run(null, null, null,
                "-encoding", "UTF-8",
                "-cp", System.getProperty("java.class.path"),
                "-d", classesDir.toString(),
                srcFile.toString());
        assertEquals(0, compileResult, "插件类应编译成功");

        // 2. 打成 jar（不含 services 文件，走自动类扫描路径）
        Path pluginJar = tempDir.resolve("dynamic-plugin.jar");
        try (JarOutputStream jos = new JarOutputStream(Files.newOutputStream(pluginJar));
             var walk = Files.walk(classesDir)) {
            var files = walk.filter(Files::isRegularFile).toList();
            for (Path f : files) {
                String entryName = classesDir.relativize(f).toString().replace('\\', '/');
                jos.putNextEntry(new JarEntry(entryName));
                jos.write(Files.readAllBytes(f));
                jos.closeEntry();
            }
        }

        // 3. 加载插件 jar
        StrategyLoader.loadPluginJar(pluginJar);

        assertTrue(StrategyFactory.isSupported("DynamicPlugin"), "插件 jar 自动扫描应注册策略");
        OperationStrategy s = StrategyFactory.createStrategy("DynamicPlugin");
        assertEquals("DynamicPlugin", s.getStrategyType());
    }

    @Test
    void externalCannotOverrideBuiltin() {
        // 注册与内置同名的外部策略应被跳过（不覆盖）
        com.awei.frt.core.strategy.OperationStrategy builtin = StrategyFactory.createStrategy("McMod");
        StrategyLoader.loadFromClasspath(); // 幂等：重复注册仅告警跳过
        assertEquals("McMod", StrategyFactory.createStrategy("McMod").getStrategyType());
        assertTrue(StrategyFactory.isSupported("McMod"));
    }
}

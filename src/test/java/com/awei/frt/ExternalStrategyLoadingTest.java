package com.awei.frt;

import com.awei.frt.core.builder.MatchRuleLoader;
import com.awei.frt.core.strategy.OperationStrategy;
import com.awei.frt.factory.StrategyFactory;
import com.awei.frt.factory.StrategyLoader;
import com.awei.frt.model.MatchRule;
import com.awei.frt.testutil.ExtPluginSources;
import com.awei.frt.testutil.TestPluginBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 外部策略【读取/加载】全面测试：
 * - 自动类扫描（jar 无 services 文件）：正常类全部注册、无效类全部跳过
 * - 标准 SPI（jar 带 services 文件）：只注册声明类，不自动扫描补充
 * - 异常/边界：坏 jar、null/缺失路径、plugins 目录缺失、目录内非 jar 文件、重复加载幂等
 * - 与规则解析联动：规则可引用已注册外部策略类型，未注册类型解析失败
 * - 保护：外部策略不得覆盖内置类型；空白/null 类型被拒
 */
class ExternalStrategyLoadingTest {

    @TempDir
    static Path tempDir;

    static Path fullJar; // 自动类扫描 jar（extfull 包）
    static Path spiJar;  // 标准 SPI jar（extspi 包）

    @BeforeAll
    static void buildAndLoadPlugins() throws Exception {
        fullJar = TestPluginBuilder.buildPluginJar(
                Files.createDirectories(tempDir.resolve("full")), "ext-full-plugin.jar",
                ExtPluginSources.FULL, null);
        spiJar = TestPluginBuilder.buildPluginJar(
                Files.createDirectories(tempDir.resolve("spi")), "ext-spi-plugin.jar",
                ExtPluginSources.SPI, ExtPluginSources.SPI_SERVICES);
        StrategyLoader.loadPluginJar(fullJar);
        StrategyLoader.loadPluginJar(spiJar);
    }

    @Test
    void autoScanRegistersAllSuitableStrategies() {
        for (String type : List.of("ExtAddStrategy", "ExtFilterStrategy", "ExtParamStrategy",
                "ExtThrowStrategy", "ExtPlainExecuteStrategy")) {
            assertTrue(StrategyFactory.isSupported(type), "自动类扫描应注册: " + type);
            OperationStrategy s = StrategyFactory.createStrategy(type);
            assertNotNull(s);
            assertEquals(type, s.getStrategyType());
            assertFalse(StrategyFactory.getDescription(type).isEmpty(), "外部策略应有中文说明: " + type);
        }
    }

    @Test
    void unsuitableClassesAreNotRegistered() {
        // 空白类型 / null 类型 / 无公开无参构造 / 与内置冲突 / 普通类 → 一律不注册
        for (String type : List.of("ExtBlankTypeStrategy", "ExtNullTypeStrategy",
                "ExtNoCtorStrategy", "ExtOverrideBuiltinStrategy", "ExtNonStrategy")) {
            assertFalse(StrategyFactory.isSupported(type), "不应注册: " + type);
        }
        // 内置策略未被外部覆盖：McMod 仍是内置实现
        assertEquals("McMod", StrategyFactory.createStrategy("McMod").getStrategyType());
    }

    @Test
    void spiJarRegistersOnlyDeclaredClasses() {
        assertTrue(StrategyFactory.isSupported("SpiDeclaredStrategy"), "SPI 声明的策略应注册");
        assertFalse(StrategyFactory.isSupported("SpiHiddenStrategy"),
                "SPI 方式只注册 services 声明的类，不自动扫描 jar 内未声明的类");
    }

    @Test
    void duplicateLoadIsIdempotent() {
        // 重复加载同一 jar：类型已存在全部跳过，不抛异常、注册表无变化
        assertDoesNotThrow(() -> StrategyLoader.loadPluginJar(fullJar));
        assertTrue(StrategyFactory.isSupported("ExtAddStrategy"));
        assertEquals("ExtAddStrategy", StrategyFactory.createStrategy("ExtAddStrategy").getStrategyType());
    }

    @Test
    void invalidJarIsSkippedGracefully() throws Exception {
        Path badJar = tempDir.resolve("broken-plugin.jar");
        Files.writeString(badJar, "this is not a zip file", StandardCharsets.UTF_8);
        assertDoesNotThrow(() -> StrategyLoader.loadPluginJar(badJar), "损坏 jar 不得抛异常");
    }

    @Test
    void nullOrMissingJarIsSafe() {
        assertDoesNotThrow(() -> StrategyLoader.loadPluginJar(null));
        assertDoesNotThrow(() -> StrategyLoader.loadPluginJar(tempDir.resolve("not-exists.jar")));
    }

    @Test
    void missingPluginsDirIsSafe() {
        assertDoesNotThrow(() -> StrategyLoader.loadExternalStrategies(tempDir.resolve("no-plugins-dir")),
                "plugins 目录缺失时应静默跳过");
    }

    @Test
    void nonJarFilesInPluginsDirAreIgnored() throws Exception {
        Path pluginsDir = Files.createDirectories(tempDir.resolve("plugins-mixed"));
        Files.writeString(pluginsDir.resolve("readme.txt"), "not a plugin");
        Files.writeString(pluginsDir.resolve("note.md"), "# note");
        assertDoesNotThrow(() -> StrategyLoader.loadExternalStrategies(pluginsDir),
                "目录内非 jar 文件应被忽略");
    }

    @Test
    void externalTypesVisibleInRegistry() {
        assertTrue(StrategyFactory.getSupportedTypes().contains("ExtAddStrategy"));
        assertTrue(StrategyFactory.getSupportedTypes().contains("SpiDeclaredStrategy"));
        assertFalse(StrategyFactory.getDescription("ExtAddStrategy").isEmpty());
    }

    @Test
    void ruleJsonReferencingExternalStrategyParses() {
        String json = """
            {
              "strategyType": "ExtAddStrategy",
              "patterns": ["*"],
              "inheritToSubfolders": false
            }
            """;
        MatchRule rule = MatchRuleLoader.fromJson(json);
        assertNotNull(rule, "规则应能引用已注册的外部策略类型");
        assertEquals("ExtAddStrategy", rule.getStrategyType());
    }

    @Test
    void ruleJsonReferencingUnknownStrategyIsRejected() {
        String json = """
            {
              "strategyType": "NoSuchExternalStrategy",
              "patterns": ["*"],
              "inheritToSubfolders": false
            }
            """;
        assertNull(MatchRuleLoader.fromJson(json), "未注册的策略类型应解析失败");
    }

    @Test
    void chainStepReferencingExternalStrategyParses() {
        String json = """
            {
              "strategyType": "FileSameName",
              "patterns": ["*.txt"],
              "strategyChain": [
                {"strategyType": "ExtAddStrategy"}
              ],
              "inheritToSubfolders": false
            }
            """;
        MatchRule rule = MatchRuleLoader.fromJson(json);
        assertNotNull(rule, "策略链步骤应能引用外部策略类型");
        assertEquals(2, rule.getEffectiveStrategies().size(), "主策略 + 外部策略步骤");
        assertEquals("ExtAddStrategy", rule.getEffectiveStrategies().get(1).getStrategyType());
    }
}

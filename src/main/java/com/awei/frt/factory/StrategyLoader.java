package com.awei.frt.factory;

import com.awei.frt.core.strategy.OperationStrategy;
import com.awei.frt.util.LoggerUtil;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 外部策略动态加载器（功能升级 2）
 * 玩家不需要修改源码，按规范编写策略类即可动态接入：
 *
 * 1. 插件目录扫描：工作目录下 plugins/ 中每个 .jar 视为一个策略插件
 *    - 优先读取 jar 内 META-INF/services/com.awei.frt.core.strategy.OperationStrategy（标准 SPI）
 *    - 未提供 services 文件时自动扫描 jar 内实现 OperationStrategy 的具体类（有公开无参构造即可）
 * 2. classpath SPI：应用 classpath 上的 META-INF/services 描述符（适合与主程序同 classpath 部署）
 *
 * 加载到的策略通过 StrategyFactory.register 注册；类型与内置/已有策略冲突时跳过并告警。
 * 策略类规范：实现 OperationStrategy，getStrategyType() 返回唯一类型标识（规则文件 strategyType 字段）。
 */
public final class StrategyLoader {

    private static final String SERVICE_FILE = "META-INF/services/" + OperationStrategy.class.getName();

    private StrategyLoader() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 加载外部策略（默认插件目录：工作目录下 plugins/）
     * 由 StrategyFactory 静态初始化时调用一次
     */
    public static void loadExternalStrategies() {
        loadExternalStrategies(Path.of("plugins"));
    }

    /**
     * 加载外部策略：classpath SPI + 指定插件目录扫描
     * @param pluginsDir 插件目录（不存在则跳过）
     */
    public static void loadExternalStrategies(Path pluginsDir) {
        loadFromClasspath();

        if (pluginsDir == null || !Files.isDirectory(pluginsDir)) {
            return;
        }
        List<Path> jars = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(pluginsDir, "*.jar")) {
            ds.forEach(jars::add);
        } catch (IOException e) {
            LoggerUtil.logException("扫描插件目录失败: " + pluginsDir, e);
            return;
        }
        for (Path jar : jars) {
            loadPluginJar(jar);
        }
    }

    /**
     * 从应用 classpath 的 META-INF/services 描述符加载策略
     */
    public static void loadFromClasspath() {
        try {
            ServiceLoader<OperationStrategy> loader = ServiceLoader.load(OperationStrategy.class);
            for (OperationStrategy strategy : loader) {
                registerExternal(strategy, "classpath");
            }
        } catch (Throwable t) {
            LoggerUtil.logException("classpath 策略 SPI 加载失败", t);
        }
    }

    /**
     * 加载单个插件 jar
     * @param jar 插件 jar 路径
     */
    public static void loadPluginJar(Path jar) {
        if (jar == null || !Files.isRegularFile(jar)) {
            return;
        }
        try (URLClassLoader cl = new URLClassLoader(
                new URL[]{jar.toUri().toURL()}, StrategyLoader.class.getClassLoader())) {
            int loaded = 0;
            String jarName = jar.getFileName().toString();

            // 方式A：标准 SPI（仅当 jar 自身含 META-INF/services 描述符；
            // 注意 ServiceLoader 会继承父 classpath 的 provider，必须按"实际注册成功"计数）
            if (hasEntry(jar, SERVICE_FILE)) {
                ServiceLoader<OperationStrategy> loader = ServiceLoader.load(OperationStrategy.class, cl);
                for (OperationStrategy strategy : loader) {
                    if (registerExternal(strategy, jarName)) {
                        loaded++;
                    }
                }
            }

            // 方式B：自动扫描 jar 内实现 OperationStrategy 的具体类（无 services 文件时的兜底方式）
            if (loaded == 0) {
                loaded = scanJarForStrategies(jar, cl);
            }

            if (loaded == 0) {
                LoggerUtil.logWarn("[插件] 未在插件中找到策略实现: " + jar.getFileName());
            } else {
                LoggerUtil.logInfo("[插件] 已加载策略插件: " + jar.getFileName() + "（" + loaded + " 个策略）");
            }
        } catch (Throwable t) {
            LoggerUtil.logException("加载策略插件失败: " + jar.getFileName(), t);
        }
    }

    /**
     * jar 内是否包含指定条目
     */
    private static boolean hasEntry(Path jar, String entryName) {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            return zip.getEntry(entryName) != null;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * 自动扫描 jar 内的策略实现类（无 services 文件时的兜底方式）
     */
    private static int scanJarForStrategies(Path jar, URLClassLoader cl) throws IOException {
        int loaded = 0;
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            java.util.Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (!name.endsWith(".class") || name.startsWith("META-INF/")) {
                    continue;
                }
                String className = name.substring(0, name.length() - 6).replace('/', '.');
                try {
                    Class<?> cls = Class.forName(className, false, cl);
                    if (OperationStrategy.class.isAssignableFrom(cls)
                            && !cls.isInterface()
                            && !cls.isEnum()
                            && !Modifier.isAbstract(cls.getModifiers())) {
                        Object instance = cls.getDeclaredConstructor().newInstance();
                        // 只统计实际注册成功的类（空白/null 类型、与内置冲突被跳过的类不计入，
                        // 否则"已加载 N 个策略"日志数字虚高——加载计数不准确）
                        if (registerExternal((OperationStrategy) instance, jar.getFileName().toString())) {
                            loaded++;
                        }
                    }
                } catch (Throwable ignored) {
                    // 单个类解析失败不影响其他类（可能是不相关的依赖类）
                }
            }
        }
        return loaded;
    }

    /**
     * 注册外部策略（类型冲突时跳过）
     * @return 是否实际注册成功（冲突/异常返回 false）
     */
    private static boolean registerExternal(OperationStrategy strategy, String source) {
        try {
            String type = strategy.getStrategyType();
            if (type == null || type.isBlank()) {
                LoggerUtil.logWarn("[插件] 忽略未声明策略类型的实现: " + strategy.getClass().getName() + "（来自 " + source + "）");
                return false;
            }
            if (StrategyFactory.isSupported(type)) {
                LoggerUtil.logWarn("[插件] 策略类型已存在，跳过外部覆盖: " + type + "（来自 " + source + "）");
                return false;
            }
            StrategyFactory.register(type, () -> strategy, strategy.getDescription());
            LoggerUtil.logInfo("[插件] 注册外部策略: " + type + "（来自 " + source + "）");
            return true;
        } catch (Throwable t) {
            LoggerUtil.logException("注册外部策略失败: " + source, t);
            return false;
        }
    }
}

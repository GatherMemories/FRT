package com.awei.frt.factory;

import com.awei.frt.core.strategy.FileSameNameStrategy;
import com.awei.frt.core.strategy.McModStrategy;
import com.awei.frt.core.strategy.OperationStrategy;
import com.awei.frt.core.strategy.StrategyProxy;
import com.awei.frt.core.strategy.ZipEntryContentStrategy;
import com.awei.frt.core.strategy.ZipEntryNameStrategy;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * 策略工厂（注册表模式）
 * 取代旧版 StrategyType 枚举 + switch：每个策略类自己声明 getStrategyType()，
 * 工厂以 类型 -> 供应商(Supplier) 注册表登记，新增策略无需改动工厂代码
 * （外部策略动态加载也通过 register 接入，见 StrategyLoader）。
 */
public class StrategyFactory {

    // 策略注册表：类型 -> 供应商（保持注册顺序，便于菜单展示）
    private static final Map<String, Supplier<OperationStrategy>> REGISTRY = new LinkedHashMap<>();
    // 策略说明（菜单/向导展示用）
    private static final Map<String, String> DESCRIPTIONS = new LinkedHashMap<>();
    // 策略实例缓存（策略为无状态单例）
    private static final Map<String, OperationStrategy> INSTANCE_CACHE = new HashMap<>();

    // 内置策略注册（可被 register 覆盖；外部策略不得覆盖内置类型，见 StrategyLoader）
    static {
        register("FileSameName", FileSameNameStrategy::new, "同名文件处理策略（按文件名匹配，支持通配符）");
        register("McMod", McModStrategy::new, "Minecraft 模组策略（按 modId 匹配 jar）");
        register("ZipEntryName", ZipEntryNameStrategy::new, "压缩包内文件名匹配策略（zip/jar 内部条目名，支持通配符）");
        register("ZipEntryContent", ZipEntryContentStrategy::new, "压缩包内文件内容匹配策略（读取 zip/jar 条目文本，contentContains 参数）");
        // 动态加载外部策略插件（plugins/ 目录 + classpath SPI，见 StrategyLoader）
        StrategyLoader.loadExternalStrategies();
    }

    private StrategyFactory() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 注册策略类型（供内置注册与外部插件加载调用）
     * @param type        策略类型标识（规则文件 strategyType 字段）
     * @param supplier    策略实例供应商
     * @param description 中文说明（可空）
     */
    public static synchronized void register(String type, Supplier<OperationStrategy> supplier, String description) {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("策略类型不能为空");
        }
        if (supplier == null) {
            throw new IllegalArgumentException("策略供应商不能为空");
        }
        REGISTRY.put(type, supplier);
        if (description != null && !description.isBlank()) {
            DESCRIPTIONS.put(type, description);
        }
        // 重新注册后失效旧缓存
        INSTANCE_CACHE.remove(type);
    }

    /**
     * 策略类型是否已注册
     */
    public static boolean isSupported(String type) {
        return type != null && REGISTRY.containsKey(type);
    }

    /**
     * 获取所有已注册的策略类型（不可修改视图）
     */
    public static Set<String> getSupportedTypes() {
        return Collections.unmodifiableSet(REGISTRY.keySet());
    }

    /**
     * 获取策略说明
     */
    public static String getDescription(String type) {
        return DESCRIPTIONS.getOrDefault(type, "");
    }

    /**
     * 创建（或取缓存）匹配策略
     * @param type 策略类型（规则文件中的 strategyType）
     * @return 策略实例（无状态单例）
     */
    public static synchronized OperationStrategy createStrategy(String type) {
        Supplier<OperationStrategy> supplier = REGISTRY.get(type);
        if (supplier == null) {
            throw new IllegalArgumentException("不支持的匹配策略类型: " + type);
        }
        // 统一包一层动态代理：日志 / 异常兜底 / 统计（见 StrategyProxy）
        return INSTANCE_CACHE.computeIfAbsent(type, k -> StrategyProxy.wrap(supplier.get()));
    }
}

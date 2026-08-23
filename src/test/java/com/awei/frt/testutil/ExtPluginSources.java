package com.awei.frt.testutil;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 测试用外部策略插件源码集。
 * <p>
 * 由 {@link TestPluginBuilder} 在测试运行时编译成 jar 后交给 StrategyLoader 加载，
 * 用于全面测试"外部策略读取与执行"：
 * <ul>
 *   <li>extfull 包（自动类扫描）：正常策略（模板钩子/裸实现/过滤/参数/抛异常）+ 无效类（空白类型/空类型/无公开无参构造/与内置冲突/普通类）；</li>
 *   <li>extspi 包（标准 SPI）：services 声明 1 个、jar 内隐藏 1 个未声明的类。</li>
 * </ul>
 */
public final class ExtPluginSources {

    private ExtPluginSources() {
    }

    /** extfull 包全部类（自动类扫描 jar） */
    public static final Map<String, String> FULL = new LinkedHashMap<>();

    /** extspi 包全部类（SPI jar） */
    public static final Map<String, String> SPI = new LinkedHashMap<>();

    /** SPI jar 的 services 描述符：只声明 SpiDeclaredStrategy（SpiHiddenStrategy 不声明） */
    public static final List<String> SPI_SERVICES = List.of("extspi.SpiDeclaredStrategy");

    // ==================== extfull：自动类扫描 ====================

    static {
        // 1. 完整模板钩子策略：ADD/REPLACE/DELETE 全走 FileUtil + 操作记录 + handled
        FULL.put("extfull.ExtAddStrategy", """
            package extfull;

            import com.awei.frt.core.context.OperationContext;
            import com.awei.frt.core.node.FileNode;
            import com.awei.frt.core.strategy.AbstractOperationStrategy;
            import com.awei.frt.core.uitls.FileUtil;
            import com.awei.frt.model.OperationRecord;

            import java.nio.file.Files;
            import java.nio.file.Path;

            /** 外部插件策略（自动类扫描）：完整实现 ADD/REPLACE/DELETE 三钩子，FileUtil 真实操作 + 记录 + handled */
            public class ExtAddStrategy extends AbstractOperationStrategy {
                @Override
                public String getStrategyType() {
                    return "ExtAddStrategy";
                }

                @Override
                public String getDescription() {
                    return "外部插件：ADD/REPLACE/DELETE 全套文件操作（FileUtil）";
                }

                @Override
                protected boolean accepts(FileNode node, OperationContext context) {
                    return !node.isDirectory();
                }

                @Override
                protected boolean doAdd(FileNode node, OperationContext context) {
                    Path target = context.getTargetPath(node.getRelativePath());
                    if (Files.exists(target)) {
                        return false;
                    }
                    OperationRecord record = newRecord(context);
                    boolean ok = FileUtil.addFile(node.getPath(), target, record, context.isDryRun());
                    context.recordOperation(record);
                    if (ok) {
                        node.setHandled(true);
                    }
                    return true;
                }

                @Override
                protected boolean doReplace(FileNode node, OperationContext context) {
                    Path target = context.getTargetPath(node.getRelativePath());
                    if (!Files.exists(target)) {
                        return false;
                    }
                    OperationRecord record = newRecord(context);
                    boolean ok = FileUtil.replaceFile(node.getPath(), target, record, context.isDryRun());
                    context.recordOperation(record);
                    if (ok) {
                        node.setHandled(true);
                    }
                    return true;
                }

                @Override
                protected boolean doDelete(FileNode node, OperationContext context) {
                    Path target = context.getTargetPath(node.getRelativePath());
                    OperationRecord record = newRecord(context);
                    boolean ok = FileUtil.deleteFile(target, record, context.isDryRun());
                    context.recordOperation(record);
                    if (ok) {
                        node.setHandled(true);
                    }
                    return true;
                }
            }
            """);

        // 2. 过滤策略：覆盖 accepts() 只接受 .dat 文件
        FULL.put("extfull.ExtFilterStrategy", """
            package extfull;

            import com.awei.frt.core.context.OperationContext;
            import com.awei.frt.core.node.FileNode;
            import com.awei.frt.core.strategy.AbstractOperationStrategy;
            import com.awei.frt.core.uitls.FileUtil;
            import com.awei.frt.model.OperationRecord;

            import java.nio.file.Files;
            import java.nio.file.Path;

            /** 外部插件策略：覆盖 accepts() 只处理 .dat 文件 */
            public class ExtFilterStrategy extends AbstractOperationStrategy {
                @Override
                public String getStrategyType() {
                    return "ExtFilterStrategy";
                }

                @Override
                public String getDescription() {
                    return "外部插件：只接受 .dat 文件的过滤策略";
                }

                @Override
                protected boolean accepts(FileNode node, OperationContext context) {
                    return !node.isDirectory() && node.getName().endsWith(".dat");
                }

                @Override
                protected boolean doAdd(FileNode node, OperationContext context) {
                    Path target = context.getTargetPath(node.getRelativePath());
                    if (Files.exists(target)) {
                        return false;
                    }
                    OperationRecord record = newRecord(context);
                    boolean ok = FileUtil.addFile(node.getPath(), target, record, context.isDryRun());
                    context.recordOperation(record);
                    if (ok) {
                        node.setHandled(true);
                    }
                    return true;
                }

                @Override
                protected boolean doReplace(FileNode node, OperationContext context) {
                    return false;
                }

                @Override
                protected boolean doDelete(FileNode node, OperationContext context) {
                    return false;
                }
            }
            """);

        // 3. 参数策略：读取 replacements.suffix，追加到目标文件名
        FULL.put("extfull.ExtParamStrategy", """
            package extfull;

            import com.awei.frt.core.context.OperationContext;
            import com.awei.frt.core.node.FileNode;
            import com.awei.frt.core.strategy.AbstractOperationStrategy;
            import com.awei.frt.core.uitls.FileUtil;
            import com.awei.frt.model.OperationRecord;

            import java.nio.file.Files;
            import java.nio.file.Path;

            /** 外部插件策略：通过 context.getRuleParam 读取 replacements 扩展参数（suffix 追加到目标文件名） */
            public class ExtParamStrategy extends AbstractOperationStrategy {
                @Override
                public String getStrategyType() {
                    return "ExtParamStrategy";
                }

                @Override
                public String getDescription() {
                    return "外部插件：读取 replacements 扩展参数（suffix）";
                }

                @Override
                protected boolean accepts(FileNode node, OperationContext context) {
                    return !node.isDirectory();
                }

                @Override
                protected boolean doAdd(FileNode node, OperationContext context) {
                    Path target = context.getTargetPath(node.getRelativePath());
                    String suffix = context.getRuleParam("suffix");
                    if (suffix != null && !suffix.isEmpty()) {
                        target = target.resolveSibling(target.getFileName() + suffix);
                    }
                    if (Files.exists(target)) {
                        return false;
                    }
                    OperationRecord record = newRecord(context);
                    boolean ok = FileUtil.addFile(node.getPath(), target, record, context.isDryRun());
                    context.recordOperation(record);
                    if (ok) {
                        node.setHandled(true);
                    }
                    return true;
                }

                @Override
                protected boolean doReplace(FileNode node, OperationContext context) {
                    return false;
                }

                @Override
                protected boolean doDelete(FileNode node, OperationContext context) {
                    return false;
                }
            }
            """);

        // 4. 抛异常策略：doAdd 抛 RuntimeException，验证 StrategyProxy 兜底
        FULL.put("extfull.ExtThrowStrategy", """
            package extfull;

            import com.awei.frt.core.context.OperationContext;
            import com.awei.frt.core.node.FileNode;
            import com.awei.frt.core.strategy.AbstractOperationStrategy;

            /** 外部插件策略：doAdd 抛异常（验证代理兜底：记失败记录、不中断整个更新） */
            public class ExtThrowStrategy extends AbstractOperationStrategy {
                @Override
                public String getStrategyType() {
                    return "ExtThrowStrategy";
                }

                @Override
                public String getDescription() {
                    return "外部插件：doAdd 抛异常（验证代理兜底）";
                }

                @Override
                protected boolean accepts(FileNode node, OperationContext context) {
                    return !node.isDirectory();
                }

                @Override
                protected boolean doAdd(FileNode node, OperationContext context) {
                    throw new RuntimeException("ext-boom");
                }

                @Override
                protected boolean doReplace(FileNode node, OperationContext context) {
                    return false;
                }

                @Override
                protected boolean doDelete(FileNode node, OperationContext context) {
                    return false;
                }
            }
            """);

        // 5. 裸实现策略：直接实现 OperationStrategy（不走模板方法）
        FULL.put("extfull.ExtPlainExecuteStrategy", """
            package extfull;

            import com.awei.frt.core.context.OperationContext;
            import com.awei.frt.core.node.FileNode;
            import com.awei.frt.core.strategy.OperationStrategy;
            import com.awei.frt.core.uitls.FileUtil;
            import com.awei.frt.model.OperationRecord;

            import java.nio.file.Files;
            import java.nio.file.Path;
            import java.util.Arrays;

            /** 外部插件策略：直接实现 OperationStrategy.execute 的裸策略（不继承模板基类） */
            public class ExtPlainExecuteStrategy implements OperationStrategy {
                @Override
                public String getStrategyType() {
                    return "ExtPlainExecuteStrategy";
                }

                @Override
                public String getDescription() {
                    return "外部插件：直接实现 execute 的裸策略";
                }

                @Override
                public void execute(FileNode node, OperationContext context, String[] operationType) {
                    if (node == null || context == null || operationType == null
                            || node.isDirectory() || node.isHandled()) {
                        return;
                    }
                    if (!Arrays.asList(operationType).contains(OperationContext.OPERATION_ADD)) {
                        return;
                    }
                    Path target = context.getTargetPath(node.getRelativePath());
                    if (Files.exists(target)) {
                        return;
                    }
                    OperationRecord record = new OperationRecord();
                    record.setStrategyType(getStrategyType());
                    boolean ok = FileUtil.addFile(node.getPath(), target, record, context.isDryRun());
                    context.recordOperation(record);
                    if (ok) {
                        node.setHandled(true);
                    }
                }
            }
            """);

        // 5.5 规则黑白名单策略：accepts 直接复用基类 matchesRules（验证封装对外部策略可用）
        FULL.put("extfull.ExtRuleMatchStrategy", """
            package extfull;

            import com.awei.frt.core.context.OperationContext;
            import com.awei.frt.core.node.FileNode;
            import com.awei.frt.core.strategy.AbstractOperationStrategy;
            import com.awei.frt.core.uitls.FileUtil;
            import com.awei.frt.model.OperationRecord;

            import java.nio.file.Files;
            import java.nio.file.Path;

            /** 外部插件策略：accepts 用基类 matchesRules 按规则 patterns/excludePatterns 过滤（与内置策略同款） */
            public class ExtRuleMatchStrategy extends AbstractOperationStrategy {
                @Override
                public String getStrategyType() {
                    return "ExtRuleMatchStrategy";
                }

                @Override
                public String getDescription() {
                    return "外部插件：基类 matchesRules 黑白名单过滤";
                }

                @Override
                protected boolean accepts(FileNode node, OperationContext context) {
                    return !node.isDirectory() && matchesRules(node, context);
                }

                @Override
                protected boolean doAdd(FileNode node, OperationContext context) {
                    Path target = context.getTargetPath(node.getRelativePath());
                    if (Files.exists(target)) {
                        return false;
                    }
                    OperationRecord record = newRecord(context);
                    boolean ok = FileUtil.addFile(node.getPath(), target, record, context.isDryRun());
                    context.recordOperation(record);
                    if (ok) {
                        node.setHandled(true);
                    }
                    return true;
                }

                @Override
                protected boolean doReplace(FileNode node, OperationContext context) {
                    return false;
                }

                @Override
                protected boolean doDelete(FileNode node, OperationContext context) {
                    return false;
                }
            }
            """);

        // 6. 无效：空白策略类型（应被跳过并告警）
        FULL.put("extfull.ExtBlankTypeStrategy", """
            package extfull;

            import com.awei.frt.core.context.OperationContext;
            import com.awei.frt.core.node.FileNode;
            import com.awei.frt.core.strategy.OperationStrategy;

            /** 无效插件：getStrategyType() 返回空白，加载时应被跳过 */
            public class ExtBlankTypeStrategy implements OperationStrategy {
                @Override
                public String getStrategyType() {
                    return "   ";
                }

                @Override
                public void execute(FileNode node, OperationContext context, String[] operationType) {
                }
            }
            """);

        // 7. 无效：null 策略类型（应被跳过并告警）
        FULL.put("extfull.ExtNullTypeStrategy", """
            package extfull;

            import com.awei.frt.core.context.OperationContext;
            import com.awei.frt.core.node.FileNode;
            import com.awei.frt.core.strategy.OperationStrategy;

            /** 无效插件：getStrategyType() 返回 null，加载时应被跳过 */
            public class ExtNullTypeStrategy implements OperationStrategy {
                @Override
                public String getStrategyType() {
                    return null;
                }

                @Override
                public void execute(FileNode node, OperationContext context, String[] operationType) {
                }
            }
            """);

        // 8. 无效：无公开无参构造（自动类扫描 newInstance 失败应跳过）
        FULL.put("extfull.ExtNoCtorStrategy", """
            package extfull;

            import com.awei.frt.core.context.OperationContext;
            import com.awei.frt.core.node.FileNode;
            import com.awei.frt.core.strategy.OperationStrategy;

            /** 无效插件：只有私有构造，自动类扫描无法实例化，应被跳过 */
            public class ExtNoCtorStrategy implements OperationStrategy {
                private ExtNoCtorStrategy() {
                }

                @Override
                public String getStrategyType() {
                    return "ExtNoCtorStrategy";
                }

                @Override
                public void execute(FileNode node, OperationContext context, String[] operationType) {
                }
            }
            """);

        // 9. 无效：类型与内置策略冲突（应被跳过，不覆盖内置）
        FULL.put("extfull.ExtOverrideBuiltinStrategy", """
            package extfull;

            import com.awei.frt.core.context.OperationContext;
            import com.awei.frt.core.node.FileNode;
            import com.awei.frt.core.strategy.OperationStrategy;

            /** 无效插件：策略类型与内置 McMod 冲突，加载时应被跳过（外部不得覆盖内置） */
            public class ExtOverrideBuiltinStrategy implements OperationStrategy {
                @Override
                public String getStrategyType() {
                    return "McMod";
                }

                @Override
                public void execute(FileNode node, OperationContext context, String[] operationType) {
                }
            }
            """);

        // 10. 普通类：不实现 OperationStrategy（自动扫描应跳过）
        FULL.put("extfull.ExtNonStrategy", """
            package extfull;

            /** 普通类：不实现 OperationStrategy，自动类扫描应跳过 */
            public final class ExtNonStrategy {
                public String hello() {
                    return "not a strategy";
                }
            }
            """);
    }

    // ==================== extspi：标准 SPI ====================

    static {
        // 11. SPI 声明的策略
        SPI.put("extspi.SpiDeclaredStrategy", """
            package extspi;

            import com.awei.frt.core.context.OperationContext;
            import com.awei.frt.core.node.FileNode;
            import com.awei.frt.core.strategy.OperationStrategy;

            /** SPI 描述符声明的外部策略 */
            public class SpiDeclaredStrategy implements OperationStrategy {
                @Override
                public String getStrategyType() {
                    return "SpiDeclaredStrategy";
                }

                @Override
                public String getDescription() {
                    return "SPI 声明的外部策略";
                }

                @Override
                public void execute(FileNode node, OperationContext context, String[] operationType) {
                }
            }
            """);

        // 12. jar 内存在但未在 services 中声明（SPI 方式不应自动扫描补充）
        SPI.put("extspi.SpiHiddenStrategy", """
            package extspi;

            import com.awei.frt.core.context.OperationContext;
            import com.awei.frt.core.node.FileNode;
            import com.awei.frt.core.strategy.OperationStrategy;

            /** jar 内存在但 services 未声明的策略（不应被注册） */
            public class SpiHiddenStrategy implements OperationStrategy {
                @Override
                public String getStrategyType() {
                    return "SpiHiddenStrategy";
                }

                @Override
                public void execute(FileNode node, OperationContext context, String[] operationType) {
                }
            }
            """);
    }
}

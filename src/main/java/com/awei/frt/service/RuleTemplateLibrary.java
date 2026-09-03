package com.awei.frt.service;

import com.awei.frt.core.builder.MatchRuleLoader;
import com.awei.frt.model.MatchRule;
import com.awei.frt.model.RuleTemplate;
import com.awei.frt.util.LoggerUtil;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

/**
 * 规则模板库（FR-2 / FR-1 自定义模板扩展）
 * <p>
 * 内置模板：从 classpath 资源 rule-templates.json 加载（Minecraft 模组更新 / 资源包 /
 * 配置文件同步等场景），懒加载后静态缓存，只读不可删改。
 * <p>
 * 自定义模板（v0.1.16 新增）：用户把当前规则保存为自定义模板，持久化为独立用户文件
 * {@code user-templates.json}（与内置 rule-templates.json 同构，RuleTemplate 模型复用），
 * 与内置模板合并展示、一键复用。存储位置两级定位：主位置 {@code <工作目录>/templates/}，
 * 不可写时回退 {@code <user.home>/.frt/templates/}（发布包场景启动脚本 cd 到解压目录，
 * 用户解压目录可写，jar 内置资源保持只读）。加载/校验/保存/删除/改名任何失败一律
 * 静默降级（返回失败状态/空列表 + 日志），不抛异常、不崩溃、不打断向导流程。
 */
public final class RuleTemplateLibrary {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private static final String RESOURCE = "/rule-templates.json";

    /** 用户自定义模板文件名（内置 rule-templates.json 同构聚合文件） */
    private static final String USER_TEMPLATES_FILE = "user-templates.json";

    /** 文件写回互斥锁：GUI 保存/删除/改名与控制台保存共享同一文件，整文件读改写防同窗口双操作 */
    private static final Object LOCK = new Object();

    /** 自定义模板 id 序号（随机种子 + 单调递增，保证同进程内唯一、跨进程碰撞概率可忽略） */
    private static final AtomicLong ID_SEQ = new AtomicLong(ThreadLocalRandom.current().nextLong(0, 1L << 40));

    // 静态缓存：内置模板为只读资源，加载一次即可（懒加载，加载发生在向导打开时，非启动路径）
    private static volatile List<RuleTemplate> cached;

    /** 测试用：覆盖用户模板文件路径（隔离测试对真实工作目录/用户主目录的写入污染；生产流程不要使用） */
    private static volatile Path customTemplatesFileOverride;

    /** 最近一次成功写入自定义模板的位置（读侧与写侧一致：主位置只读/写失败回退兜底后，读侧也走兜底） */
    private static volatile Path lastSuccessfulWrite;

    /** 测试用：两级定位的工作目录/用户主目录覆盖（默认 null=Paths.get(".")/user.home；测试注入 @TempDir 隔离） */
    private static volatile Path[] customTemplatesLocationOverride;

    /** 测试用：可写性探测注入（默认 null=Files.isWritable；注入固定结果模拟"主位置只读"场景） */
    private static volatile Predicate<Path> writabilityProbeOverride;

    private RuleTemplateLibrary() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 自定义模板保存结果状态（保存失败绝不抛异常到调用方）
     */
    public enum SaveStatus {
        /** 保存成功 */
        SUCCESS,
        /** 与现有自定义模板同名（overwrite=false 时返回；调用方询问后带 overwrite=true 重调覆盖） */
        DUPLICATE_NAME,
        /** 规则不合法（id/name 为空或 rule 无法通过 MatchRuleLoader 校验），未写文件 */
        INVALID_RULE,
        /** 与内置模板重名，内置只读不可覆盖 */
        BUILTIN_NAME_CONFLICT,
        /** 文件读写失败（主位置与兜底位置均不可写），未写文件 */
        IO_ERROR
    }

    /**
     * 加载全部有效内置模板（classpath 资源）。
     * 逐条校验：模板 rule 必须能被 MatchRuleLoader.fromJson 解析（strategyType 与链步骤已注册），
     * 无效模板跳过并 logWarn，剩余有效模板照常返回；文件缺失/损坏返回空列表并 logWarn，
     * 不抛异常、不崩溃。
     *
     * @return 有效模板列表（不可修改视图）；加载失败返回空列表
     */
    public static List<RuleTemplate> loadAll() {
        List<RuleTemplate> local = cached;
        if (local == null) {
            synchronized (RuleTemplateLibrary.class) {
                local = cached;
                if (local == null) {
                    local = loadFromClasspath();
                    cached = local;
                }
            }
        }
        return local;
    }

    /**
     * 按模板 id 查找，未命中返回 null。
     * v0.1.16 语义扩展：内置优先，内置未命中再查自定义模板（既有"内置 7 模板"断言与
     * no-such-template 断言均不受影响）。
     */
    public static RuleTemplate findById(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        for (RuleTemplate t : loadAll()) {
            if (id.equals(t.getId())) {
                return t;
            }
        }
        // 内置未命中 → 查自定义（每次重读用户文件，保证最新状态）
        for (RuleTemplate t : loadAllCustom()) {
            if (id.equals(t.getId())) {
                return t;
            }
        }
        return null;
    }

    /**
     * 加载全部有效自定义模板（用户文件，每次调用重读——文件极小，避免缓存失效问题；
     * 静态缓存仅限内置模板）。
     * <p>
     * 文件缺失/损坏 → 空列表 + logWarn，不抛异常；逐条校验与内置加载一致
     * （rule 必须经 MatchRuleLoader 校验，坏项跳过）。
     *
     * @return 自定义模板列表（不可修改视图）
     */
    public static List<RuleTemplate> loadAllCustom() {
        return loadCustomFrom(getCustomTemplatesFile());
    }

    /**
     * 内置 + 自定义合并列表（内置在前、自定义在后，来源天然区分）；不可修改视图。
     */
    public static List<RuleTemplate> loadAllMerged() {
        List<RuleTemplate> merged = new ArrayList<>();
        merged.addAll(loadAll());       // 内置在前
        merged.addAll(loadAllCustom()); // 自定义在后
        return Collections.unmodifiableList(merged);
    }

    /**
     * 保存自定义模板（§3.3，审查 round 2 修复项）：
     * 校验（id/name/rule 非空 + rule 经 MatchRuleLoader 校验）→ 合并现有列表
     * （传入 id 与现有列表冲突时自动重新生成 id，见 §9.5；同 name 不同 id 按 overwrite 覆盖
     * 并保持原 id；内置重名拒绝）→ 整文件读改写写回。主位置写失败时回退兜底位置，
     * 并<b>基于兜底位置现有列表重新合并</b>（不覆盖兜底已有模板），成功后记录写入位置。
     * 任何失败返回失败状态，不抛异常。
     *
     * @param template  待保存模板（id 由调用方经 {@link #generateCustomTemplateId()} 生成）
     * @param overwrite 与现有自定义模板同名时是否覆盖（true=更新该名称模板全部字段并保持原 id）
     * @return 保存结果状态
     */
    public static SaveStatus saveTemplate(RuleTemplate template, boolean overwrite) {
        synchronized (LOCK) {
            Path file = getCustomTemplatesFile();
            SavePlan plan = planSave(file, template, overwrite);
            if (plan.status != SaveStatus.SUCCESS) {
                return plan.status;
            }
            SaveStatus status = writeTemplatesFile(file, plan.templates);
            if (status == SaveStatus.SUCCESS) {
                rememberWriteLocation(file);
                return status;
            }
            // 测试覆盖路径：单文件直写，不做运行时回退（避免测试写入真实用户主目录）
            if (customTemplatesFileOverride != null) {
                return status;
            }
            // 主位置写失败（只读/占用等运行时失败）→ 回退兜底位置：基于兜底现有列表重新合并，
            // 避免用主位置列表覆盖兜底已有模板（先前兜底模板不丢失）
            Path fallback = fallbackTemplatesFile();
            if (fallback.equals(file)) {
                return status; // 目标即兜底位置，避免重复写
            }
            LoggerUtil.logWarn("[模板库] 主位置写入失败，回退用户主目录: " + fallback);
            SavePlan fallbackPlan = planSave(fallback, template, overwrite);
            if (fallbackPlan.status != SaveStatus.SUCCESS) {
                return fallbackPlan.status;
            }
            SaveStatus fallbackStatus = writeTemplatesFile(fallback, fallbackPlan.templates);
            if (fallbackStatus == SaveStatus.SUCCESS) {
                rememberWriteLocation(fallback);
            }
            return fallbackStatus;
        }
    }

    /**
     * 删除自定义模板（§3.3/§3.8）：仅作用于自定义集合；内置 id 一律拒绝（内置只读保护）；
     * id 不存在返回 false。主位置写失败时回退兜底位置（基于兜底现有列表重新执行删除，
     * 不覆盖兜底已有模板），成功后记录写入位置。
     *
     * @param id 自定义模板 id
     * @return 是否删除成功
     */
    public static boolean deleteTemplate(String id) {
        synchronized (LOCK) {
            return applyDelete(getCustomTemplatesFile(), id, true);
        }
    }

    /**
     * 自定义模板改名（§3.3/§3.8）：仅作用于自定义集合，改名不换 id；
     * 新名非空且不与内置模板名/其他自定义模板名冲突；内置 id 拒绝；失败返回 false。
     * 主位置写失败时回退兜底位置（基于兜底现有列表重新执行改名），成功后记录写入位置。
     *
     * @param id      自定义模板 id
     * @param newName 新名称（非空、不冲突）
     * @return 是否改名成功
     */
    public static boolean renameTemplate(String id, String newName) {
        synchronized (LOCK) {
            return applyRename(getCustomTemplatesFile(), id, newName, true);
        }
    }

    /**
     * 生成自定义模板 id：{@code custom-<时间戳>-<序号>}，前缀与内置 id 天然隔离；
     * 同进程内序号单调递增保证唯一（跨进程碰撞概率可忽略；即使碰撞，planSave 也会
     * 在保存前做存在性检查并自动重新生成 id，见 §9.5）。
     *
     * @return 唯一自定义模板 id
     */
    public static String generateCustomTemplateId() {
        return "custom-" + System.currentTimeMillis() + "-" + Long.toHexString(ID_SEQ.incrementAndGet());
    }

    /**
     * 保存前校验（§3.7，GUI 与控制台两入口共用）：rule 序列化后经 MatchRuleLoader.fromJson
     * 非 null（strategyType 与链步骤均已在注册表，与模板库加载校验、writeRuleFile 自校验同款）。
     *
     * @param rule 组装好的规则
     * @return true=规则合法可保存
     */
    public static boolean isValidRule(MatchRule rule) {
        if (rule == null) {
            return false;
        }
        try {
            return MatchRuleLoader.fromJson(MAPPER.writeValueAsString(rule)) != null;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 用户自定义模板文件（§3.1 两级定位，读侧与写侧一致）：
     * 1. 主位置 {@code <工作目录>/templates/user-templates.json}（工作目录 = Paths.get(".") 规范化
     *    绝对路径，与外部 config.json 同目录；发布包场景启动脚本先 cd 到解压目录，用户可写）；
     * 2. 兜底位置 {@code <user.home>/.frt/templates/user-templates.json}（主位置不可用时）。
     * <p>
     * 解析顺序：测试覆盖路径 → 最近一次成功写入位置（文件仍存在时，写侧与读侧保持一致）→
     * 两级定位（主位置存在但不可写视为不可用，回退兜底位置，见 {@link #resolveCustomTemplatesFile}）。
     *
     * @return 解析后的用户模板文件路径
     */
    public static Path getCustomTemplatesFile() {
        Path override = customTemplatesFileOverride;
        if (override != null) {
            return override;
        }
        Path last = lastSuccessfulWrite;
        if (last != null && Files.isRegularFile(last)) {
            return last;
        }
        Path[] loc = customTemplatesLocationOverride;
        if (loc != null) {
            return resolveCustomTemplatesFile(loc[0], loc[1]);
        }
        return resolveCustomTemplatesFile(
                Paths.get(".").normalize().toAbsolutePath(),
                Paths.get(System.getProperty("user.home", ".")));
    }

    /**
     * 测试用：覆盖用户模板文件路径（参照 ConfigLoader.setBackupPathForTesting 先例）。
     * 测试必须用 @TempDir 注入临时路径，不得写真实工作目录/用户主目录。
     *
     * @param path 测试注入路径；传 null 恢复生产路径解析并清除写入位置记忆
     */
    public static void setCustomTemplatesFileForTesting(Path path) {
        customTemplatesFileOverride = path;
        if (path == null) {
            lastSuccessfulWrite = null; // 清除测试残留的写入位置记忆，避免泄漏到其他测试
        }
    }

    /**
     * 测试用：覆盖两级定位的工作目录/用户主目录（配合可写性探测注入，可端到端验证
     * "主位置只读 → 读侧/写侧均走兜底"的生产入口链路）。测试必须用 @TempDir 注入临时路径。
     *
     * @param workingDir 模拟工作目录；传 null 恢复生产路径解析
     * @param homeDir    模拟用户主目录；传 null 恢复生产路径解析
     */
    static void setCustomTemplatesLocationForTesting(Path workingDir, Path homeDir) {
        customTemplatesLocationOverride = (workingDir == null || homeDir == null) ? null
                : new Path[]{workingDir, homeDir};
    }

    /**
     * 测试用：可写性探测注入（默认 null=Files.isWritable；注入固定结果模拟"主位置只读"场景，
     * 因为 root 运行环境下 chmod 权限位对 Files.isWritable 无效）。测试结束后必须传 null 恢复。
     *
     * @param probe 可写性探测；传 null 恢复 Files.isWritable
     */
    static void setCustomTemplatesWritableProbeForTesting(Predicate<Path> probe) {
        writabilityProbeOverride = probe;
    }

    // ---------------- 包内钩子（供测试注入临时路径隔离污染） ----------------

    /**
     * 从指定文件加载自定义模板（文件缺失/损坏 → 空列表 + logWarn，不抛异常；逐条校验复用内置逻辑）。
     * 与写侧共用 LOCK 互斥：GUI/控制台保存/删除/改名写盘期间读侧等待，避免读到写了一半的文件。
     */
    static List<RuleTemplate> loadCustomFrom(Path file) {
        synchronized (LOCK) {
            if (file == null || !Files.isRegularFile(file)) {
                return Collections.emptyList(); // 文件缺失 = 无自定义模板（首次使用静默，不刷警告）
            }
            return loadFrom(file);
        }
    }

    /**
     * 把模板保存到指定文件（单文件直写，无回退；供测试注入临时路径做确定性断言）
     */
    static SaveStatus saveCustomTo(Path file, RuleTemplate template, boolean overwrite) {
        SavePlan plan = planSave(file, template, overwrite);
        if (plan.status != SaveStatus.SUCCESS) {
            return plan.status;
        }
        return writeTemplatesFile(file, plan.templates);
    }

    /**
     * 把模板列表按两级定位写回指定工作目录/主目录（主位置失败回退兜底位置；
     * 供测试注入临时路径模拟"主位置只读"场景）
     */
    static SaveStatus saveCustomTo(Path workingDir, Path homeDir, List<RuleTemplate> templates) {
        Path primary = workingDir.resolve("templates").resolve(USER_TEMPLATES_FILE);
        SaveStatus status = writeTemplatesFile(primary, templates);
        if (status == SaveStatus.SUCCESS) {
            return status;
        }
        Path fallback = homeDir.resolve(".frt").resolve("templates").resolve(USER_TEMPLATES_FILE);
        return writeTemplatesFile(fallback, templates);
    }

    /**
     * 从指定文件删除自定义模板（单文件直写，无回退；内置 id 拒绝；供测试注入临时路径）
     */
    static boolean deleteCustomFrom(Path file, String id) {
        return applyDelete(file, id, false);
    }

    /**
     * 在指定文件中给自定义模板改名（单文件直写，无回退；改名不换 id；供测试注入临时路径）
     */
    static boolean renameCustomIn(Path file, String id, String newName) {
        return applyRename(file, id, newName, false);
    }

    /**
     * 两级定位（§3.1，审查 round 2 修复项——读侧与写侧一致）：
     * 主位置可用（文件存在且可写，或目录可创建）→ 主位置；
     * 主位置存在但<b>不可写</b> → 视为不可用，回退兜底位置（避免"保存已回退兜底但加载仍读主位置"
     * 导致新保存的模板不可见、或覆盖兜底已有模板的数据不一致）；两级均不可用默认主位置
     * （写操作会返回失败状态，不抛异常）。
     */
    static Path resolveCustomTemplatesFile(Path workingDir, Path homeDir) {
        Path primary = workingDir.resolve("templates").resolve(USER_TEMPLATES_FILE);
        Path fallback = homeDir.resolve(".frt").resolve("templates").resolve(USER_TEMPLATES_FILE);
        if (isUsableCustomTemplatesFile(primary)) {
            return primary;
        }
        if (isUsableCustomTemplatesFile(fallback)) {
            return fallback;
        }
        return primary;
    }

    // ---------------- 私有实现 ----------------

    /** 保存计划：结果状态 + 待写列表（仅 SUCCESS 时有列表） */
    private static final class SavePlan {
        final SaveStatus status;
        final List<RuleTemplate> templates;

        private SavePlan(SaveStatus status, List<RuleTemplate> templates) {
            this.status = status;
            this.templates = templates;
        }

        static SavePlan ok(List<RuleTemplate> templates) {
            return new SavePlan(SaveStatus.SUCCESS, templates);
        }

        static SavePlan fail(SaveStatus status) {
            return new SavePlan(status, null);
        }
    }

    /**
     * 保存前的校验 + 合并（§3.3/§3.7，审查 round 2 修复项）：
     * ① 基础校验：id/name 非空、rule 非空且经 MatchRuleLoader 校验（保存前校验，失败不写文件）；
     * ② 内置只读保护：名称与内置模板重名 → 拒绝；
     * ③ id 冲突（§9.5）：传入 id 已存在于现有列表 → <b>自动重新生成 id 后重新合并</b>，
     *    绝不静默覆盖已有模板（"同 id 更新字段"语义已移除）；
     * ④ 同 name 不同 id → overwrite=false 返回 DUPLICATE_NAME，overwrite=true 更新该名称
     *    模板全部字段（保持原 id）；
     * ⑤ 追加新模板。
     */
    private static SavePlan planSave(Path file, RuleTemplate template, boolean overwrite) {
        if (template == null || template.getId() == null || template.getId().isBlank()
                || template.getName() == null || template.getName().isBlank()
                || !isValidRule(template.getRule())) {
            return SavePlan.fail(SaveStatus.INVALID_RULE);
        }
        if (isBuiltinName(template.getName())) {
            return SavePlan.fail(SaveStatus.BUILTIN_NAME_CONFLICT);
        }
        // 现有自定义列表（坏文件/缺失 → 空集重建，不阻塞保存）
        List<RuleTemplate> merged = safeLoad(file);
        // id 冲突处理：传入 id 已存在 → 重新生成 id（循环直至唯一），按新 id 继续合并
        String effectiveId = template.getId();
        while (containsId(merged, effectiveId)) {
            effectiveId = generateCustomTemplateId();
        }
        RuleTemplate effective = effectiveId.equals(template.getId()) ? template : withId(template, effectiveId);
        // 同 name 不同 id
        for (int i = 0; i < merged.size(); i++) {
            if (effective.getName().equals(merged.get(i).getName())) {
                if (!overwrite) {
                    return SavePlan.fail(SaveStatus.DUPLICATE_NAME);
                }
                // 覆盖：保持原 id（引用不失效），更新其余字段
                RuleTemplate updated = new RuleTemplate();
                updated.setId(merged.get(i).getId());
                updated.setName(effective.getName());
                updated.setCategory(effective.getCategory());
                updated.setDescription(effective.getDescription());
                updated.setRule(effective.getRule());
                merged.set(i, updated);
                return SavePlan.ok(merged);
            }
        }
        merged.add(effective);
        return SavePlan.ok(merged);
    }

    /** 列表是否已含指定 id */
    private static boolean containsId(List<RuleTemplate> list, String id) {
        for (RuleTemplate t : list) {
            if (id.equals(t.getId())) {
                return true;
            }
        }
        return false;
    }

    /** 复制模板全部字段并替换 id（id 冲突重新生成后使用） */
    private static RuleTemplate withId(RuleTemplate t, String id) {
        RuleTemplate copy = new RuleTemplate();
        copy.setId(id);
        copy.setName(t.getName());
        copy.setCategory(t.getCategory());
        copy.setDescription(t.getDescription());
        copy.setRule(t.getRule());
        return copy;
    }

    /**
     * 删除核心（审查 round 2 修复项）：内置 id 拒绝；id 不存在返回 false。
     * allowFallback=true 时主位置写失败回退兜底位置——基于兜底现有列表重新执行删除
     * （不覆盖兜底已有模板），成功后记录写入位置。
     */
    private static boolean applyDelete(Path file, String id, boolean allowFallback) {
        if (id == null || id.isBlank() || isBuiltinId(id)) {
            return false; // 内置只读保护
        }
        List<RuleTemplate> existing = safeLoad(file);
        List<RuleTemplate> remaining = new ArrayList<>();
        boolean removed = false;
        for (RuleTemplate t : existing) {
            if (id.equals(t.getId())) {
                removed = true;
            } else {
                remaining.add(t);
            }
        }
        if (!removed) {
            return false; // id 不存在
        }
        SaveStatus status = writeTemplatesFile(file, remaining);
        if (status == SaveStatus.SUCCESS) {
            rememberWriteLocation(file);
            return true;
        }
        if (!allowFallback || customTemplatesFileOverride != null) {
            return false; // 单文件钩子/测试覆盖路径：不做运行时回退
        }
        Path fallback = fallbackTemplatesFile();
        if (fallback.equals(file)) {
            return false; // 目标即兜底位置，避免重复写
        }
        LoggerUtil.logWarn("[模板库] 主位置写入失败，回退用户主目录: " + fallback);
        return applyDelete(fallback, id, false); // 基于兜底现有列表重新执行删除
    }

    /**
     * 改名核心（审查 round 2 修复项）：内置 id 拒绝；新名非空且不与内置/其他自定义重名；改名不换 id。
     * allowFallback=true 时主位置写失败回退兜底位置——基于兜底现有列表重新执行改名
     * （不覆盖兜底已有模板），成功后记录写入位置。
     */
    private static boolean applyRename(Path file, String id, String newName, boolean allowFallback) {
        if (id == null || id.isBlank() || newName == null || newName.isBlank()) {
            return false;
        }
        if (isBuiltinId(id)) {
            return false; // 内置只读保护
        }
        if (isBuiltinName(newName)) {
            return false; // 新名不得占用内置模板名
        }
        List<RuleTemplate> existing = safeLoad(file);
        // 新名不得与其他自定义模板重名（排除自身）
        for (RuleTemplate t : existing) {
            if (!id.equals(t.getId()) && newName.equals(t.getName())) {
                return false;
            }
        }
        List<RuleTemplate> renamed = new ArrayList<>();
        boolean found = false;
        for (RuleTemplate t : existing) {
            if (id.equals(t.getId())) {
                if (newName.equals(t.getName())) {
                    return true; // 名称未变化，视为成功（无需写回）
                }
                RuleTemplate copy = new RuleTemplate();
                copy.setId(t.getId()); // 改名不换 id
                copy.setName(newName);
                copy.setCategory(t.getCategory());
                copy.setDescription(t.getDescription());
                copy.setRule(t.getRule());
                renamed.add(copy);
                found = true;
            } else {
                renamed.add(t);
            }
        }
        if (!found) {
            return false;
        }
        SaveStatus status = writeTemplatesFile(file, renamed);
        if (status == SaveStatus.SUCCESS) {
            rememberWriteLocation(file);
            return true;
        }
        if (!allowFallback || customTemplatesFileOverride != null) {
            return false; // 单文件钩子/测试覆盖路径：不做运行时回退
        }
        Path fallback = fallbackTemplatesFile();
        if (fallback.equals(file)) {
            return false; // 目标即兜底位置，避免重复写
        }
        LoggerUtil.logWarn("[模板库] 主位置写入失败，回退用户主目录: " + fallback);
        return applyRename(fallback, id, newName, false); // 基于兜底现有列表重新执行改名
    }

    /** 兜底位置：<user.home>/.frt/templates/user-templates.json */
    private static Path fallbackTemplatesFile() {
        return Paths.get(System.getProperty("user.home", "."))
                .resolve(".frt").resolve("templates").resolve(USER_TEMPLATES_FILE);
    }

    /** 记录最近一次成功写入的位置（读侧解析优先使用，保证读侧与写侧一致） */
    private static void rememberWriteLocation(Path file) {
        lastSuccessfulWrite = file;
    }

    /**
     * 自定义模板文件位置可用性探测（审查 round 2 修复项）：
     * 文件已存在 → 必须是常规文件且可写（先 isRegularFile 再探测 Files.isWritable，
     * 存在但不可写视为不可用）；文件不存在 → 其父目录（或最近的已存在祖先目录）
     * 可写即可（保存时会自动创建 templates/）。
     */
    private static boolean isUsableCustomTemplatesFile(Path file) {
        if (file == null) {
            return false;
        }
        if (Files.isRegularFile(file)) {
            return isWritable(file);
        }
        Path parent = file.getParent();
        if (parent == null) {
            return false;
        }
        Path ancestor = parent;
        while (ancestor != null && !Files.exists(ancestor)) {
            ancestor = ancestor.getParent();
        }
        return ancestor != null && Files.isDirectory(ancestor) && isWritable(ancestor);
    }

    /** 可写性探测：默认 Files.isWritable；测试可注入固定结果模拟"主位置只读"场景 */
    private static boolean isWritable(Path path) {
        Predicate<Path> probe = writabilityProbeOverride;
        if (probe != null) {
            return probe.test(path);
        }
        return Files.isWritable(path);
    }

    /**
     * 把模板列表写入指定文件：顶层结构 {"templates":[...]} 与内置 rule-templates.json 同构；
     * templates/ 目录不存在自动创建；UTF-8 + 美化输出；失败返回 IO_ERROR 并 logWarn，不抛异常。
     * <p>
     * 写盘采用"临时文件 + 原子移动"（tmp → ATOMIC_MOVE）：崩溃/断电不会留下半截文件，
     * 读侧并发读要么看到旧完整文件要么看到新完整文件（不会读到截断 JSON——原直写
     * Files.writeString 在写中读会解析失败返回空列表，自定义模板列表瞬间"消失"）。
     * FAT32/exFAT 等不支持 ATOMIC_MOVE 的文件系统自动降级为普通 move。
     * 调用方（save/delete/rename）已持 LOCK，读侧 loadCustomFrom/findById 也以 LOCK 互斥。
     */
    private static SaveStatus writeTemplatesFile(Path file, List<RuleTemplate> templates) {
        if (file == null) {
            return SaveStatus.IO_ERROR;
        }
        Path tmpFile = null;
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            ObjectNode root = MAPPER.createObjectNode();
            ArrayNode arr = root.putArray("templates");
            for (RuleTemplate t : templates) {
                arr.add(MAPPER.valueToTree(t));
            }
            String json = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root);
            // 先写同目录临时文件，再原子移动（同目录保证 ATOMIC_MOVE 可用性）
            tmpFile = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmpFile, json, StandardCharsets.UTF_8);
            try {
                Files.move(tmpFile, file,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                // 不支持原子移动的文件系统：降级为普通 move（同目录 rename 基本安全）
                Files.move(tmpFile, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            return SaveStatus.SUCCESS;
        } catch (Exception e) {
            // 清理可能残留的临时文件
            if (tmpFile != null) {
                try {
                    Files.deleteIfExists(tmpFile);
                } catch (IOException ignored) {
                }
            }
            LoggerUtil.logWarn("[模板库] 写入自定义模板文件失败: " + file + " - " + e.getMessage());
            return SaveStatus.IO_ERROR;
        }
    }

    /** 读取现有自定义列表；文件缺失/损坏 → 空集重建（坏文件不阻塞后续保存）。
     *  与写侧共用 LOCK：写中读不会读到半截文件（配合 writeTemplatesFile 的原子移动） */
    private static List<RuleTemplate> safeLoad(Path file) {
        synchronized (LOCK) {
            if (file == null || !Files.isRegularFile(file)) {
                return new ArrayList<>();
            }
            try {
                return new ArrayList<>(loadFrom(file));
            } catch (Exception e) {
                LoggerUtil.logWarn("[模板库] 读取自定义模板失败，按空集重建: " + e.getMessage());
                return new ArrayList<>();
            }
        }
    }

    private static boolean isBuiltinId(String id) {
        for (RuleTemplate t : loadAll()) {
            if (id.equals(t.getId())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isBuiltinName(String name) {
        for (RuleTemplate t : loadAll()) {
            if (name.equals(t.getName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 从 classpath 读取 rule-templates.json（IDE 运行与打包 jar 均可用 getResourceAsStream）
     */
    private static List<RuleTemplate> loadFromClasspath() {
        try (InputStream is = RuleTemplateLibrary.class.getResourceAsStream(RESOURCE)) {
            if (is == null) {
                LoggerUtil.logWarn("[模板库] 未找到内置模板资源 " + RESOURCE + "，模板库为空");
                return Collections.emptyList();
            }
            return parse(is.readAllBytes());
        } catch (Exception e) {
            LoggerUtil.logWarn("[模板库] 读取内置模板失败: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 从指定路径加载模板（包内可见，供测试注入坏文件/临时文件验证容错；
     * 参照 ConfigLoader.saveThemeTo(Path,...) 的包内钩子先例）
     */
    static List<RuleTemplate> loadFrom(Path jsonPath) {
        try {
            return parse(Files.readAllBytes(jsonPath));
        } catch (IOException e) {
            LoggerUtil.logWarn("[模板库] 读取模板文件失败: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 解析模板 JSON 字节并逐条校验（共享实现，内置/自定义通用）：
     * 顶层结构 {"templates": [ {id,name,category,description,rule}, ... ]}；
     * rule 字段为 MatchRule 标准 JSON 形态。单条无效（缺 id/rule、rule 校验失败、
     * id 重复）→ 跳过并 logWarn，不拖垮整体。
     */
    private static List<RuleTemplate> parse(byte[] bytes) {
        try {
            String json = new String(bytes, StandardCharsets.UTF_8);
            if (json.startsWith("\uFEFF")) {
                json = json.substring(1); // 去 UTF-8 BOM，兼容带 BOM 的编辑器保存
            }
            JsonNode root = MAPPER.readTree(json);
            JsonNode templatesNode = root == null ? null : root.get("templates");
            if (templatesNode == null || !templatesNode.isArray()) {
                LoggerUtil.logWarn("[模板库] 模板文件缺少 templates 数组，模板库为空");
                return Collections.emptyList();
            }
            List<RuleTemplate> result = new ArrayList<>();
            Set<String> seenIds = new HashSet<>();
            for (JsonNode node : templatesNode) {
                try {
                    RuleTemplate t = MAPPER.treeToValue(node, RuleTemplate.class);
                    if (t == null || t.getRule() == null || t.getId() == null || t.getId().isBlank()) {
                        LoggerUtil.logWarn("[模板库] 跳过无效模板项（缺少 id 或 rule）: "
                                + (t == null || t.getId() == null ? "未命名" : t.getId()));
                        continue;
                    }
                    if (!seenIds.add(t.getId())) {
                        LoggerUtil.logWarn("[模板库] 跳过重复 id 模板项，保留第一条: " + t.getId());
                        continue;
                    }
                    // 逐条校验：rule 必须能被 MatchRuleLoader.fromJson 解析（策略注册表校验），
                    // 保证模板套用后生成的规则文件可被程序正常加载执行
                    String ruleJson = MAPPER.writeValueAsString(t.getRule());
                    if (MatchRuleLoader.fromJson(ruleJson) == null) {
                        LoggerUtil.logWarn("[模板库] 跳过无效模板 " + t.getId() + "：rule 无法通过规则解析校验");
                        continue;
                    }
                    result.add(t);
                } catch (Exception e) {
                    LoggerUtil.logWarn("[模板库] 跳过无效模板项: " + e.getMessage());
                }
            }
            if (result.isEmpty()) {
                LoggerUtil.logWarn("[模板库] 没有可用的有效模板");
            }
            return Collections.unmodifiableList(result);
        } catch (Exception e) {
            LoggerUtil.logWarn("[模板库] 模板文件解析失败: " + e.getMessage());
            return Collections.emptyList();
        }
    }
}

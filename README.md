# 多层级文件夹更新系统 (FRT)

基于 Java 17 的多层级文件夹更新系统：按规则文件（`matching-rules.json` 等）将**更新目录**的文件新增/替换到**目标目录**，或按**删除目录**匹配删除目标文件，全程自动备份、可恢复。特别适用于 Minecraft 模组管理等需要精细化文件操作的场景。

## 功能一览

| 功能 | 作用 |
|------|------|
| 更新文件 | 扫描更新目录，按规则对目标目录执行新增/替换，自动备份可恢复 |
| 删除文件 | 扫描删除目录，按规则匹配删除目标目录中对应的文件（带备份） |
| 恢复操作 | 从备份恢复最近一次操作前的状态；启动时检测到未完成会话（异常中断遗留）会提示恢复 |
| 规则生成 | 交互式向导生成/编辑规则文件（控制台逐步向导；UI 为表单弹窗，支持策略链） |
| 清理残留备份 | 删除备份目录中未被任何操作记录引用的文件（无记录保护的跳过），残留 ≥5 个时启动提醒 |
| 核心配置 | 设置更新/目标/删除/备份目录与日志级别，写入 config.json |
| 双界面 | 控制台菜单（7 项）或 Swing 图形界面（`--ui`）；更新/删除前均有 dry-run 预览二次确认 |

## 快速开始

```bash
mvn -o package -DskipTests            # 构建可执行 jar（首次可去掉 -o）
./start-frt.sh                        # Linux/macOS：控制台模式
./start-frt.sh --ui                   # Linux/macOS：图形界面模式
start-frt.bat --ui                    # Windows：图形界面模式
java -jar target/FRT-0.1.0-SNAPSHOT.jar --ui   # 直接运行 jar
```

要求 JDK 17+。跨平台注意：config.json 的 `baseDirectory` 若是 Windows 路径，在 Linux 上需改为对应绝对路径。

## 配置文件

### config.json（全局配置，均可省略）

| 参数 | 作用 | 默认值 |
|------|------|--------|
| `updatePath` | 更新文件目录 | `update` |
| `targetPath` | 目标处理目录 | `THtest` |
| `deletePath` | 删除文件目录 | `delete` |
| `backupPath` | 备份目录 | `backup` |
| `logLevel` | 日志级别（DEBUG/INFO/WARN/ERROR） | `INFO` |

相对路径基于 `baseDirectory` 解析；未知键（如 `logPath`）静默忽略，核心配置向导写入时保留。

### 规则文件（replace.json / add.json / delete.json / matching-rules.json，作用相同）

| 参数 | 说明 |
|------|------|
| `strategyType` | 策略类型（单策略必填；配置了 `strategyChain` 时忽略顶层该项） |
| `patterns` | 匹配模式列表（支持 `*`/`?` 通配符），留空表示匹配全部 |
| `excludePatterns` | 排除模式列表，命中则跳过 |
| `strategyChain` | 多策略组合链：依次执行各策略，后续策略只处理前序**剩余**（未被处理）的文件 |
| `inheritToSubfolders` | 规则是否继承到子文件夹（子层无本地规则时生效） |
| `replacements` | 策略额外参数（键值对），各策略支持项见下表 |

## 内置策略与额外参数（replacements）

> 通用规则：`patterns`/`excludePatterns` 支持 `*`/`?` 通配符；`caseSensitive=false` 忽略大小写。

### McMod —— Minecraft 模组策略（按 modId 匹配 jar）
- **作用**：以目录为单位，自动解析 jar 内模组元数据（兼容 NeoForge / Forge / Fabric / Quilt / 旧版 mcmod.info），按模组 **modId** 增/删/改；不识别元数据的 jar 跳过。`patterns`/`excludePatterns` **无效**。
- **额外参数**：
  | 参数 | 作用 |
  |------|------|
  | `onlyIfVersionChanged=true` | 目标已存在**相同版本**模组时跳过替换 |
  | `onlyIfContentSame=true` | 源与目标文件**内容（MD5）相同**时跳过替换（比版本判断更准，能识别同版本重新打包的变化） |

### FileSameName —— 同名文件策略（按文件名匹配）
- **作用**：按文件名/通配符匹配普通文件，执行新增/替换/删除。
- **额外参数**：
  | 参数 | 作用 |
  |------|------|
  | `caseSensitive=false` | 文件名匹配忽略大小写（默认区分） |
  | `onlyIfContentSame=true` | 替换时源与目标文件**内容（MD5）相同**则跳过（避免无谓写入，计入"跳过"） |

### ZipEntryName —— 压缩包内文件名匹配（zip/jar）
- **作用**：仅处理 .zip/.jar；打开包检查**内部条目名**，包内**任意一个**条目名匹配 `patterns`（且不匹配 `excludePatterns`）即命中，命中后整包参与操作（新增/替换/删除，**不解压**内部文件）。`patterns` 留空 = 匹配所有压缩包。例：`["META-INF/*.toml"]` 只处理含 mods.toml 的包。
- **额外参数**：
  | 参数 | 作用 |
  |------|------|
  | `caseSensitive=false` | 条目名匹配忽略大小写（默认区分） |

### ZipEntryContent —— 压缩包内文件内容匹配（zip/jar）
- **作用**：在 ZipEntryName 的基础上，还要求命中条目（大小 ≤1MB）的**文本内容**包含 `contentContains` 任一关键词。例：`patterns:["config/*.properties"]` + `contentContains:"port=8080"` 只处理包内配置写了 `port=8080` 的包。
- **额外参数**：
  | 参数 | 作用 |
  |------|------|
  | `contentContains=关键词1,关键词2` | **必填**；条目文本包含**任一**关键词即命中（英文逗号分隔多个） |
  | `caseSensitive=false` | 内容匹配忽略大小写（默认区分） |

### 压缩包策略选型
- `ZipEntryContent` 已内含条目名匹配条件，**与 `ZipEntryName` 二选一即可**：只看"包内有什么文件"用 `ZipEntryName`（快，不读内容）；须看"文件内容写了什么"用 `ZipEntryContent`（慢，读文本）。
- 所有 jar 无差别全收 → 用 `FileSameName` + `patterns:["*.jar"]` 即可，无需压缩包策略。
- 一条规则覆盖多种文件类型时，用 `strategyChain` 串多个策略，例如先 `McMod` 处理模组 jar、剩余文件交给 `ZipEntryName`。

## 规则继承机制

1. **本地优先**：每个文件夹优先使用自己的规则文件；
2. **自动继承**：文件夹无本地规则时继承父文件夹规则；
3. **多层支持**：任意层级的规则继承链；
4. **策略隔离**：不同策略类型的规则独立继承。

## 外部策略插件

按规范编写策略类打成 jar 放入程序工作目录的 `plugins/`，启动时自动加载，规则文件 `strategyType` 直接填插件类型标识；与内置类型冲突时插件被跳过（内置优先）。向导的策略列表会自动包含插件策略。

**推荐方式：继承 `AbstractOperationStrategy`**（模板方法已统一 null 校验、节点过滤与 add/replace/delete 分派，只需实现钩子）：

```java
package com.example;

import com.awei.frt.core.context.OperationContext;
import com.awei.frt.core.node.FileNode;
import com.awei.frt.core.strategy.AbstractOperationStrategy;
import com.awei.frt.core.uitls.FileUtil;
import com.awei.frt.model.OperationRecord;

import java.nio.file.Files;
import java.nio.file.Path;

/** 自定义策略：只处理 .dat 文件 */
public class MyStrategy extends AbstractOperationStrategy {
    @Override
    public String getStrategyType() { return "MyStrategy"; }   // 规则文件 strategyType 填这个

    @Override
    public String getDescription() { return "示例策略（按扩展名处理 .dat）"; }

    /** 节点筛选：决定哪些文件/目录进入本策略 */
    @Override
    protected boolean accepts(FileNode node, OperationContext context) {
        return !node.isDirectory() && node.getName().endsWith(".dat");
    }

    /** 新增钩子：返回 true = 已处理该节点（链中后续策略跳过） */
    @Override
    protected boolean doAdd(FileNode node, OperationContext context) {
        Path target = context.getTargetPath(node.getRelativePath()); // 目标位置
        if (Files.exists(target)) {
            return false;                       // 目标已存在，交给 replace 钩子
        }
        OperationRecord record = newRecord(context);                // 创建操作记录（已带策略类型；FileUtil 会自动写入操作类型）
        boolean ok = FileUtil.addFile(node.getPath(), target, record, context.isDryRun()); // 复制+备份+记录
        context.recordOperation(record);        // 提交记录（预览模式不落盘）
        if (ok) {
            node.setHandled(true);              // 标记已处理（策略链后续策略跳过）
        }
        return ok;
    }

    @Override
    protected boolean doReplace(FileNode node, OperationContext context) {
        Path target = context.getTargetPath(node.getRelativePath());
        if (!Files.exists(target)) {
            return false;
        }
        // 读取规则额外参数：context.getRuleParam("key")，如 {"caseSensitive": "false"}
        OperationRecord record = newRecord(context);
        boolean ok = FileUtil.replaceFile(node.getPath(), target, record, context.isDryRun());
        context.recordOperation(record);
        if (ok) {
            node.setHandled(true);
        }
        return ok;
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
        return ok;
    }
}
```

> 上述写法与内置策略（如 `ZipEntryBaseStrategy`）完全一致，可直接参考其源码；`accepts` 只做筛选，真正的文件操作统一走 `FileUtil` + `OperationRecord` 流程，即可自动获得备份、操作记录与会话恢复能力。

**简单方式：直接实现 `OperationStrategy` 接口**（需自行处理一切，适合极简场景）：

```java
public class MyStrategy implements OperationStrategy {
    public String getStrategyType() { return "MyStrategy"; }
    public String getDescription() { return "说明"; }
    public void execute(FileNode node, OperationContext context, String[] operationType) {
        // 自行判断操作类型（与 OperationContext.OPERATION_ADD / OPERATION_REPLACE / OPERATION_DELETE 常量比较）与节点筛选
    }
}
```

**方法参数对象参考**：

`FileNode node` —— 当前被处理的文件/目录节点：

| 方法 | 返回 | 作用 |
|------|------|------|
| `getPath()` | `Path` | 节点完整路径（源文件路径） |
| `getRelativePath()` | `String` | 相对路径，传给 `context.getTargetPath()` 计算目标位置 |
| `getName()` | `String` | 节点名称 |
| `isDirectory()` | `boolean` | 是否目录 |
| `isHandled()` / `setHandled(true)` | `boolean`/`void` | 策略链"已处理"标记：处理成功后 `setHandled(true)`，后续策略跳过该节点 |

`OperationContext context` —— 操作上下文：

| 方法 | 返回 | 作用 |
|------|------|------|
| `getTargetPath(relativePath)` | `Path` | 目标目录下对应路径（操作目标位置） |
| `getRuleParam(key)` | `String` | 读取规则文件 `replacements` 中的参数，未配置返回 `null` |
| `isDryRun()` | `boolean` | 是否预览模式（true 时不应真正改动文件；FileUtil 带 dryRun 参数的重载已自动处理） |
| `recordOperation(record)` | `void` | 记录一次操作（预览模式不落盘会话记录） |
| `getConfig()` | `Config` | 全局配置（目录/日志级别等） |
| `OPERATION_ADD` / `OPERATION_REPLACE` / `OPERATION_DELETE` | `String` 常量 | 操作类型值（`"operation_add"` 等），记录或判断操作类型用 |

`com.awei.frt.core.uitls.FileUtil` —— 文件操作工具（自动备份 + 写操作记录，三个方法均有带 `dryRun` 的重载）：

| 方法 | 返回 | 作用 |
|------|------|------|
| `addFile(source, target, record[, dryRun])` | `boolean` | 复制新增源文件到目标 |
| `replaceFile(source, target, record[, dryRun])` | `boolean` | 用源文件替换目标文件（自动备份） |
| `deleteFile(file, record[, dryRun])` | `boolean` | 删除目标文件（自动备份） |

> FileUtil 三个方法内部会自动写入 `record` 的操作类型（`OPERATION_ADD`/`OPERATION_REPLACE`/`OPERATION_DELETE`）与源/目标路径、MD5 特征码，无需手动设置。

`OperationRecord` —— 操作记录（继承 `AbstractOperationStrategy` 时用 `newRecord(context)` 创建，已填好策略类型；常规流程中 FileUtil 会自动填写操作类型/路径/MD5，**无需手动设置**）。手动场景可用 setter：`setOperationType(OperationContext.OPERATION_ADD 等)`、`setSourcePath`/`setTargetPath`、`setSuccess`、`setErrorMessage`；最后 `context.recordOperation(record)` 提交。

**加载方式**（启动时自动执行，无需额外配置）：

1. **classpath SPI**：读取应用 classpath 上的 `META-INF/services/com.awei.frt.core.strategy.OperationStrategy` 描述符（适合策略类与主程序同 classpath 部署）；
2. **plugins/ 目录 jar**（每个 jar 二选一）：
   - **标准 SPI**：jar 内提供 `META-INF/services/com.awei.frt.core.strategy.OperationStrategy` 文件，内容写策略类全限定名（如 `com.example.MyStrategy`，一行一个）；
   - **自动扫描**：jar 内无 services 文件时，自动扫描 jar 内所有实现 `OperationStrategy` 的具体类（需公开无参构造）。仅当 SPI 实际注册数为 0 时才启用该兜底。

> 注册时若 `strategyType` 与已注册类型（含内置策略）冲突，外部策略被跳过（内置优先），并输出告警。

## 测试

`mvn test`（surefire 3.2.5，JUnit5 真实运行）：当前 **58 个测试全绿**，覆盖策略注册表/模板方法/动态代理/多策略链/外部插件加载、压缩包策略、模组元数据解析、备份恢复/残留清理/会话记录、进度回调等。

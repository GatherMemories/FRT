# 多层级文件夹更新工具

基于 Java 17 的多层级文件夹更新工具：按规则文件（`matching-rules.json` 等）将**更新目录**的文件新增/替换到**目标目录**，或按**删除目录**匹配删除目标文件，全程自动备份、可恢复。特别适用于 Minecraft 模组管理等需要精细化文件操作的场景。

> 仓库：[https://github.com/GatherMemories/FRT](https://github.com/GatherMemories/FRT) ｜ 版本号自动取自 `pom.xml` 的 `<version>`（构建时注入），升级版本只需改 pom.xml 后重新打包，界面标题与状态栏、控制台启动横幅自动更新。

## 更新日志

| 版本 | 内容 |
|------|------|
| v0.1.14 | 修复 Windows 文字渲染粗细/浓淡不一（ClearType 亚像素抗锯齿） |
| v0.1.13 | 检查更新三层降级：默认信任库 → Windows 系统证书库 → 绕过证书校验（兼容 HTTPS 被安全软件/代理拦截的网络环境） |
| v0.1.12 | 检查更新证书校验失败时回退 Windows 系统证书库重试 |
| v0.1.11 | 修复发布包精简运行时缺 jdk.crypto.ec 导致 HTTPS 握手失败（检查更新不可用） |
| v0.1.10 | 移除平台外观切换，各平台样式统一（与 Linux 一致），仅保留字体优化 |
| v0.1.9 | Windows 日志区字体优先覆盖中文（更纱黑体/微软雅黑），不再回退宋体 |
| v0.1.8 | 便捷功能批次：菜单栏（文件/视图/帮助）、检查更新、一键打开目录、关于对话框、深浅主题切换；字体抗锯齿 + 平台字体（思源黑体/微软雅黑/苹方）；关于对话框布局重排 |
| v0.1.7 | 版本号自动取自 pom.xml + 界面/控制台显示 + 状态栏 GitHub 链接；修复备份恢复"备份文件不存在"（同内容去重索引悬空） |

> v0.1.8 之后以体验打磨与问题修复为主；重大新功能见下方[功能一览](#功能一览)与[开发路线图](开发路线图.md)。

## 功能一览

| 功能 | 作用 |
|------|------|
| 更新文件 | 扫描更新目录，按规则对目标目录执行新增/替换，自动备份可恢复 |
| 删除文件 | 扫描删除目录，按规则匹配删除目标目录中对应的文件（带备份） |
| 恢复操作 | 从备份恢复最近一次操作前的状态；启动时检测到未完成会话（异常中断遗留）会提示恢复 |
| 规则生成 | 交互式向导生成/编辑规则文件（控制台逐步向导；UI 为表单弹窗，支持策略链） |
| 清理残留备份 | 删除备份目录中未被任何操作记录引用的文件（无记录保护的跳过），残留 ≥5 个时启动提醒 |
| 核心配置 | 设置更新/目标/删除/备份目录与日志级别，写入 config.json |
| 打包插件 | 把 `plugins/` 目录下的 `.java` 策略源码一键编译打包成 jar（下次启动自动加载），无需命令行/IDE |
| 双界面 | Swing 图形界面（默认）或控制台菜单（8 项，`--console`）；更新/删除前均有 dry-run 预览二次确认；日志区按等级彩色显示（成功/失败/警告/固定等），顶部 A-/A+ 可调字体大小（10~24px，自动保存） |

## 快速开始

```bash
mvn -o package -DskipTests            # 构建可执行 jar（首次可去掉 -o）
./start-frt.sh                        # Linux/macOS：默认启动图形界面
./start-frt.sh --console              # Linux/macOS：切换控制台模式（-c 等价）
start-frt.bat                         # Windows：默认启动图形界面
start-frt.bat --console               # Windows：切换控制台模式（-c 等价）
java -jar target/FRT-*.jar --ui   # 直接运行 jar（图形界面）
java -jar target/FRT-*.jar        # 直接运行 jar（控制台）
```

要求 JDK 17+。跨平台注意：config.json 的 `baseDirectory` 若是 Windows 路径，在 Linux 上需改为对应绝对路径。

### 发布包（zip）首次使用

发布包内含 jar + 启动脚本 + `runtime/`（无 JDK 也能运行），**无需自带 config.json**（程序有内置默认配置，启动时自动创建 `update/THtest/delete/backup` 目录）。解压后：

1. 默认启动**图形界面**：双击 `start-frt.bat`（Windows）或运行 `./start-frt.sh`（Linux/macOS，若无可执行权限先 `chmod +x start-frt.sh`）；
2. 首次使用建议先点顶部"**核心配置**"按钮，把 更新/目标/删除/备份 目录设到实际位置；
3. 需要手动配置时，参照下方[配置文件](#配置文件)章节的 `config.json` 完整示例创建即可。

> 注意：图形界面模式下黑色控制台窗口保持空白是**正常现象**（日志显示在程序窗口内）；如需控制台菜单模式，运行 `start-frt.bat --console`（Linux：`./start-frt.sh --console`）。

## 无 JDK 环境运行（发布包自带精简运行时）

发布包内的 `runtime/` 是 **jlink 生成的精简版 Java 运行时**（约 86MB，仅为完整 JDK 的三分之一），启动脚本**优先使用它**，目标机器**无需安装 JDK**。

```
工具包/
├── runtime/            # 精简 Java 运行时（无 JDK 也能运行）
├── FRT-*.jar
├── start-frt.sh        # Linux/macOS 启动脚本
├── start-frt.bat       # Windows 启动脚本
├── config.json         # 配置模板
└── README.md
```

> **注意**：`runtime/` 与操作系统平台相关（Linux 的 runtime 不能在 Windows 用）。各平台发布包需在对应平台生成 runtime：

```bash
# 在有 JDK 17+ 的机器上（Windows 用户在 Windows 上执行，Linux 用户在 Linux 上执行）
jlink --add-modules java.base,java.desktop,java.naming,java.sql,jdk.unsupported,jdk.compiler \
      --strip-debug --no-header-files --no-man-pages \
      --output runtime
```

把生成的 `runtime/` 目录放进发布包即可；系统已装 JDK 时脚本自动回退用系统 java。

## 配置文件

### config.json（全局配置，均可省略）

| 参数 | 作用 | 默认值 |
|------|------|--------|
| `updatePath` | 更新文件目录 | `update` |
| `targetPath` | 目标处理目录 | `THtest` |
| `deletePath` | 删除文件目录 | `delete` |
| `backupPath` | 备份目录 | `backup` |
| `logLevel` | 日志级别（DEBUG/INFO/WARN/ERROR） | `INFO` |
| `maxBackupRecords` | 备份记录保留上限，超出自动淘汰最旧（固定 pinned 的记录除外） | `20` |

相对路径基于 `baseDirectory` 解析；未知键（如 `logPath`）静默忽略，核心配置向导写入时保留。

**config.json 完整示例**（放到程序根目录；省略则全部用默认值）：

```json
{
  "updatePath": "update",
  "targetPath": "THtest",
  "deletePath": "delete",
  "backupPath": "backup",
  "logLevel": "INFO"
}
```

> 程序根目录没有 `config.json` 时使用内置默认配置，启动时自动创建 `update/THtest/delete/backup` 目录；需要自定义路径时按上面示例创建即可（也可用顶部"核心配置"功能生成）。备份记录默认保留 20 条，超出自动删除最旧；恢复菜单里选备份后输入 `p` 可**固定**该备份（永久保留，不受数量淘汰影响，列表显示 `[固定]`）。

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

## 外部策略插件（自定义策略 · 小白教程）

内置策略不够用时，可以**自己写一个策略类**，打成 jar 放进程序根目录的 `plugins/` 文件夹，程序启动时自动加载。之后规则文件 `strategyType` 直接填你的策略标识即可，规则生成向导的策略列表也会自动出现它。

> 全程 4 步：**写代码 → 打包成 jar → 放进 plugins/ → 在规则里引用**。下面每一步都有可直接复制的内容。

### 第 1 步：写策略类（复制改改就能用）

新建 `MyStrategy.java` 文件（**文件名必须和类名一致**）。下面的示例实现"只处理 `.dat` 文件"：

```java
package com.example;

import com.awei.frt.core.context.OperationContext;
import com.awei.frt.core.node.FileNode;
import com.awei.frt.core.strategy.AbstractOperationStrategy;
import com.awei.frt.core.uitls.FileUtil;
import com.awei.frt.model.OperationRecord;
import com.awei.frt.util.LoggerUtil;

import java.nio.file.Files;
import java.nio.file.Path;

public class MyStrategy extends AbstractOperationStrategy {

    @Override
    public String getStrategyType() { return "MyStrategy"; }          // ① 唯一标识：规则文件 strategyType 填这个

    @Override
    public String getDescription() { return "按规则黑白名单处理文件"; }     // ② 中文说明（向导/日志显示，可省略）

    // ③ 筛选：返回 true 的文件才交给本策略
    //    用基类提供的 matchesRules() 按规则文件 patterns/excludePatterns 过滤（与内置策略同款）：
    //    patterns:["*.txt"] 只处理 txt；patterns 留空 = 匹配所有；excludePatterns 排除；caseSensitive=false 忽略大小写
    @Override
    protected boolean accepts(FileNode node, OperationContext context) {
        return !node.isDirectory() && matchesRules(node, context);
    }

    // ④ 新增：把更新目录的文件复制到目标目录
    @Override
    protected boolean doAdd(FileNode node, OperationContext context) {
        Path target = context.getTargetPath(node.getRelativePath()); // 目标位置（别用 node.getPath()！那是源文件位置）
        if (Files.exists(target)) {
            return false;   // 目标已存在 → 不新增，交给下面的 replace 钩子
        }
        OperationRecord record = newRecord(context);                  // 创建操作记录（自动带好你的策略类型）
        boolean ok = FileUtil.addFile(node.getPath(), target, record, context.isDryRun()); // 真正的复制动作
        context.recordOperation(record);                              // 提交记录（备份/恢复/统计全靠它）
        // 处理结果打日志（+ = 新增，预览模式不打，避免"预览就报成功"误解）
        if (!context.isDryRun()) {
            LoggerUtil.logInfo("+ " + node.getName() + " " + (ok ? "成功" : "失败"));
        }
        if (ok) {
            node.setHandled(true);                                    // 标记已处理：策略链后续步骤不再碰这个文件
        }
        return ok;
    }

    // ⑤ 替换：目标已有同名文件时用源文件覆盖（自动备份旧文件）
    @Override
    protected boolean doReplace(FileNode node, OperationContext context) {
        Path target = context.getTargetPath(node.getRelativePath());
        if (!Files.exists(target)) {
            return false;
        }
        OperationRecord record = newRecord(context);
        boolean ok = FileUtil.replaceFile(node.getPath(), target, record, context.isDryRun());
        context.recordOperation(record);                              // 提交记录（备份/恢复/统计全靠它）
        if (!context.isDryRun()) {
            LoggerUtil.logInfo("= " + node.getName() + " " + (ok ? "成功" : "失败"));
        }
        if (ok) {
            node.setHandled(true);
        }
        return ok;
    }

    // ⑥ 删除：删除目标目录里对应的文件（自动备份后删除）
    @Override
    protected boolean doDelete(FileNode node, OperationContext context) {
        Path target = context.getTargetPath(node.getRelativePath());
        OperationRecord record = newRecord(context);
        boolean ok = FileUtil.deleteFile(target, record, context.isDryRun());
        context.recordOperation(record);                              // 提交记录（备份/恢复/统计全靠它）
        if (!context.isDryRun()) {
            LoggerUtil.logInfo("- " + node.getName() + " " + (ok ? "成功" : "失败"));
        }
        if (ok) {
            node.setHandled(true);
        }
        return ok;
    }
}
```

**只想要"新增"？** 把 `doReplace`/`doDelete` 的方法体换成 `return false;` 即可（返回 false = 本策略不管这个操作）。

**各方法通俗解释**：

- `getStrategyType()` —— 策略身份证。返回值写进规则文件 `strategyType` 字段，**全程序唯一**（不能与内置策略/其他插件重名，否则被跳过）。
- `getDescription()` —— 中文说明，向导和日志展示用，不写也行。
- `accepts(...)` —— 过滤器。返回 `true` 的文件才进入本策略；**只做判断，不要在这里做文件操作**。想按规则文件的 `patterns`/`excludePatterns` 黑白名单过滤（与内置策略一致），一行调用基类的 `matchesRules(node, context)` 即可（空白名单=匹配所有、黑名单排除、`caseSensitive=false` 忽略大小写）——不需要自己实现通配符匹配。
- `doAdd / doReplace / doDelete(...)` —— 三个操作钩子：新增/替换/删除时被调用。返回 `true` = 已处理该节点（链中后续策略跳过）；返回 `false` = 未处理。
- `node` —— 当前文件节点（源文件）。`node.getPath()` 源路径；`node.getName()` 文件名；`node.getRelativePath()` 相对路径；`node.setHandled(true)` 标记已处理。
- `context` —— 操作上下文。`context.getTargetPath(相对路径)` 计算**目标位置**；`context.getRuleParam("key")` 读取规则 `replacements` 里的参数；`context.isDryRun()` 是否预览模式；`context.recordOperation(record)` 提交操作记录。
- `FileUtil` —— 真正的文件操作工具。三个方法**内部自动完成备份 + 写操作记录 + MD5 特征码**，务必用它而不是自己写 `Files.copy`（否则没有备份/恢复能力）：
  - `FileUtil.addFile(源, 目标, record[, dryRun])` —— 复制新增
  - `FileUtil.replaceFile(源, 目标, record[, dryRun])` —— 覆盖替换（自动备份旧文件）
  - `FileUtil.deleteFile(文件, record[, dryRun])` —— 删除（自动备份）

> 预览模式（dryRun）下 `FileUtil` 会自动"只校验不落盘"，所以上面代码直接透传 `context.isDryRun()` 即可，无需自己判断。

> **想看处理结果日志？** 处理完成后用 `LoggerUtil.logInfo("+ " + node.getName() + " " + (ok ? "成功" : "失败"))` 打印（`+` 新增 / `=` 替换 / `-` 删除，与内置策略一致；预览模式跳过不打印）。不打印的话文件照常处理，但界面日志区看不到结果——内置策略都有这行日志。

### 第 2 步：编译打包成 jar

**方法 A：程序内置"打包插件"按钮（推荐，无需命令行）**

程序界面上有 **"打包插件"** 按钮（控制台菜单：选 `7`），一键把 `plugins/` 目录下所有 `.java` 源码编译打包成 jar（多个文件互相引用也能一起打包），输出回 `plugins/`。打包成功日志提示"重启程序后自动加载生效"。需要完整 JDK 启动（发布包精简运行时已内置编译器模块，可直接用）。

**方法 B：命令行**（需要 JDK 17+；`FRT-*.jar` 换成你实际的 jar 文件名）：

```bash
javac -encoding UTF-8 -cp FRT-0.1.7-SNAPSHOT.jar -d out MyStrategy.java
jar --create --file my-strategy.jar -C out .
```

**方法 B：IDE 导出**（IDEA：File → Project Structure → Artifacts 新建 jar，Build → Build Artifacts 导出）。

打出来的 `my-strategy.jar` 里装的是编译后的 `.class` 文件（不是 `.java`）。

### 第 3 步：放进 plugins/ 并启动

1. 在程序根目录建 `plugins/` 文件夹（没有就新建）；
2. 把 `my-strategy.jar` 放进去；
3. 启动程序，日志出现 `[插件] 已加载策略插件: my-strategy.jar（1 个策略）` 即成功；规则生成向导的策略列表里也能看到"只处理 .dat 文件"。

### 第 4 步：在规则文件里使用

在更新目录放 `matching-rules.json`：

```json
{
  "strategyType": "MyStrategy",
  "replacements": { "suffix": ".copy" },
  "inheritToSubfolders": false
}
```

策略内用 `context.getRuleParam("suffix")` 就能读到 `".copy"` —— **想给插件传什么参数，都写在 `replacements` 里**，策略里用 `getRuleParam` 读。

### 进阶：直接在 execute 里写（简单方式）

不想继承模板类，也可以直接实现 `OperationStrategy` 接口，但 null 校验、操作类型分派、节点筛选全要自己写：

```java
public class MyStrategy implements OperationStrategy {
    public String getStrategyType() { return "MyStrategy"; }
    public String getDescription() { return "说明"; }
    public void execute(FileNode node, OperationContext context, String[] operationType) {
        // 自行判断操作类型（与 OperationContext.OPERATION_ADD / OPERATION_REPLACE / OPERATION_DELETE 常量比较）与节点筛选
    }
}
```

> 内置策略源码（`src/main/java/com/awei/frt/core/strategy/`）就是最好的参考，写法与上面完全一致。

**方法参数对象参考**（精确版）：

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

### 常见问题（避坑）

| 现象 | 原因 / 解决办法 |
|------|----------------|
| 日志"未在插件中找到策略实现" | jar 里没有实现 `OperationStrategy` 的类；确认类 `extends AbstractOperationStrategy` 或 `implements OperationStrategy` |
| 插件加载了，但规则报"策略类型不合法" | 规则 `strategyType` 与 `getStrategyType()` 返回值不一致（含大小写） |
| 插件被跳过，提示"策略类型已存在" | 与内置策略或其他插件重名了；换一个唯一标识 |
| 改了代码不生效 | 重新编译打包、替换 plugins/ 里的 jar，并**重启程序**（插件只在启动时加载） |
| 自动扫描注册不上 | 策略类必须是 `public`、文件名=类名、有公开无参构造 |
| 文件没备份/没操作记录 | 用了 `Files.copy` 等原生 API 绕过了 FileUtil；改用 `FileUtil.addFile/replaceFile/deleteFile` |
| 文件被复制到了错误位置 | 目标位置要用 `context.getTargetPath(node.getRelativePath())`，不要用 `node.getPath()` |

### 加载方式（程序启动时自动执行，无需配置）

1. **classpath SPI**：读取应用 classpath 上的 `META-INF/services/com.awei.frt.core.strategy.OperationStrategy` 描述符（适合策略类与主程序同 classpath 部署）；
2. **plugins/ 目录 jar**（每个 jar 二选一）：
   - **标准 SPI**：jar 内提供 `META-INF/services/com.awei.frt.core.strategy.OperationStrategy` 文件，内容写策略类全限定名（如 `com.example.MyStrategy`，一行一个）——**不写也可以**，程序会兜底自动扫描；
   - **自动扫描**：jar 内无 services 文件时，自动扫描 jar 内所有实现 `OperationStrategy` 的具体类（需公开无参构造）。仅当 SPI 实际注册数为 0 时才启用该兜底。

> 注册时若 `strategyType` 与已注册类型（含内置策略）冲突，外部策略被跳过（内置优先），并输出告警。

## 测试

`mvn test`（surefire 3.2.5，JUnit5 真实运行）：当前 **123 个测试全绿**，覆盖策略注册表/模板方法/动态代理/多策略链/外部插件加载（含读取与执行全面测试）、压缩包策略、模组元数据解析、备份恢复/残留清理/会话记录、进度回调等。

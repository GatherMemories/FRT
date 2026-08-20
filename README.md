# 多层级文件夹更新系统 (FRT)

## 系统概述

这是一个基于Java实现的多层级文件夹更新系统，专为处理复杂的文件更新场景设计。系统采用灵活的规则配置机制，支持多层级文件夹结构的规则继承，特别适用于Minecraft模组管理等需要精细化文件操作的应用场景。

## 核心特性

- **智能规则继承**：子文件夹自动继承父文件夹规则，本地规则优先
- **多策略支持**：内置 4 种策略（Minecraft 模组识别 / 同名文件 / 压缩包内文件名 / 压缩包内文件内容），支持 `strategyChain` 多策略组合与 `plugins/` 外部插件动态加载
- **操作类型完备**：支持替换、新增、删除三种文件操作模式
- **安全备份机制**：自动备份与恢复功能，确保操作安全；启动时检测未完成会话提示恢复、残留备份过多提醒清理
- **用户交互确认**：更新/删除/清理前均提供 dry-run 预览二次确认，避免误操作
- **双界面**：控制台菜单 + Swing 图形界面（`--ui`，含表单式规则生成向导、执行进度条）

## 系统架构

### 设计模式应用

系统采用多种设计模式构建，确保架构的灵活性和可扩展性：

1. **组合模式**：`FileNode`抽象基类统一管理文件和文件夹节点
2. **策略模式**：`OperationStrategy`接口定义统一的操作策略规范
3. **责任链模式**：`RuleInheritanceContext`实现多层级的规则继承
4. **工厂模式**：`StrategyFactory`统一创建策略对象

### 当前实现状态

#### 已实现的策略类
- **`McModStrategy`**：Minecraft模组文件处理策略
  - 自动识别.jar文件中的模组信息（自研 `ModMetadataParser` 解析）
  - 支持模组ID、版本号等元数据匹配
  - 兼容 NeoForge / Forge / Fabric / Quilt / 旧版Forge(mcmod.info) 平台
  - 版本占位符（如 `${file.jarVersion}`）自动兜底：MANIFEST.MF → 文件名
  - 智能处理Forge、Fabric等不同平台的模组

- **`FileSameNameStrategy`**：同名文件处理策略
  - 基于文件名进行文件匹配和操作
  - 支持通配符模式的文件筛选（统一 `GlobMatcher`，正则元字符安全转义）

- **`ZipEntryNameStrategy`**：压缩包内文件名匹配策略（类型 `ZipEntryName`）
  - 作用于 .zip/.jar 文件：压缩包内存在条目名匹配 `patterns` 即命中
  - 例：`patterns: ["META-INF/*.toml"]` 只处理含 mods.toml 的包；`patterns: ["*.class"]` 处理含 class 文件的包
  - 支持通配符与 `excludePatterns` 排除；空 patterns = 匹配所有压缩包
  - 可选 `replacements: {"caseSensitive": "false"}` 忽略条目名大小写（默认区分）

- **`ZipEntryContentStrategy`**：压缩包内文件内容匹配策略（类型 `ZipEntryContent`）
  - 读取 zip/jar 内条目文本，内容包含 `replacements.contentContains`（任一关键词）即命中
  - 例：`replacements: {"contentContains": "port=8080"}` 处理含该配置内容的包
  - 条目文本读取限制 1MB（防大二进制包拖慢扫描）
  - 可选 `replacements: {"caseSensitive": "false"}` 忽略大小写（默认区分）

> 策略基类 `AbstractOperationStrategy` 提供模板方法（统一 null/类型校验 + add/replace/delete 钩子分派），
> 新增策略只需实现三个钩子；`StrategyFactory` 采用**注册表**（策略类自报 `getStrategyType()`），
> 不再依赖枚举。动态代理 `StrategyProxy` 自动为每次策略执行提供日志、异常兜底与失败统计。
> 向导（规则生成）的策略列表会自动包含所有内置与外部插件策略。

### 压缩包策略：匹配机制与选型

`ZipEntryName` / `ZipEntryContent` 共用基类 `ZipEntryBaseStrategy`，与 `FileSameName` 的本质区别是：**前者只看文件名，后者会打开压缩包看"里面有什么"**——用于挑选"文件名一样但内容结构不同"的特定类型包。

**匹配流程**（以整个压缩包为操作单位）：

1. 仅处理 `.zip` / `.jar` 文件（目录跳过）；
2. 打开压缩包，遍历全部内部条目（`ZipEntry`）；
3. 判定（包内**任意一个**条目满足即整个包命中）：
   - `ZipEntryName`：条目名匹配 `patterns`（任一命中即可）且不匹配 `excludePatterns`；
   - `ZipEntryContent`：先过条目名 `patterns`（隐含 Name 的条件），再要求该条目**文本内容**包含 `contentContains` 任一关键词（每条目读取上限 1MB，超出视为不匹配）；
4. 命中后执行操作的对象是**压缩包文件本身**（整包新增/替换/删除到目标目录），**不会解压内部文件**。

**匹配效果示例**：

```jsonc
// 只处理含 mods.toml 的 jar（识别 Forge/NeoForge 模组包）
{"strategyType": "ZipEntryName", "patterns": ["META-INF/*.toml"]}
// 识别 Fabric 模组包
{"strategyType": "ZipEntryName", "patterns": ["fabric.mod.json"]}
// 按内容挑包：包内 config/*.properties 写了 port=8080 才处理
{"strategyType": "ZipEntryContent", "patterns": ["config/*.properties"],
 "replacements": {"contentContains": "port=8080"}}
```

**选型要点（是否需多策略搭配）**：

- `ZipEntryContent` 已内含 `ZipEntryName` 的名字匹配条件，**两者二选一即可，无需搭配**：只看"包内有什么文件"→ 用 `ZipEntryName`（快，不读内容）；必须看"文件内容写了什么"→ 用 `ZipEntryContent`（慢，读文本）。
- 所有 jar 无差别全收 → 直接用 `FileSameName` + `patterns: ["*.jar"]` 即可，不必用压缩包策略。
- 只有**一条规则要覆盖多种不同类型文件**时才需要 `strategyChain` 串多个策略（见下），例如先 `McMod` 处理模组 jar、剩余文件再交给 `ZipEntryName`。

## 配置文件参数说明

### 1. config.json - 系统全局配置

| 参数名 | 作用说明 | 是否必填 | 数据类型 | 默认值 | 示例 |
|--------|----------|------|----------|---------|------|
| `updatePath` | 更新文件目录 | 否    | String | `"update"` | `"./testDic/update"` |
| `targetPath` | 目标处理目录 | 否    | String | `"THtest"` | `"./testDic/THtest"` |
| `deletePath` | 删除文件目录 | 否    | String | `"delete"` | `"./testDic/delete"` |
| `backupPath` | 备份目录 | 否    | String | `"backup"` | `"./testDic/backup"` |
| `logLevel` | 日志级别 | 否    | String | `"INFO"` | `"DEBUG"`, `"INFO"`, `"WARN"`, `"ERROR"` |

### 2. 规则配置文件（replace.json / add.json / delete.json / matching-rules.json）

| 参数名 | 作用说明 | 是否必填  | 数据类型 | 默认值 | 示例 |
|--------|----------|-------|----------|---------|------|
| `strategyType` | **策略类型**（单策略时使用） | 是/否 | String | 无 | `"McMod"`, `"FileSameName"`, `"ZipEntryName"` |
| `patterns` | **匹配文件模式** | 否     | List\<String\> | 空列表 | `["*.jar"]`, `["*.txt", "*.doc"]` |
| `excludePatterns` | 排除文件模式 | 否     | List\<String\> | 空列表 | `["*backup*", "*Test*"]` |
| `strategyChain` | **多策略组合链**（可选）：依次执行各策略，后续策略只处理前序**剩余**的文件 | 否     | List\<规则对象\> | 空 | 见下方示例 |
| `inheritToSubfolders` | 是否应用到子文件夹 | 否     | Boolean | `false` | `true`, `false` |
| `replacements` | **策略扩展参数**（键值对，供策略读取自定义配置） | 否     | Map\<String, String\> | 空 Map | `{"onlyIfVersionChanged": "true"}` |

### 3. 重要说明

#### config.json 特点：
- 所有路径参数支持相对路径和绝对路径
- 相对路径会自动基于 `baseDirectory` 解析为绝对路径
- 所有参数都有默认值，配置文件可省略不写，实际使用**目标文件夹路径**必填
- 未知键（如示例中的 `logPath`）会被**静默忽略**（Jackson `FAIL_ON_UNKNOWN_PROPERTIES=false`），核心配置向导写入时也会保留这些未管理的键（合并写入）

#### 规则配置文件特点：
- **单策略**：`strategyType` 必填；配置了 `strategyChain` 时**忽略顶层** `strategyType`
- **策略类型说明**：
  - `"McMod"`：Minecraft模组文件处理策略（只检测jar文件，patterns、excludePatterns 参数无效）
  - `"FileSameName"`：同名文件处理策略
  - `"ZipEntryName"`：压缩包内文件名匹配策略（见上方选型说明）
  - `"ZipEntryContent"`：压缩包内文件内容匹配策略（见上方选型说明）
- **⚠️ 重要提醒**：`replacements` 是**策略扩展参数**（键值对），只对支持它的策略生效，未配置或策略不支持时无任何副作用。当前已支持：
  - `McMod` 策略：`{"onlyIfVersionChanged": "true"}` — 目标已存在**相同版本**的模组时跳过替换
  - `McMod` 策略：`{"onlyIfContentSame": "true"}` — 目标文件与源文件**内容（MD5）相同**时跳过替换（比版本判断更准确，能识别同版本重新打包的内容变化）
  - `FileSameName` 策略：`{"caseSensitive": "false"}` — 文件名/通配符匹配忽略大小写（默认区分）
  - `ZipEntryName` / `ZipEntryContent` 策略：`{"caseSensitive": "false"}` — 条目名/内容匹配忽略大小写（默认区分）
  - `ZipEntryContent` 策略：`{"contentContains": "关键词1,关键词2"}` — **必填**，条目文本包含任一关键词即命中（英文逗号分隔多个）

#### 规则文件命名规范（任选其一作用都是相同的）：
- `replace.json` - 文件替换操作规则
- `add.json` - 文件新增操作规则  
- `delete.json` - 文件删除操作规则
- `matching-rules.json` - 通用匹配规则（新版）

### 4. 配置示例

#### config.json 示例：
```json
{
   "updatePath": "./update", 
   "targetPath": "./target",
   "backupPath": "./backup",
   "deletePath": "./delete",
   "logLevel": "INFO"
}
```

#### matching-rules.json 示例（单策略）：
```json
{
  "strategyType": "McMod",
  "inheritToSubfolders": true
}
```

#### matching-rules.json 示例（多策略组合链）：
先处理 `*.txt`，剩余文件再交给第二个策略处理 `*.json`：
```json
{
  "strategyChain": [
    {"strategyType": "FileSameName", "patterns": ["*.txt"]},
    {"strategyType": "FileSameName", "patterns": ["*.json"]}
  ],
  "inheritToSubfolders": false
}
```
> 链中每个步骤都是独立的规则对象（可各自配置 patterns / excludePatterns / replacements）；
> 某个文件被前序策略**成功处理**后，后续策略自动跳过它（"剩余文件"语义）。
> 配置了 `strategyChain` 时忽略顶层 `strategyType`；也可在交互向导（菜单 4 / UI 规则生成）中选择"是否配置策略链"按提示生成。

#### matching-rules.json 示例（压缩包策略）：
只处理含 mods.toml 的模组 jar，其余 .jar 一律不处理：
```json
{
  "strategyType": "ZipEntryName",
  "patterns": ["META-INF/*.toml"],
  "inheritToSubfolders": false
}
```
按包内配置内容挑选（包内 `config/*.properties` 内容含 `port=8080` 才处理）：
```json
{
  "strategyType": "ZipEntryContent",
  "patterns": ["config/*.properties"],
  "replacements": {"contentContains": "port=8080"}
}
```

### 规则继承机制

系统采用智能的规则继承策略：

1. **本地优先**：每个文件夹优先使用自己的规则配置文件
2. **自动继承**：当文件夹没有本地规则时，自动继承父文件夹的规则
3. **多层支持**：支持任意层级的规则继承链
4. **策略隔离**：不同策略类型的规则独立继承

## 项目结构

```
src/main/java/com/awei/frt/
├── core/                                  # 核心框架层
│   ├── context/                           # 上下文管理
│   │   ├── OperationContext.java          # 操作上下文
│   │   ├── RuleInheritanceContext.java    # 规则继承上下文
│   │   └── ProgressCallback.java          # 进度回调（更新/删除进度条）
│   ├── node/                              # 文件节点
│   │   ├── FileNode.java                  # 文件节点抽象基类
│   │   ├── FolderNode.java                # 文件夹节点
│   │   └── FileLeaf.java                  # 文件叶子节点
│   ├── strategy/                          # 策略实现层
│   │   ├── OperationStrategy.java         # 策略接口（策略自报 getStrategyType）
│   │   ├── AbstractOperationStrategy.java # 模板方法基类（add/replace/delete 钩子）
│   │   ├── McModStrategy.java             # Minecraft模组策略
│   │   ├── FileSameNameStrategy.java      # 同名文件策略
│   │   ├── ZipEntryBaseStrategy.java      # 压缩包策略基类（zip/jar，内部命中判定）
│   │   ├── ZipEntryNameStrategy.java      # 压缩包内文件名匹配策略
│   │   ├── ZipEntryContentStrategy.java   # 压缩包内文件内容匹配策略
│   │   └── StrategyProxy.java             # 策略动态代理（日志/异常兜底/统计）
│   ├── mod/                               # 模组元数据（自研解析，替代第三方库）
│   │   ├── ModInfo.java                   # 模组元数据模型
│   │   └── ModMetadataParser.java         # 模组元数据解析器
│   ├── uitls/                             # 工具类
│   │   ├── GlobMatcher.java               # 通配符匹配（统一 * ? 语法）
│   │   ├── FileUtil.java                  # 文件操作工具（add/replace/delete）
│   │   └── FileSignUtil.java              # 文件签名（MD5 等）
│   └── builder/                           # 构建器
│       ├── FileTreeBuilder.java           # 文件树构建
│       ├── MatchRuleLoader.java           # 规则加载
│       ├── BackupFileLoader.java          # 备份加载/恢复/残留清理/会话记录
│       └── ConfigLoader.java              # 配置加载（外部 config.json 优先）
├── factory/                               # 策略工厂 + 外部插件加载
│   ├── StrategyFactory.java               # 策略注册表（取代旧枚举）
│   └── StrategyLoader.java                # 外部策略动态加载（plugins/ + SPI）
├── service/                               # 业务服务层
│   ├── FileUpdateServiceNew.java          # 文件更新服务（支持进度回调）
│   ├── FileDeleteService.java             # 文件删除服务（支持进度回调）
│   ├── RestoreService.java                # 恢复服务
│   ├── RuleConfigWizard.java              # 规则配置向导（预览/写入/自校验公共流程）
│   └── CoreConfigWizard.java              # 核心配置向导（合并写入 config.json）
├── model/                                 # 数据模型层
│   ├── Config.java                        # 配置模型
│   ├── MatchRule.java                     # 匹配规则模型（支持 strategyChain）
│   ├── OperationRecord.java               # 操作记录模型
│   ├── ProcessingResult.java              # 处理结果模型
│   └── RestoreResult.java                 # 恢复结果模型
├── ui/                                    # 图形界面（--ui 启动）
│   ├── MainUI.java                        # UI 入口
│   ├── FRTFrame.java                      # 主窗口（顶部按钮+日志区+底部输入框+状态栏+进度条）
│   ├── UITheme.java                       # 全局主题（字体/颜色/间距）
│   ├── RuleWizardForm.java                # 规则生成表单（模态弹窗，含策略链步骤增删）
│   ├── ConfigFormDialog.java              # 核心配置表单（模态弹窗）
│   ├── UserPrompter.java                  # 服务层交互抽象（控制台/对话框/窗口内输入）
│   ├── ConsoleUserPrompter.java           # 控制台实现
│   └── SwingPrompter.java                 # Swing 实现
├── constants/                             # 常量定义（RulesConstants）
├── exception/                             # 异常体系（FRTException 等）
└── util/                                  # LoggerUtil（slf4j + logback）/ PreviewUtil
```

## 使用指南

### 快速开始

1. **准备配置文件**：在目标目录创建相应的规则配置文件（或使用交互式向导生成，见下）
2. **启动系统**（四选一）：

```bash
# 方式一：Maven 直接运行（开发调试）
mvn compile exec:java -Dexec.mainClass="com.awei.frt.Main"

# 方式二：Linux / macOS 启动脚本（自动检查 jar 与 JDK 版本，透传参数）
./start-frt.sh            # 控制台模式
./start-frt.sh --ui       # 图形界面模式

# 方式三：Windows 启动脚本
start-frt.bat --ui

# 方式四：直接运行打包好的可执行 jar
java -jar target/FRT-0.1.0-SNAPSHOT.jar --ui
```

> 启动脚本要求 JDK 17+（实测 21 可用）；若 `target/FRT-0.1.0-SNAPSHOT.jar` 不存在，先执行 `mvn -o package -DskipTests` 构建（首次缺依赖可去掉 `-o` 联网下载）。
> 跨平台注意：`config.json` 里的 `baseDirectory` 若为 Windows 路径（如 `C:/Users/...`），在 Linux 上需改为对应的绝对路径。

### 控制台菜单

启动后提供 7 个菜单项：**1. 更新文件 / 2. 删除文件 / 3. 执行恢复操作 / 4. 规则生成 / 5. 清理残留备份 / 6. 核心配置 / 7. 退出**。
启动时还会：检测未完成的操作会话（上次异常中断遗留）提示是否恢复、残留备份 ≥5 个时提醒清理（不阻塞启动）。

### 图形界面（Swing）

支持图形界面启动（`--ui`，功能与控制台一致，交互改为窗口内完成，不再弹窗打断滚轮浏览）：

```bash
./start-frt.sh --ui    # 或 java -jar FRT-0.1.0-SNAPSHOT.jar --ui
```

主窗口布局：
- **顶部**：功能按钮 更新文件 / 删除文件 / 恢复备份 / 规则生成 / 清理残留备份 / 核心配置 / 清空日志
- **中部**：实时日志区（双写控制台 + 窗口，长内容可滚动）
- **底部**：**固定输入框 + 快捷选项按钮**（按提示自动生成 是/否、1-N 数字、0/-1 等快捷按钮，仅等待输入时出现）+ **最底部状态栏**
- **进度条**：更新/删除真实执行阶段显示 `已处理/总数 + 当前文件`

> **更新/删除均带预览二次确认**：点击后先列出将执行的 [+]新增 / [=]替换 / [-]删除 计划，确认后才真正执行；预览阶段仅模拟（静默日志 + cancelled 标记），不会实际操作。
> **规则生成使用表单式模态弹窗**：作用目录 / 目标文件夹下拉 + 主策略参数 + 策略链步骤可增删，一次填完所有参数，JSON 预览/写入复用控制台向导的公共流程。
> **核心配置使用表单式弹窗**：设置更新/目标/删除/备份目录与日志级别，保存走公共流程（预览/确认/自动创建缺失目录/写入/自校验）。

### 备份清理

主菜单选择 **5. 清理残留备份**（或 UI 对应按钮）：扫描 `backup/` 下未被任何操作记录引用的备份文件（记录被删但备份残留、手工放入、异常中断残留），列表展示并确认后删除，避免备份目录无限膨胀。
- **无记录保护**：备份文件若没有任何备份记录可参照，会被跳过删除（防止误删恢复所需的文件）；
- 启动时检测到残留备份 ≥5 个会提示清理（不阻塞启动）。

### 交互式生成规则配置文件

主菜单选择 **4. 规则生成**（或 UI 的"规则生成"按钮），有两种方式：

**控制台逐步向导**：
1. 选择规则作用目录（更新目录 / 删除目录）
2. 显示目录文件结构图，输入**文件夹编号**选择要在哪一层生成规则
3. 逐个输入参数，每个参数均提示**数据类型、必填性、默认值、可选值**
4. 生成前**预览 JSON 内容**，确认后写入 `matching-rules.json`，并自动校验格式

**UI 表单向导**（`RuleWizardForm` 模态弹窗）：作用目录下拉 → 目标文件夹下拉（文件树）→ 主策略参数（类型/patterns/excludePatterns/继承开关/replacements）→ 策略链步骤可增删，一次填完，确定后走同一公共写入流程。

向导会提示：已存在规则文件的层、McMod 策略下不生效的参数（`patterns`/`excludePatterns`）、策略扩展参数示例等。

### 目录结构示例

```
项目根目录/
├── config.json              # 全局配置（可选）
├── plugins/                 # 外部策略插件目录（可选，放策略 jar 自动加载）
├── update/                  # 更新文件目录
│   ├── matching-rules.json         # 根级替换规则（使用mcmod策略）
│   ├── mod1.jar             # Minecraft模组文件
│   └── subfolder/
│       ├── matching-rules.json         # 子目录新增规则（使用filesame策略）
│       ├── file2.new        # 新增文件
│       └── subsubfolder/    # 无本地规则，继承父目录规则（需要父目录规则参数 inheritToSubfolders：true）
│           └── file3.class   # 继承处理
├── target/                  # 目标处理目录
├── backup/                  # 自动备份目录
└── logs/                    # 操作日志目录
```

## 扩展性设计

### 策略扩展
系统采用策略模式 + 注册表设计，新增策略有两种方式：

**方式一：源码内注册（内置策略）**
1. 实现 `OperationStrategy` 接口（推荐继承 `AbstractOperationStrategy` 模板基类，只实现 add/replace/delete 三个钩子）
2. 类内声明 `getStrategyType()` 返回唯一类型标识
3. 在 `StrategyFactory` 静态块中 `register("类型", 类::new, "说明")`

**方式二：外部策略插件（无需改源码，动态加载）**
1. 按上述规范编写策略类，打成 jar 放入程序工作目录的 `plugins/` 文件夹
2. 加载方式二选一：
   - 标准 SPI：jar 内提供 `META-INF/services/com.awei.frt.core.strategy.OperationStrategy` 文件，内容为策略类全限定名
   - 自动扫描：未提供 services 文件时，自动扫描 jar 内所有实现 `OperationStrategy` 的具体类（需公开无参构造）
3. 启动程序即可，规则文件的 `strategyType` 直接填插件策略的类型标识；类型与内置策略冲突时插件会被跳过（内置优先）

### 规则扩展
规则模型采用灵活的JSON配置，支持：
- 新增规则参数（通过 `replacements` 键值对给策略传参）
- 自定义策略配置
- 动态规则加载

## 测试与验证

系统包含完整的 JUnit5 测试用例（`mvn test`，surefire 3.2.5 保证 JUnit5 真实运行；当前 **58 个测试全绿**）：
- 策略注册表 / 模板方法 / 动态代理 / 多策略链 / 外部插件加载测试
- 压缩包策略测试（ZipEntryName / ZipEntryContent 命中与整包操作）
- 模组元数据解析测试（NeoForge/Forge/Fabric/Quilt/旧版 + 版本占位符兜底）
- 备份恢复、残留清理、会话记录、核心配置合并写入测试
- 进度回调语义测试（进度总数=文件树文件数，`inheritToSubfolders=false` 时未处理子层文件不计数，属预期语义）

## 总结

FRT系统通过精心设计的架构和灵活的规则配置机制，为复杂的文件更新场景提供了强大的解决方案。系统当前专注于Minecraft模组管理和通用文件操作，同时为未来的功能扩展预留了充分的空间。

**特别提醒**：`replacements`参数是策略扩展参数（键值对），仅对支持它的策略生效；新增策略时可通过 `OperationContext.getRuleParam(key)` 读取，实现自定义配置。

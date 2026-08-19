# 多层级文件夹更新系统 (FRT)

## 系统概述

这是一个基于Java实现的多层级文件夹更新系统，专为处理复杂的文件更新场景设计。系统采用灵活的规则配置机制，支持多层级文件夹结构的规则继承，特别适用于Minecraft模组管理等需要精细化文件操作的应用场景。

## 核心特性

- **智能规则继承**：子文件夹自动继承父文件夹规则，本地规则优先
- **多策略支持**：支持多种文件处理策略，包括Minecraft模组识别、同名文件处理等
- **操作类型完备**：支持替换、新增、删除三种文件操作模式
- **安全备份机制**：自动备份与恢复功能，确保操作安全
- **用户交互确认**：关键操作前进行用户确认，避免误操作

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

- **`ZipEntryContentStrategy`**：压缩包内文件内容匹配策略（类型 `ZipEntryContent`）
  - 读取 zip/jar 内条目文本，内容包含 `replacements.contentContains`（任一关键词）即命中
  - 例：`replacements: {"contentContains": "port=8080"}` 处理含该配置内容的包
  - 条目文本读取限制 1MB（防大二进制包拖慢扫描）

> 策略基类 `AbstractOperationStrategy` 提供模板方法（统一 null/类型校验 + add/replace/delete 钩子分派），
> 新增策略只需实现三个钩子；`StrategyFactory` 采用**注册表**（策略类自报 `getStrategyType()`），
> 不再依赖枚举。动态代理 `StrategyProxy` 自动为每次策略执行提供日志、异常兜底与失败统计。
> 向导（规则生成）的策略列表会自动包含所有内置与外部插件策略。

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
| `strategyType` | **策略类型**（单策略时使用） | 是/否 | String | 无 | `"McMod"`, `"FileSameName"` |
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

#### 规则配置文件特点：
- **必填参数**：`strategyType`（策略类型）
- **策略类型说明**：
  - `"McMod"`：Minecraft模组文件处理策略（只检测jar文件，patterns、excludePatterns 参数无效）
  - `"FileSameName"`：同名文件处理策略
- **⚠️ 重要提醒**：`replacements` 是**策略扩展参数**（键值对），只对支持它的策略生效，未配置或策略不支持时无任何副作用。当前已支持：
  - `McMod` 策略：`{"onlyIfVersionChanged": "true"}` — 目标已存在**相同版本**的模组时跳过替换
  - `McMod` 策略：`{"onlyIfContentSame": "true"}` — 目标文件与源文件**内容（MD5）相同**时跳过替换（比版本判断更准确，能识别同版本重新打包的内容变化）
  - `FileSameName` 策略：`{"caseSensitive": "false"}` — 文件名/通配符匹配忽略大小写（默认区分）

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
> 配置了 `strategyChain` 时忽略顶层 `strategyType`；也可在交互向导（菜单 4）中选择"是否配置策略链"按提示生成。

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
│   │   └── RuleInheritanceContext.java    # 规则继承上下文
│   ├── node/                              # 文件节点
│   │   ├── FileNode.java                  # 文件节点抽象基类
│   │   ├── FolderNode.java                # 文件夹节点
│   │   └── FileLeaf.java                  # 文件叶子节点
│   ├── strategy/                          # 策略实现层
│   │   ├── OperationStrategy.java         # 策略接口（策略自报 getStrategyType）
│   │   ├── AbstractOperationStrategy.java # 模板方法基类（add/replace/delete 钩子）
│   │   ├── McModStrategy.java             # Minecraft模组策略
│   │   ├── FileSameNameStrategy.java      # 同名文件策略
│   │   └── StrategyProxy.java             # 策略动态代理（日志/异常兜底/统计）
│   ├── mod/                               # 模组元数据（自研解析，替代第三方库）
│   │   ├── ModInfo.java                   # 模组元数据模型
│   │   └── ModMetadataParser.java         # 模组元数据解析器
│   ├── uitls/                             # 工具类（GlobMatcher / FileUtil / FileSignUtil）
│   └── builder/                           # 构建器（文件树/规则加载/备份/配置）
├── factory/                               # 策略工厂 + 外部插件加载
│   ├── StrategyFactory.java               # 策略注册表（取代旧枚举）
│   └── StrategyLoader.java                # 外部策略动态加载（plugins/ + SPI）
├── service/                               # 业务服务层
│   ├── FileUpdateServiceNew.java          # 文件更新服务
│   ├── FileDeleteService.java             # 文件删除服务
│   ├── RestoreService.java                # 恢复服务
│   └── RuleConfigWizard.java              # 规则配置交互向导（支持策略链）
├── model/                                 # 数据模型层
│   ├── Config.java                        # 配置模型
│   ├── MatchRule.java                     # 匹配规则模型（支持 strategyChain）
│   ├── OperationRecord.java               # 操作记录模型
│   └── ProcessingResult.java              # 处理结果模型
└── util/                                  # LoggerUtil（slf4j + logback）
```

## 使用指南

### 快速开始

1. **准备配置文件**：在目标目录创建相应的规则配置文件（或使用交互式向导生成，见下）
2. **启动系统**：运行以下命令启动文件处理流程

```bash
mvn compile exec:java -Dexec.mainClass="com.awei.frt.Main"
```

### 图形界面（Swing，基础版）

支持图形界面启动（功能与控制台一致，交互确认改为对话框）：

```bash
java -jar FRT-0.1.0-SNAPSHOT.jar --ui
```

主窗口提供 更新文件 / 删除文件 / 恢复备份 / 规则生成 / 清理残留备份 五个按钮与实时日志区。

> **更新/删除均带预览二次确认**：点击后先列出将执行的 [+]新增 / [=]替换 / [-]删除 计划，确认后才真正执行。

### 备份清理

主菜单选择 **5. 清理残留备份**：扫描 `backup/` 下未被任何操作记录引用的备份文件（记录被删但备份残留、手工放入、异常中断残留），列表展示并确认后删除，避免备份目录无限膨胀。

### 交互式生成规则配置文件

主菜单选择 **4. 规则生成**（生成/编辑匹配规则配置文件），向导会：

1. 选择规则作用目录（更新目录 / 删除目录）
2. 显示目录文件结构图，输入**文件夹编号**选择要在哪一层生成规则
3. 逐个输入参数，每个参数均提示**数据类型、必填性、默认值、可选值**
4. 生成前**预览 JSON 内容**，确认后写入 `matching-rules.json`，并自动校验格式

向导会提示：已存在规则文件的层、McMod 策略下不生效的参数（`patterns`/`excludePatterns`）、策略扩展参数示例等。

### 目录结构示例

```
项目根目录/
├── config.json              # 全局配置（可选）
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

系统包含完整的 JUnit5 测试用例（`mvn test`，surefire 3.2.5 保证真实运行）：
- 策略注册表 / 模板方法 / 动态代理 / 多策略链 / 外部插件加载测试
- 模组元数据解析测试（NeoForge/Forge/Fabric/Quilt/旧版 + 版本占位符兜底）
- 备份恢复与规则链集成测试

## 总结

FRT系统通过精心设计的架构和灵活的规则配置机制，为复杂的文件更新场景提供了强大的解决方案。系统当前专注于Minecraft模组管理和通用文件操作，同时为未来的功能扩展预留了充分的空间。

**特别提醒**：`replacements`参数是策略扩展参数（键值对），仅对支持它的策略生效；新增策略时可通过 `OperationContext.getRuleParam(key)` 读取，实现自定义配置。

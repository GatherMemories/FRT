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
  - 自动识别.jar文件中的模组信息
  - 支持模组ID、版本号等元数据匹配
  - 智能处理Forge、Fabric等不同平台的模组

- **`FileSameNameStrategy`**：同名文件处理策略
  - 基于文件名进行文件匹配和操作
  - 支持通配符模式的文件筛选

#### 待实现的策略类
- `ZipFileContentStrategy`：ZIP文件内容处理策略（规划中）
- `ZipFileNameStrategy`：ZIP文件名处理策略（规划中）

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
| `strategyType` | **策略类型** | **是** | String | 无 | `"McMod"`, `"FileSameName"` |
| `patterns` | **匹配文件模式** | 否     | List\<String\> | 空列表 | `["*.jar"]`, `["*.txt", "*.doc"]` |
| `excludePatterns` | 排除文件模式 | 否     | List\<String\> | 空列表 | `["*backup*", "*Test*"]` |
| `inheritToSubfolders` | 是否应用到子文件夹 | 否     | Boolean | `false` | `true`, `false` |
| `replacements` | **预留替换项**（⚠️ **当前未使用**） | 否     | List\<String\> | 空列表 | `[]`（请勿依赖此参数） |

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
- **⚠️ 重要提醒**：`replacements` 参数是预留字段，**当前版本所有策略类均未使用此参数**，仅作为未来扩展预留

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

#### matching-rules.json 示例：
```json
{
  "strategyType": "McMod",
  "inheritToSubfolders": true
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
│   │   └── RuleInheritanceContext.java    # 规则继承上下文
│   ├── node/                              # 文件节点
│   │   ├── FileNode.java                  # 文件节点抽象基类
│   │   ├── FolderNode.java                # 文件夹节点
│   │   └── FileLeaf.java                  # 文件叶子节点
│   ├── strategy/                          # 策略实现层
│   │   ├── OperationStrategy.java         # 策略接口
│   │   ├── McModStrategy.java             # Minecraft模组策略
│   │   └── FileSameNameStrategy.java      # 同名文件策略
│   └── builder/                           # 构建器
│       └── FileTreeBuilder.java           # 文件树构建器
├── service/                               # 业务服务层
│   ├── FileReplaceServiceNew.java         # 文件替换服务
│   └── RestoreService.java                # 恢复服务
├── model/                                 # 数据模型层
│   ├── Config.java                        # 配置模型
│   ├── MatchRule.java                     # 匹配规则模型
│   ├── OperationRecord.java               # 操作记录模型
│   └── ProcessingResult.java              # 处理结果模型
└── utils/                                 # 工具类层
    ├── ConfigLoader.java                  # 配置加载器
    └── FileUtils.java                     # 文件工具类
```

## 使用指南

### 快速开始

1. **准备配置文件**：在目标目录创建相应的规则配置文件
2. **启动系统**：运行以下命令启动文件处理流程

```bash
mvn compile exec:java -Dexec.mainClass="com.awei.frt.Main"
```

### 目录结构示例

```
项目根目录/
├── config.json              # 全局配置（可选）
├── update/                  # 更新文件目录
│   ├── replace.json         # 根级替换规则（使用mcmod策略）
│   ├── mod1.jar             # Minecraft模组文件
│   └── subfolder/
│       ├── add.json         # 子目录新增规则（使用filesame策略）
│       ├── file2.new        # 新增文件
│       └── subsubfolder/    # 无本地规则，继承父目录规则
│           └── file3.class   # 继承处理
├── target/                  # 目标处理目录
├── backup/                  # 自动备份目录
└── logs/                    # 操作日志目录
```

## 扩展性设计

### 策略扩展
系统采用策略模式设计，新增策略类只需：
1. 实现`OperationStrategy`接口
2. 在`StrategyFactory`中注册新策略
3. 更新配置文件中的`strategyType`选项

### 规则扩展
规则模型采用灵活的JSON配置，支持：
- 新增规则参数（如未来的`replacements`参数扩展）
- 自定义策略配置
- 动态规则加载

## 测试与验证

系统包含完整的测试用例：
- `TestNewFramework.java`：验证多层级规则继承机制
- 各策略类的单元测试
- 集成测试验证端到端功能

## 总结

FRT系统通过精心设计的架构和灵活的规则配置机制，为复杂的文件更新场景提供了强大的解决方案。系统当前专注于Minecraft模组管理和通用文件操作，同时为未来的功能扩展预留了充分的空间。

**特别提醒**：`replacements`参数目前仅作为预留字段，在实际使用中请避免依赖此参数实现业务逻辑。

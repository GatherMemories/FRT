# 多层级文件夹更新系统 (FRT)

## 系统概述

这是一个基于Java实现的多层级文件夹更新系统，支持用户自定义匹配规则的JSON配置文件。系统能够处理多层级文件夹结构，并实现规则继承机制，使子文件夹在没有自己的规则时继承父文件夹的规则。

## 核心特性

- **多层级规则继承**：子文件夹可继承父文件夹规则，优先使用本地规则
- **灵活的配置**：支持replace.json、add.json、delete.json三种配置文件
- **多种操作模式**：支持替换、新增、删除三种文件操作
- **备份与恢复**：自动备份和恢复功能
- **用户交互**：操作确认机制

## 架构设计

### 设计模式应用

1. **组合模式 (Composite Pattern)**
   - `FileNode`：定义文件系统节点的抽象基类
   - `FileLeaf`：代表实际文件的叶子节点
   - `FolderNode`：包含子节点的复合节点

2. **策略模式 (Strategy Pattern)**
   - `OperationStrategy`：定义操作策略接口
   - `ReplaceStrategy`：文件替换策略
   - `AddStrategy`：文件新增策略
   - `DeleteStrategy`：文件删除策略

3. **责任链模式 (Chain of Responsibility Pattern)**
   - `RuleInheritanceContext`：实现规则继承机制
   - 子节点优先使用本地规则，否则继承父节点规则

4. **工厂模式 (Factory Pattern)**
   - `StrategyFactory`：创建不同类型的策略对象

### 项目结构

```
src/main/java/com/awei/frt/
├── core/                                  # 核心框架层
│   ├── context/                           # 上下文管理
│   │   ├── OperationContext.java          # 操作上下文
│   │   └── RuleInheritanceContext.java    # 规则继承上下文
│   ├── node/                              # 文件节点（组合模式）
│   │   ├── FileNode.java                  # 文件节点抽象基类
│   │   ├── FolderNode.java                # 文件夹节点
│   │   └── FileLeaf.java                  # 文件节点（叶子）
│   ├── strategy/                          # 操作策略（策略模式）
│   │   ├── OperationStrategy.java         # 操作策略接口
│   │   ├── AddStrategy.java               # 新增策略
│   │   ├── ReplaceStrategy.java           # 替换策略
│   │   └── DeleteStrategy.java            # 删除策略
│   └── builder/                           # 构建器
│       └── FileTreeBuilder.java           # 文件树构建器
├── service/                               # 业务服务层
│   ├── FileReplaceServiceNew.java         # 文件替换服务
│   └── RestoreService.java                # 恢复服务
├── model/                                 # 数据模型层
│   ├── Config.java                        # 配置模型
│   ├── ReplaceRule.java                   # 替换规则模型
│   ├── OperationRecord.java               # 操作记录模型
│   └── ProcessingResult.java              # 处理结果模型
├── utils/                                 # 工具类层
│   ├── ConfigLoader.java                  # 配置加载器
│   └── FileUtils.java                     # 文件工具类
├── factory/                               # 工厂层
│   └── StrategyFactory.java               # 策略工厂
└── exception/                             # 异常层
    ├── FRTException.java                  # 基础异常类
    ├── RuleNotFoundException.java         # 规则未找到异常
    └── FileOperationException.java        # 文件操作异常
```

## 规则继承机制

系统实现了灵活的规则继承机制：

1. **本地规则优先**：每个文件夹优先使用自己的规则配置文件
2. **继承机制**：当文件夹没有自己的规则时，自动继承父文件夹的规则
3. **多层继承**：支持任意层级的规则继承
4. **配置类型**：支持replace.json、add.json、delete.json三种配置类型

### 规则文件格式

**replace.json** - 用于文件内容替换：
```json
{
  "patterns": ["*.jar", "*.class"],
  "excludePatterns": ["*backup*", "*Test*"],
  "backup": true,
  "confirmBeforeReplace": false,
  "replacements": [
    {
      "oldValue": "old_text",
      "newValue": "new_text"
    }
  ]
}
```

**add.json** - 用于文件新增：
```json
{
  "patterns": ["*.new", "*.add"],
  "excludePatterns": ["*.tmp"],
  "backup": false,
  "confirmBeforeReplace": true
}
```

**delete.json** - 用于文件删除：
```json
{
  "patterns": ["*.old", "*.del"],
  "excludePatterns": ["*.keep"],
  "backup": true,
  "confirmBeforeReplace": true
}
```

## 使用方法

### 1. 启动系统
```bash
mvn compile exec:java -Dexec.mainClass="com.awei.frt.Main"
```

### 2. 目录结构示例
```
FRT项目根目录/
├── config.json              # 全局配置文件
├── update/                  # 更新文件目录
│   ├── replace.json         # 根级替换规则
│   ├── file1.jar            # 需要处理的文件
│   └── subfolder/
│       ├── add.json         # 子目录新增规则
│       ├── file2.class      # 需要处理的文件
│       └── subsubfolder/    # 无规则，继承父目录规则
│           └── file3.class  # 需要处理的文件
├── THtest/                  # 目标目录
├── old/                     # 备份目录
└── logs/                    # 日志目录
```

### 3. 配置文件
系统按以下优先级加载配置：
1. FRT项目根目录外部的config.json
2. resources目录下的config.json
3. 使用默认配置

## 扩展性与维护性

### 扩展性
- **策略模式**：易于添加新的文件操作类型
- **组合模式**：易于扩展新的节点类型
- **工厂模式**：易于扩展新的策略创建方式

### 维护性
- **解耦设计**：各组件职责明确，相互独立
- **统一接口**：统一的节点和策略接口便于维护
- **模块化结构**：清晰的分层架构便于维护

## 测试验证

系统包含完整的测试用例验证功能：
- `TestNewFramework.java`：验证多层级规则继承
- 各组件单元测试

## 总结

该系统通过多种设计模式的组合应用，实现了灵活、可扩展、易维护的多层级文件夹更新功能。规则继承机制确保了配置的灵活性，同时保持了系统的简洁性。整体架构清晰，便于后续功能扩展和维护。
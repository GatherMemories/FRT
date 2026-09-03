package com.awei.frt.core.node;

import com.awei.frt.constants.RulesConstants;
import com.awei.frt.core.context.OperationContext;
import com.awei.frt.core.context.RuleInheritanceContext;
import com.awei.frt.core.strategy.OperationStrategy;
import com.awei.frt.factory.StrategyFactory;
import com.awei.frt.model.MatchRule;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 文件夹节点（组合模式中的复合节点）
 * 包含子节点（文件和子文件夹）
 */
public class FolderNode extends FileNode {
    private final List<FileNode> children;

    public FolderNode(Path path, String relativePath) {
        super(path, relativePath);
        this.children = new ArrayList<>();
    }

    /**
     * 处理文件夹节点及其子节点
     * 使用栈实现迭代遍历，避免递归导致的栈溢出
     *
     * 原理：
     * 1. 创建一个栈，将根节点任务压入栈
     * 2. 循环弹出栈顶任务进行处理
     * 3. 处理当前节点时，将其子文件夹节点压入栈（倒序压入，保证正序处理）
     * 4. 直到栈为空，所有节点处理完毕
     *
     * @param localRuleIC 规则继承上下文
     * @param context 操作上下文
     * @param operationType 操作类型
     */
    @Override
    public void process(RuleInheritanceContext localRuleIC, OperationContext context, String[] operationType) {
        // 创建任务栈，用于模拟递归调用栈
        Deque<ProcessTask> stack = new ArrayDeque<>();
        // 将根节点任务压入栈
        stack.push(new ProcessTask(this, localRuleIC));

        // 主循环：只要栈不为空，就继续处理
        while (!stack.isEmpty()) {
            ProcessTask task = stack.pop();
            FolderNode node = task.node;
            RuleInheritanceContext parentRuleIC = task.ruleContext;

            // 正常处理阶段：取规则 → 执行策略 → 子文件处理 → 子文件夹入栈
            prepareAndScheduleProcess(node, parentRuleIC, context, operationType, stack);
        }
    }

    /**
     * 准备并调度处理任务
     * 1. 创建规则上下文副本
     * 2. 获取有效规则并执行策略
     * 3. 处理子文件（直接处理）
     * 4. 将子文件夹压入栈（延迟处理）
     *
     * @param node 当前处理的文件夹节点
     * @param parentRuleIC 父级规则上下文
     * @param context 操作上下文
     * @param operationType 操作类型
     * @param stack 任务栈
     */
    private void prepareAndScheduleProcess(FolderNode node, RuleInheritanceContext parentRuleIC,
            OperationContext context, String[] operationType, Deque<ProcessTask> stack) {
        // 创建当前文件夹的规则上下文副本（继承父级规则）
        RuleInheritanceContext ruleContext = new RuleInheritanceContext(parentRuleIC);
        // 获取当前文件夹的有效规则
        MatchRule effectiveRule = ruleContext.getEffectiveRule(node);
        // 将规则上下文设置到操作上下文中（供策略类访问）
        context.setRuleInheritanceContext(ruleContext);

        // 本层无有效规则（含继承被禁用且无本地规则）：本层文件不做任何处理，
        // 但<b>仍须下钻子文件夹</b>——深层子目录可能自带本地规则文件（"按目录放规则文件"
        // 的约定），若父层无规则就直接剪枝整棵子树，深层规则将永远不可达（审查 H4）。
        // 同时把本层被跳过的文件计入进度（done/total 对齐，进度条可达 100%）。
        if (effectiveRule == null || effectiveRule.getEffectiveStrategies().isEmpty()) {
            reportSkippedFiles(node, context);
            scheduleChildFolders(node, ruleContext, stack);
            return;
        }

        // 多策略组合链：按序执行每个策略步骤，前序已处理的节点（handled）后续步骤跳过
        RuleInheritanceContext ric = context.getRuleInheritanceContext();
        MatchRule savedRule = ric.getRuleChain();
        try {
            for (MatchRule step : effectiveRule.getEffectiveStrategies()) {
                if (node.isHandled()) {
                    break;
                }
                ric.setRuleChain(step);
                OperationStrategy strategy = StrategyFactory.createStrategy(step.getStrategyType());
                strategy.execute(node, context, operationType);
            }
        } finally {
            ric.setRuleChain(savedRule);
        }

        // 处理直接子文件；子文件夹压栈稍后处理
        for (FileNode child : node.children) {
            if (!child.isDirectory()) {
                child.process(ruleContext, context, operationType);
            }
        }

        scheduleChildFolders(node, ruleContext, stack);
    }

    /** 把子文件夹压入任务栈（倒序保证正序处理） */
    private void scheduleChildFolders(FolderNode node, RuleInheritanceContext ruleContext,
            Deque<ProcessTask> stack) {
        // 子文件夹：全部入栈（是否处理由各层自己的规则决定，父层无规则不剪枝子树）
        for (int i = node.children.size() - 1; i >= 0; i--) {
            FileNode child = node.children.get(i);
            if (child.isDirectory()) {
                stack.push(new ProcessTask((FolderNode) child, ruleContext));
            }
        }
    }

    /** 本层因无规则被跳过的直接子文件：计入进度（不产生操作记录，仅对齐 done/total） */
    private void reportSkippedFiles(FolderNode node, OperationContext context) {
        for (FileNode child : node.children) {
            if (!child.isDirectory()) {
                context.reportProgress(child.getRelativePath());
            }
        }
    }

    /**
     * 处理任务内部类
     * 封装一次处理所需的所有上下文信息
     */
    private static class ProcessTask {
        final FolderNode node;                    // 要处理的节点
        final RuleInheritanceContext ruleContext; // 规则上下文

        ProcessTask(FolderNode node, RuleInheritanceContext ruleContext) {
            this.node = node;
            this.ruleContext = ruleContext;
        }
    }

    @Override
    public boolean isDirectory() {
        return true;
    }

    @Override
    public int getChildCount() {
        return children.size();
    }

    public void addChild(FileNode child) {
        children.add(child);
    }

    public List<FileNode> getChildren() {
        return new ArrayList<>(children);
    }

    public boolean isDirectoryType() {
        return Files.isDirectory(path);
    }

    /**
     * 构建子节点树
     * 使用栈实现迭代遍历，避免递归导致的栈溢出
     *
     * 原理：
     * 1. 创建构建任务栈，将根节点任务压入栈
     * 2. 弹出栈顶任务，遍历该目录下的所有子项
     * 3. 对于每个子项：
     *    - 如果是文件：直接创建 FileLeaf 并添加到父节点
     *    - 如果是文件夹：创建 FolderNode，添加到父节点，并将构建任务压入栈
     * 4. 重复步骤2-3，直到栈为空
     *
     * 优点：使用堆内存代替系统栈，可处理任意深度的目录结构
     */
    public void buildChildren() {
        // 如果不是目录，直接返回
        if (!isDirectoryType()) {
            return;
        }

        // 创建构建任务栈
        Deque<BuildTask> stack = new ArrayDeque<>();
        // 将根节点构建任务压入栈
        stack.push(new BuildTask(this, this.path, this.relativePath));

        // 主循环：只要栈不为空，就继续构建
        while (!stack.isEmpty()) {
            // 弹出栈顶任务（当前要处理的目录）
            BuildTask task = stack.pop();
            FolderNode parentNode = task.parentNode;      // 父节点（用于添加子节点）
            Path currentPath = task.currentPath;          // 当前目录路径
            String currentRelativePath = task.relativePath; // 当前相对路径

            try (Stream<Path> stream = Files.list(currentPath)) {
                // 获取当前目录下的所有子项
                List<Path> paths = stream.collect(Collectors.toList());
                // 暂存子文件夹构建任务
                List<BuildTask> subFolders = new ArrayList<>();

                // 遍历当前目录下的所有子项
                for (Path childPath : paths) {
                    // 计算子项的相对路径
                    String childRelativePath = currentRelativePath.isEmpty() ?
                            childPath.getFileName().toString() :
                            currentRelativePath + "/" + childPath.getFileName();

                    // 跳过规则配置文件
                    String fileName = childPath.getFileName().toString();
                    if (isRuleFile(fileName)) {
                        continue;
                    }

                    if (Files.isDirectory(childPath)) {
                        // 子项是文件夹：创建节点，添加到父节点，暂存构建任务
                        FolderNode folderNode = new FolderNode(childPath, childRelativePath);
                        parentNode.addChild(folderNode);
                        subFolders.add(new BuildTask(folderNode, childPath, childRelativePath));
                    } else {
                        // 子项是文件：直接创建叶子节点并添加到父节点
                        parentNode.addChild(new FileLeaf(childPath, childRelativePath));
                    }
                }

                // 将子文件夹构建任务倒序压入栈
                // 倒序是为了保证正序处理（栈是后进先出）
                for (int i = subFolders.size() - 1; i >= 0; i--) {
                    stack.push(subFolders.get(i));
                }
            } catch (IOException e) {
                throw new RuntimeException("构建文件夹节点失败: " + currentPath, e);
            }
        }
    }

    /**
     * 检查是否是规则文件
     */
    private boolean isRuleFile(String fileName) {
        String[] ruleTypes = RulesConstants.FileNames.ALL_RULE_FILES;
        for (String ruleType : ruleTypes) {
            if (fileName.equals(ruleType)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return "FolderNode[" + path + ", children: " + children.size() + "]";
    }

    /**
     * 构建任务内部类
     * 封装一次目录构建所需的所有上下文信息
     */
    private static class BuildTask {
        final FolderNode parentNode;   // 父节点（用于添加子节点）
        final Path currentPath;        // 当前要遍历的目录路径
        final String relativePath;     // 当前目录的相对路径

        BuildTask(FolderNode parentNode, Path currentPath, String relativePath) {
            this.parentNode = parentNode;
            this.currentPath = currentPath;
            this.relativePath = relativePath;
        }
    }
}

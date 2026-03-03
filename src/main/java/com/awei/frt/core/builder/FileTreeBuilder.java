package com.awei.frt.core.builder;

import com.awei.frt.core.node.FileNode;
import com.awei.frt.core.node.FileLeaf;
import com.awei.frt.core.node.FolderNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * 文件树构建器
 * 负责从文件系统路径构建节点树结构
 */
public class FileTreeBuilder {

    /**
     * 构建文件树
     * @param rootPath 根路径
     * @return 构建的文件树根节点
     */
    public static FileNode buildTree(Path rootPath) {
        if (Files.isDirectory(rootPath)) {
            FolderNode rootNode = new FolderNode(rootPath, "");
            rootNode.buildChildren();
            return rootNode;
        } else {
            return new FileLeaf(rootPath, rootPath.getFileName().toString());
        }
    }

    /**
     * 打印文件树结构（用于调试）
     * 使用栈实现的迭代方式，避免递归栈溢出
     *
     * 输出格式示例：
     * 📁 root/
     * ├── 📁 folder1/
     * │   ├── 📄 file1.txt
     * │   └── 📄 file2.txt
     * └── 📁 folder2/
     *     └── 📄 file3.txt
     *
     * @param node 根节点
     * @param depth 初始深度（用于缩进显示）
     */
    public static void printTree(FileNode node, int depth) {
        // 步骤1: 初始化任务栈
        // 栈用于存储待打印的节点任务，实现迭代遍历
        Deque<PrintTask> stack = new ArrayDeque<>();
        // 将根节点作为第一个任务压入栈
        // isLast=true 表示根节点是"最后一个"（根节点没有同级节点）
        // parentPrefixes=空列表 表示根节点没有父级前缀
        stack.push(new PrintTask(node, depth, true, new ArrayList<>()));

        // 步骤2: 主循环 - 处理栈中的所有任务
        // 循环条件：栈不为空时继续处理
        while (!stack.isEmpty()) {
            // 步骤2.1: 弹出栈顶任务
            // 每次循环处理一个节点
            PrintTask task = stack.pop();
            FileNode currentNode = task.node;           // 当前要打印的节点
            int currentDepth = task.depth;              // 当前深度（用于判断是否是根节点）
            boolean isLast = task.isLast;               // 是否是同级最后一个节点
            List<Boolean> parentPrefixes = task.parentPrefixes; // 父级前缀列表

            // 步骤2.2: 根据深度选择处理方式
            if (currentDepth == 0) {
                // 情况A: 根节点（depth=0）
                // 根节点特殊处理：不带前缀，直接打印
                printRootNode(currentNode);
                // 将根节点的子节点压入栈，父级前缀为空
                pushChildrenTasks(currentNode, currentDepth, stack, new ArrayList<>());
            } else {
                // 情况B: 非根节点（depth>0）
                // 带前缀打印，前缀由父级连接线组成
                printNodeWithPrefix(currentNode, currentDepth, isLast, parentPrefixes);
                // 构建新的父级前缀列表
                // 当前节点如果不是最后一个，则父级需要显示连接线（│）
                List<Boolean> newPrefixes = new ArrayList<>(parentPrefixes);
                newPrefixes.add(!isLast); // true=需要画连接线，false=空白
                // 将当前节点的子节点压入栈
                pushChildrenTasks(currentNode, currentDepth, stack, newPrefixes);
            }
        }
        // 步骤3: 栈为空，所有节点打印完毕
    }

    /**
     * 打印根节点
     */
    private static void printRootNode(FileNode node) {
        if (node.isDirectory()) {
            System.out.println("📁 " + node.getName() + "/");
        } else {
            System.out.println("📄 " + node.getName());
        }
    }

    /**
     * 打印带前缀的节点
     */
    private static void printNodeWithPrefix(FileNode node, int depth, boolean isLast, List<Boolean> parentPrefixes) {
        StringBuilder prefix = new StringBuilder();

        for (int i = 0; i < parentPrefixes.size(); i++) {
            if (parentPrefixes.get(i)) {
                prefix.append("│   ");
            } else {
                prefix.append("    ");
            }
        }

        String connector = isLast ? "└── " : "├── ";
        String icon = node.isDirectory() ? "📁 " : "📄 ";
        String suffix = node.isDirectory() ? "/" : "";

        System.out.println(prefix + connector + icon + node.getName() + suffix);
    }

    /**
     * 将子节点任务压入栈
     */
    private static void pushChildrenTasks(FileNode node, int currentDepth, 
            Deque<PrintTask> stack, List<Boolean> newPrefixes) {
        if (!node.isDirectory()) {
            return;
        }

        FolderNode folderNode = (FolderNode) node;
        List<FileNode> children = folderNode.getChildren();

        if (children.isEmpty()) {
            return;
        }

        List<PrintTask> tasks = new ArrayList<>();
        for (int i = 0; i < children.size(); i++) {
            boolean isLastChild = (i == children.size() - 1);
            tasks.add(new PrintTask(children.get(i), currentDepth + 1, isLastChild, newPrefixes));
        }

        for (int i = tasks.size() - 1; i >= 0; i--) {
            stack.push(tasks.get(i));
        }
    }

    /**
     * 打印任务内部类
     * 封装一次打印所需的所有信息
     */
    private static class PrintTask {
        final FileNode node;           // 要打印的节点
        final int depth;               // 当前深度
        final boolean isLast;          // 是否是同级最后一个节点
        final List<Boolean> parentPrefixes; // 父级前缀列表（用于绘制连接线）

        PrintTask(FileNode node, int depth, boolean isLast, List<Boolean> parentPrefixes) {
            this.node = node;
            this.depth = depth;
            this.isLast = isLast;
            this.parentPrefixes = parentPrefixes;
        }
    }
}

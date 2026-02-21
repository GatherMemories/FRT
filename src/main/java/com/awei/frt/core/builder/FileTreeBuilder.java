package com.awei.frt.core.builder;

import com.awei.frt.core.node.FileNode;
import com.awei.frt.core.node.FileLeaf;
import com.awei.frt.core.node.FolderNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
     * @param node 当前节点
     * @param depth 当前深度
     */
    public static void printTree(FileNode node, int depth) {
        String indent = "  ".repeat(Math.max(0, depth));

        if (node.isDirectory()) {
            int newDepth = depth + 1;
            List<FileNode> FolderNodes = new ArrayList<>();
            System.out.println(indent + "[DIR] " + node.getName() + "/");
            FolderNode folderNode = (FolderNode) node;
            for (FileNode child : folderNode.getChildren()) {
                if (child.isDirectory()){
                    FolderNodes.add(child);
                    continue;
                }
                printTree(child, newDepth);
            }
            for (FileNode FolderNode : FolderNodes) {
                printTree(FolderNode, newDepth);
            }
        } else {
            System.out.println(indent + "[FILE] " + node.getName());
        }

    }
}

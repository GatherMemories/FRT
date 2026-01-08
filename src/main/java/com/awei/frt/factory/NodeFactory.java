package com.awei.frt.factory;

import com.awei.frt.core.node.FileNode;
import com.awei.frt.core.node.FileNodeImpl;
import com.awei.frt.core.node.RootNode;

import java.nio.file.Path;

/**
 * 节点工厂（简化版）
 * 根据路径类型创建相应的节点对象
 */
public class NodeFactory {

    /**
     * 创建根节点
     *
     * @param rootPath 根目录路径
     * @return 根节点对象
     */
    public static RootNode createRootNode(Path rootPath) {
        return new RootNode(rootPath.normalize());
    }

    /**
     * 创建文件节点
     *
     * @param path 文件路径
     * @param relativePath 相对路径
     * @return 文件节点对象
     */
    public static FileNode createFileNode(Path path, String relativePath) {
        return new FileNodeImpl(path.normalize(), relativePath);
    }

    /**
     * 创建文件节点（自动计算相对路径）
     *
     * @param basePath 基准路径
     * @param fullPath 完整路径
     * @return 文件节点对象
     */
    public static FileNode createFileNode(Path basePath, Path fullPath) {
        basePath = basePath.normalize();
        fullPath = fullPath.normalize();

        String relativePath = basePath.relativize(fullPath).toString();
        return new FileNodeImpl(fullPath, relativePath);
    }

    /**
     * 判断是否为根目录
     *
     * @param basePath 基准路径
     * @param path 要检查的路径
     * @return 是否为根目录
     */
    public static boolean isRootDirectory(Path basePath, Path path) {
        basePath = basePath.normalize();
        path = path.normalize();
        return basePath.equals(path);
    }
}

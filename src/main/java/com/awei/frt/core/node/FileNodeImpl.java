package com.awei.frt.core.node;

import com.awei.frt.core.context.OperationContext;
import com.awei.frt.core.strategy.OperationStrategy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 文件节点实现（简化版 - 不使用 Jackson）
 */
public class FileNodeImpl extends FileNode {
    public FileNodeImpl(Path path, String relativePath) {
        super(path, relativePath);
    }

    @Override
    public void process(OperationStrategy strategy, String rule, OperationContext context) {
        strategy.execute(this, rule, context);
    }
    
    @Override
    public int getChildCount() {
        if (!isDirectory()) {
            return 0;
        }
        try (Stream<Path> stream = Files.list(path)) {
            return (int) stream.count();
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * 获取文件内容
     */
    public String getContent() {
        try {
            return Files.readString(path);
        } catch (Exception e) {
            throw new RuntimeException("读取文件失败: " + path, e);
        }
    }

    /**
     * 设置文件内容
     */
    public void setContent(String content) {
        try {
            Files.writeString(path, content);
        } catch (Exception e) {
            throw new RuntimeException("写入文件失败: " + path, e);
        }
    }

    /**
     * 检查是否是文件
     */
    public boolean isFile() {
        return Files.isRegularFile(path);
    }

    /**
     * 检查是否是目录
     */
    public boolean isDirectory() {
        return Files.isDirectory(path);
    }

    /**
     * 获取子节点列表（如果是目录）
     */
    public java.util.List<FileNode> getChildren() {
        if (!isDirectory()) {
            return java.util.Collections.emptyList();
        }

        try (Stream<Path> stream = Files.list(path)) {
            return stream
                .filter(Files::isRegularFile)
                .filter(p -> !p.getFileName().toString().equals("replace.json"))
                .filter(p -> !p.getFileName().toString().startsWith("."))
                .map(p -> new FileNodeImpl(p, relativePath + "/" + p.getFileName()))
                .collect(Collectors.toList());
        } catch (Exception e) {
            throw new RuntimeException("读取目录失败: " + path, e);
        }
    }

    /**
     * 获取子目录列表
     */
    public java.util.List<FileNode> getChildDirectories() {
        if (!isDirectory()) {
            return java.util.Collections.emptyList();
        }

        try (Stream<Path> stream = Files.list(path)) {
            return stream
                .filter(Files::isDirectory)
                .filter(p -> !p.getFileName().toString().startsWith("."))
                .map(p -> new FileNodeImpl(p, relativePath + "/" + p.getFileName()))
                .collect(Collectors.toList());
        } catch (Exception e) {
            throw new RuntimeException("读取子目录失败: " + path, e);
        }
    }
}

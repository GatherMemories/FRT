package com.awei.frt.core.node;

import java.nio.file.Path;

/**
 * 根节点（简化版）
 * 代表整个项目的根目录
 */
public class RootNode extends FileNodeImpl {
    public RootNode(Path path) {
        super(path, "");
    }

    @Override
    public String getRelativePath() {
        return "";
    }

    @Override
    public String toString() {
        return "RootNode[" + path + "]";
    }
}

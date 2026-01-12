package com.awei.frt.constants;

/**
 * 规则常量类
 * 定义FRT项目中使用的各种常量
 */
public final class RulesConstants {
    // 私有构造函数，防止实例化
    private RulesConstants() {
        throw new AssertionError("无法实例化 RulesConstants 常量类");
    }


    public static final class FileNames {
        /** 匹配规则文件名 (增删改)*/
        public static final String MATCHING_RULES_JSON = "matching-rules.json";

        /** 替换操作规则文件名（旧版，可能已废弃） */
        public static final String REPLACE_JSON = "replace.json";

        /** 新增操作规则文件名（旧版，可能已废弃） */
        public static final String ADD_JSON = "add.json";

        /** 删除操作规则文件名（旧版，可能已废弃） */
        public static final String DELETE_JSON = "delete.json";

        /** 所有规则文件名数组（用于遍历和查找） */
        public static final String[] ALL_RULE_FILES = {
                MATCHING_RULES_JSON,
                REPLACE_JSON,
                ADD_JSON,
                DELETE_JSON
        };
    }

}

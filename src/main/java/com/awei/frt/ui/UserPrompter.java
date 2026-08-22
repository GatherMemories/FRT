package com.awei.frt.ui;

/**
 * 用户交互抽象（UI 化改造）
 * 控制台模式与 Swing 模式共用服务层：服务内不再直接依赖 Scanner，
 * 而是通过本接口读取用户输入；交互提示仍由服务打印（控制台/日志区），
 * SwingPrompter 会取"最近一行提示"弹出对话框。
 */
public interface UserPrompter {

    /**
     * 读取一行用户输入（已 trim；输入被取消/EOF 时返回空串）
     * @return 用户输入
     */
    String readLine();
}

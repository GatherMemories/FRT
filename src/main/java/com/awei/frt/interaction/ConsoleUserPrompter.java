package com.awei.frt.interaction;

import java.util.Scanner;

/**
 * 控制台交互实现：包装 Scanner（默认行为，与控制台版一致）
 */
public class ConsoleUserPrompter implements UserPrompter {

    private final Scanner scanner;

    public ConsoleUserPrompter(Scanner scanner) {
        this.scanner = scanner;
    }

    @Override
    public String readLine() {
        return scanner.hasNextLine() ? scanner.nextLine().trim() : "";
    }
}

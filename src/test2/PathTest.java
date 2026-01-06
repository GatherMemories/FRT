package com.awei.frt.test2;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 路径检测测试工具
 */
public class PathTest {
    
    public static void main(String[] args) {
        PathTest tester = new PathTest();
        tester.testPaths();
    }
    
    public void testPaths() {
        System.out.println("=".repeat(50));
        System.out.println("           路径检测测试");
        System.out.println("=".repeat(50));
        
        // 获取项目根目录
        Path projectRoot = getProjectRoot();
        System.out.println("项目根目录: " + projectRoot);
        System.out.println();
        
        // 测试各种路径格式
        String[] testPaths = {
            "C:/Users/5454564546/Desktop/THtest",
            "C:\\Users\\5454564546\\Desktop\\THtest", 
            "../THtest",
            "THtest",
            "./THtest",
            "/THtest"
        };
        
        for (String testPath : testPaths) {
            System.out.println("测试路径: " + testPath);
            testSinglePath(testPath, projectRoot);
            System.out.println();
        }
        
        // 测试真实存在的目录
        System.out.println("检测真实存在的目录:");
        testRealDirectories(projectRoot);
    }
    
    private void testSinglePath(String pathStr, Path projectRoot) {
        try {
            Path pathObj = Paths.get(pathStr);
            
            System.out.println("  路径对象: " + pathObj);
            System.out.println("  是否绝对路径: " + pathObj.isAbsolute());
            System.out.println("  根路径: " + pathObj.getRoot());
            System.out.println("  文件名: " + pathObj.getFileName());
            
            Path resolvedPath;
            if (pathObj.isAbsolute()) {
                resolvedPath = pathObj.normalize();
                System.out.println("  解析方式: 直接使用绝对路径");
            } else {
                resolvedPath = projectRoot.resolve(pathObj).normalize();
                System.out.println("  解析方式: 基于项目根目录解析");
                System.out.println("  项目根目录: " + projectRoot);
            }
            
            System.out.println("  标准化路径: " + resolvedPath);
            System.out.println("  实际存在: " + (Files.exists(resolvedPath) ? "✓" : "✗"));
            
            // 检查父目录
            Path parent = resolvedPath.getParent();
            if (parent != null) {
                System.out.println("  父目录: " + parent);
                System.out.println("  父目录存在: " + (Files.exists(parent) ? "✓" : "✗"));
                
                if (Files.exists(parent)) {
                    try {
                        Object[] files = Files.list(parent).limit(20).toArray();
                        System.out.println("  父目录内容 (" + files.length + " 个):");
                        for (Object file : files) {
                            System.out.println("    " + file);
                        }
                    } catch (Exception e) {
                        System.out.println("  父目录内容: 无法读取 - " + e.getMessage());
                    }
                }
            }
            
        } catch (Exception e) {
            System.out.println("  错误: " + e.getMessage());
        }
    }
    
    private void testRealDirectories(Path projectRoot) {
        String[] likelyPaths = {
            "C:/Users/5454564546/Desktop",
            "C:/Users/5454564546/Desktop/FRT",
            "C:/Users/5454564546/Desktop/THtest",
            "C:/Users/5454564546/Desktop/update",
            "C:/Users/5454564546/Desktop/delete",
            "C:/Users/5454564546/Desktop/old",
            "C:/Users/5454564546/Desktop/logs",
            "../",
            "../THtest",
            "../update"
        };
        
        for (String pathStr : likelyPaths) {
            try {
                Path path;
                if (Paths.get(pathStr).isAbsolute()) {
                    path = Paths.get(pathStr);
                } else {
                    path = projectRoot.resolve(pathStr).normalize();
                }
                
                if (Files.exists(path)) {
                    System.out.println("  ✓ " + pathStr + " -> " + path);
                    
                    if (Files.isDirectory(path)) {
                        try {
                            long count = Files.list(path).count();
                            System.out.println("      目录包含 " + count + " 个项目");
                        } catch (Exception e) {
                            System.out.println("      无法读取目录内容");
                        }
                    }
                } else {
                    System.out.println("  ✗ " + pathStr + " -> " + path);
                }
                
            } catch (Exception e) {
                System.out.println("  ? " + pathStr + " - 错误: " + e.getMessage());
            }
        }
    }
    
    private Path getProjectRoot() {
        try {
            Path currentPath = Paths.get(getClass().getProtectionDomain().getCodeSource().getLocation().toURI());
            
            if (currentPath.toString().contains("classes")) {
                return currentPath.getParent().getParent();
            }
            
            if (currentPath.toString().contains("target")) {
                return currentPath.getParent();
            }
            
            return Paths.get("").toAbsolutePath();
        } catch (Exception e) {
            return Paths.get("").toAbsolutePath();
        }
    }
}
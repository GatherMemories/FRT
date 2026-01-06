import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ConfigValidationTest {
    
    public static void main(String[] args) {
        System.out.println("=========================================");
        System.out.println("🔍 配置文件路径验证");
        System.out.println("=========================================");
        
        Path projectRoot = Paths.get("").toAbsolutePath().normalize();
        Path parentDir = projectRoot.getParent();
        
        System.out.println("📁 项目根目录: " + projectRoot);
        System.out.println("📁 FRT同级目录: " + parentDir);
        System.out.println();
        
        System.out.println("🔍 检查各路径配置的正确性:");
        System.out.println();
        
        // 测试项目内路径
        System.out.println("📍 当前配置(项目内路径):");
        checkPath("update", projectRoot.resolve("update"));
        checkPath("THtest", projectRoot.resolve("THtest"));
        checkPath("delete", projectRoot.resolve("delete"));
        checkPath("old", projectRoot.resolve("old"));
        checkPath("logs", projectRoot.resolve("logs"));
        
        System.out.println();
        System.out.println("📍 建议配置(FRT同级路径):");
        checkPath("../update", parentDir.resolve("update"));
        checkPath("../THtest", parentDir.resolve("THtest"));
        checkPath("../delete", parentDir.resolve("delete"));
        checkPath("../old", parentDir.resolve("old"));
        checkPath("../logs", parentDir.resolve("logs"));
        
        System.out.println();
        System.out.println("💡 建议的配置文件内容:");
        System.out.println("{");
        System.out.println("  \"targetPath\": \"../THtest\",");
        System.out.println("  \"updatePath\": \"../update\",");
        System.out.println("  \"deletePath\": \"../delete\",");
        System.out.println("  \"backupPath\": \"../old\",");
        System.out.println("  \"logPath\": \"../logs\"");
        System.out.println("}");
    }
    
    private static void checkPath(String config, Path actualPath) {
        boolean exists = Files.exists(actualPath);
        String status = exists ? "✓ 存在" : "✗ 缺失";
        System.out.println("   " + config + " -> " + actualPath + " " + status);
    }
}
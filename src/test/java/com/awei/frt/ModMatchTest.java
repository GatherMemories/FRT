package com.awei.frt;

import me.andreasmelone.basicmodinfoparser.BasicModInfo;
import me.andreasmelone.basicmodinfoparser.Platform;

import java.io.File;
import java.io.IOException;
import java.util.jar.JarFile;

/**
 * @Author: mou_ren
 * @Date: 2026/1/18 00:24
 */
public class ModMatchTest {


    public static void main(String[] args) {
        // Specify the folder containing your .jar files
        File folder = new File("mods/");
        File[] modFiles = folder.listFiles((dir, name) -> name.endsWith(".jar"));

        if (modFiles != null) {
            for (File modFile : modFiles) {
                try (JarFile jarFile = new JarFile(modFile)) {
                    // Detect the mod platform (Forge, Fabric, etc.)
                    Platform[] platforms = Platform.findModPlatform(modFile);
                    if (platforms.length == 0) {
                        System.out.println("No supported platform found for: " + modFile.getName());
                        continue;
                    }
                    for (Platform platform : platforms) {
                        // Get the mod info content and parse it
                        String modInfoContent = platform.getInfoFileContent(jarFile);
                        for (BasicModInfo modInfo : platform.parse(modInfoContent)) {
                            // Output the parsed mod information
                            System.out.println("Mod ID: " + modInfo.getId());
                            System.out.println("Mod Name: " + modInfo.getName());
                            System.out.println("Mod Version: " + modInfo.getVersion());
                            System.out.println("Mod Description: " + modInfo.getDescription());
                        }
                    }
                } catch (IOException e) {
                    System.err.println("Failed to read mod file: " + modFile.getName());
                    e.printStackTrace();
                }
            }
        }
    }
}

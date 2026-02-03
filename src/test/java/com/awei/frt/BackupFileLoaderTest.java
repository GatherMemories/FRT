package com.awei.frt;

import com.awei.frt.core.builder.BackupFileLoader;
import com.awei.frt.core.builder.ConfigLoader;
import com.awei.frt.model.Config;
import com.awei.frt.model.ProcessingResult;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class BackupFileLoaderTest {

    @Test
    public void test() {

        Map<String, Path> backupFiles = BackupFileLoader.getBackupFiles();
        System.out.println(backupFiles);

        Path recordPath = Path.of("C:\\Users\\5454564546\\Desktop\\FRT\\backup\\record\\backup-20260131-184239.json");
        ProcessingResult processingResult = BackupFileLoader.loadOperationRecord(recordPath.getFileName().toString());
        System.out.println(processingResult);
    }
}

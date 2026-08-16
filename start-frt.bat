@echo off
rem ============================================================
rem  FRT - Multi-level Folder Update Tool (launcher)
rem  Sets UTF-8 codepage for CJK display, then runs shaded jar.
rem  Requires JDK 17+ (tested on 21)
rem ============================================================
chcp 65001 >nul
title FRT - Multi-level Folder Update System
cd /d "%~dp0"

set JAR=target\FRT-0.1.0-SNAPSHOT.jar

if not exist "%JAR%" (
    echo [ERROR] jar not found: %JAR%
    echo Please run: mvn -o package -DskipTests first.
    pause
    exit /b 1
)

java -Dfile.encoding=UTF-8 -jar "%JAR%"

echo.
pause

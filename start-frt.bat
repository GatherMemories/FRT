@echo off
rem ============================================================
rem  多层级文件夹更新工具 - 启动脚本 (Windows)
rem  设置 UTF-8 代码页保证中文显示，默认启动图形界面
rem  用法:
rem    start-frt.bat                默认启动图形界面 (UI)
rem    start-frt.bat --console      切换为控制台模式（-c 等价）
rem    start-frt.bat --ui           显式指定图形界面（默认即此）
rem  要求: JDK 17+（实测 21 可用）
rem ============================================================
chcp 65001 >nul
title 多层级文件夹更新工具
cd /d "%~dp0"

rem 兼容两种布局：发布包内 jar 与脚本同目录；开发目录 target\
rem jar 名用通配（FRT-*.jar），升版本无需改脚本
set JAR=
for /f "delims=" %%f in ('dir /b "FRT-*.jar" 2^>nul') do set JAR=%%f
if not defined JAR (
    for /f "delims=" %%f in ('dir /b "target\FRT-*.jar" 2^>nul') do set JAR=target\%%f
)

if not defined JAR (
    echo [ERROR] jar not found: FRT-*.jar
    echo Please run: mvn -o package -DskipTests first, or use release package.
    pause
    exit /b 1
)

rem 定位 java：优先发布包自带 runtime（无 JDK 环境可用），其次系统 PATH
if exist "runtime\bin\java.exe" (
    set JAVA=runtime\bin\java.exe
) else (
    set JAVA=java
)

rem 默认启动图形界面；--console / -c 切换控制台；其余参数透传（开关参数不转发给程序）
set USE_UI=1
set FORWARD=
:parse_args
if "%~1"=="" goto run
if /i "%~1"=="--ui" goto skip_arg
if /i "%~1"=="--console" set USE_UI=0 & goto skip_arg
if /i "%~1"=="-c" set USE_UI=0 & goto skip_arg
set FORWARD=%FORWARD% "%~1"
:skip_arg
shift
goto parse_args

:run
if "%USE_UI%"=="1" (
    echo 正在启动图形界面（多层级文件夹更新工具）...
    echo 若未弹出窗口，请关闭本窗口后运行: start-frt.bat --console 进入控制台模式
    "%JAVA%" -Dfile.encoding=UTF-8 -jar "%JAR%" --ui %FORWARD%
    rem UI 关闭后程序退出，终端窗口随之自动关闭
    exit /b 0
) else (
    "%JAVA%" -Dfile.encoding=UTF-8 -jar "%JAR%" %FORWARD%
    echo.
    pause
)

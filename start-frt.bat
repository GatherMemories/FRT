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
if exist "FRT-0.1.0-SNAPSHOT.jar" (
    set JAR=FRT-0.1.0-SNAPSHOT.jar
) else (
    set JAR=target\FRT-0.1.0-SNAPSHOT.jar
)

if not exist "%JAR%" (
    echo [ERROR] jar not found: FRT-0.1.0-SNAPSHOT.jar
    echo Please run: mvn -o package -DskipTests first, or use release package.
    pause
    exit /b 1
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
    java -Dfile.encoding=UTF-8 -jar "%JAR%" --ui %FORWARD%
) else (
    java -Dfile.encoding=UTF-8 -jar "%JAR%" %FORWARD%
)

echo.
pause

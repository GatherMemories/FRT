@echo off
chcp 65001 >nul
title 多层级文件夹更新工具
rem ============================================================
rem  MultiLevel Folder Updater - launcher (Windows)
rem  UTF-8 codepage set above BEFORE any Chinese text, so cmd
rem  never parses UTF-8 bytes as GBK (that caused flash-close).
rem  Usage:
rem    start-frt.bat                 default: GUI (UI)
rem    start-frt.bat --console       console mode (-c works too)
rem    start-frt.bat --ui            explicit GUI mode
rem  Requires: JDK 17+ (bundled runtime/ included in release)
rem ============================================================
cd /d "%~dp0"

rem 兼容两种布局：发布包内 jar 与脚本同目录；开发目录 target\
rem jar 名用通配（FRT-*.jar），升版本无需改脚本
set JAR=
for /f "delims=" %%f in ('dir /b /o-d "FRT-*.jar" 2^>nul') do set JAR=%%f & goto found_jar
if not defined JAR (
    for /f "delims=" %%f in ('dir /b /o-d "target\FRT-*.jar" 2^>nul') do set JAR=target\%%f
)
:found_jar

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
rem 预检 java 可用性：不可用时报错并暂停，避免窗口一闪而过（用户看不到原因）
"%JAVA%" -version >nul 2>&1
if errorlevel 1 (
    echo.
    echo [ERROR] 未找到可用的 java！
    echo 发布包缺少 runtime\bin\java.exe 且系统未安装 JDK 17+。
    echo 请重新下载完整发布包（zip 内应含 runtime 目录），或安装 JDK 17+ 后重试。
    pause
    exit /b 1
)

if "%USE_UI%"=="1" (
    echo 正在启动图形界面（多层级文件夹更新工具）...
    echo 若未弹出窗口，请关闭本窗口后运行: start-frt.bat --console 进入控制台模式
    "%JAVA%" -Dfile.encoding=UTF-8 -jar "%JAR%" --ui %FORWARD%
    if errorlevel 1 (
        echo.
        echo [错误] 程序启动失败（退出码 %errorlevel%）！
        echo 请运行: start-frt.bat --console 查看详细错误日志（logs 目录）。
        pause
    )
    rem UI 正常关闭后，终端窗口自动关闭；启动失败时已在上方暂停提示
    exit /b 0
) else (
    "%JAVA%" -Dfile.encoding=UTF-8 -jar "%JAR%" %FORWARD%
    echo.
    pause
)

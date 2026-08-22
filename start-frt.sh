#!/usr/bin/env bash
# ============================================================
#  多层级文件夹更新工具 - 启动脚本 (Linux/macOS)
#
#  用法:
#    ./start-frt.sh              默认启动图形界面 (UI)
#    ./start-frt.sh --console    切换为控制台模式（-c 等价）
#    ./start-frt.sh --ui         显式指定图形界面（默认即此）
#    其他参数会原样透传给程序
#
#  要求: JDK 17+（与 start-frt.bat 一致，实测 21 可用）
#
#  注意: config.json 里的 baseDirectory 若还是 Windows 路径
#        （如 C:/Users/...），在 Linux 上请改为对应的绝对路径，
#        否则基于它的相对路径会解析到错误位置。
# ============================================================
set -euo pipefail

# 切换到脚本所在目录，保证 target/、config.json 等相对路径始终正确
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

JAR="target/FRT-0.1.0-SNAPSHOT.jar"

# 1. 检查可执行 jar 是否存在
if [[ ! -f "$JAR" ]]; then
    echo "[ERROR] jar 不存在: $JAR" >&2
    echo "请先构建: mvn -o package -DskipTests" >&2
    echo "（首次构建如缺依赖可去掉 -o 联网下载）" >&2
    exit 1
fi

# 2. 检查 java 命令是否可用
if ! command -v java >/dev/null 2>&1; then
    echo "[ERROR] 未找到 java 命令，请先安装 JDK 17+" >&2
    exit 1
fi

# 3. 检查 Java 主版本是否 >= 17（仅警告，不阻止运行）
JAVA_MAJOR="$(java -version 2>&1 | sed -n 's/.*version "\([0-9]*\).*/\1/p')"
if [[ -n "$JAVA_MAJOR" && "$JAVA_MAJOR" -lt 17 ]]; then
    echo "[WARN] 检测到 Java $JAVA_MAJOR，本工具要求 JDK 17+，可能无法运行" >&2
fi

# 4. 默认启动图形界面；--console / -c 切换控制台；其余参数透传
USE_UI=true
FORWARD=()
for arg in "$@"; do
    case "$arg" in
        --ui)        USE_UI=true ;;
        --console|-c) USE_UI=false ;;
        *)           FORWARD+=("$arg") ;;
    esac
done

if [[ "$USE_UI" == true ]]; then
    exec java -Dfile.encoding=UTF-8 -jar "$JAR" --ui "${FORWARD[@]}"
else
    exec java -Dfile.encoding=UTF-8 -jar "$JAR" "${FORWARD[@]}"
fi

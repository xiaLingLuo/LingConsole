#!/usr/bin/env bash
# LingConsole - A Server WebUI control panel
# Copyright (C) 2026  XIAZHIRUI HUANG
#
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU Affero General Public License as published
# by the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU Affero General Public License for more details.
#
# You should have received a copy of the GNU Affero General Public License
# along with this program.  If not, see <https://www.gnu.org/licenses/>.
# ============================================================================
#  LingConsole 一键安装脚本
#
#  用法:
#    sudo su -c "wget -qO- https://lingconsole.xzrui.cn/install.sh | bash"
#
#  - 必须以 root 运行
#  - 程序安装目录: /opt/lingConsole  (jar + 启动脚本 + config.txt)
#  - 程序配置/数据目录: /lingConsole
#  - 注册系统命令 lingconsole (start / end)
# ============================================================================

set -u

if [ "$(id -u)" != "0" ]; then
    echo "本程序需要root权限！"
    exit 1
fi

JAR_URL="https://github.com/xiaLingLuo/LingConsole/releases/download/1.2.9/LingConsole.jar"
START_SCRIPT_URL="https://raw.githubusercontent.com/xiaLingLuo/LingConsole/refs/heads/master/start/start.sh"
CONFIG_URL="https://raw.githubusercontent.com/xiaLingLuo/LingConsole/refs/heads/master/start/config.txt"
VERSION_URL="https://raw.githubusercontent.com/xiaLingLuo/LingConsole/refs/heads/master/lastver.txt"


JAR_SHA256=""
START_SCRIPT_SHA256=""
CONFIG_SHA256=""

INSTALL_DIR="/opt/lingConsole"
DATA_DIR="/lingConsole"

log()  { printf '\033[1;36m[LingConsole]\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m[LingConsole]\033[0m %s\n' "$*"; }
die()  { printf '\033[1;31m[LingConsole] ERROR: %s\n' "$*" >&2; exit 1; }

ensure_tools() {
    if ! command -v wget >/dev/null 2>&1 && ! command -v curl >/dev/null 2>&1; then
        die "未找到 wget 或 curl, 无法下载文件, 请先安装其一后重试"
    fi
}

detect_arch() {
    local arch
    arch="$(uname -m 2>/dev/null)"
    case "$arch" in
        x86_64|amd64) echo "x64" ;;
        aarch64|arm64) echo "aarch64" ;;
        armv7l|armhf|armv6l|arm) echo "arm" ;;
        ppc64le) echo "ppc64le" ;;
        s390x) echo "s390x" ;;
        riscv64) echo "riscv64" ;;
        *) echo "" ;;
    esac
}

install_java() {
    local arch
    arch="$(detect_arch)"
    if [ -z "$arch" ]; then
        die "无法自动安装 Java: 不支持的架构 $(uname -m), 请手动安装 JDK 25"
    fi
    local url tmp="/tmp/lingconsole-jdk25.tar.gz" dest="${INSTALL_DIR}/java"
    url="https://api.adoptium.net/v3/binary/latest/25/ga/linux/${arch}/jdk/hotspot/normal/eclipse"
    log "下载 JDK 25 (${arch}) ..."
    if command -v wget >/dev/null 2>&1; then
        wget -q -O "$tmp" "$url" || die "JDK 25 下载失败: ${url}"
    else
        curl -fsSL -o "$tmp" "$url" || die "JDK 25 下载失败: ${url}"
    fi
    rm -rf "$dest"
    mkdir -p "$dest"
    tar -xzf "$tmp" -C "$dest" --strip-components=1 || die "JDK 25 解压失败"
    rm -f "$tmp"
    "${dest}/bin/java" -version >/dev/null 2>&1 || die "JDK 25 安装后验证失败: ${dest}/bin/java"
    log "JDK 25 已安装到 ${dest}"
}

download_file() {
    local url="$1" dest="$2"
    if command -v wget >/dev/null 2>&1; then
        wget -q -O "$dest" "$url" || return 1
    else
        curl -fsSL -o "$dest" "$url" || return 1
    fi
}

verify_sha256() {
    local file="$1" expected="$2" label="$3"
    [ -n "$expected" ] || return 0
    local actual
    actual="$(sha256sum "$file" 2>/dev/null | awk '{print $1}')"
    if [ "$actual" != "$expected" ]; then
        rm -f "$file"
        die "${label} SHA-256 校验失败: 期望 ${expected}, 实际 ${actual}"
    fi
    log "${label} SHA-256 校验通过"
}

version_lt() {
    local a="$1" b="$2" i
    local -a av bv
    IFS='.' read -r -a av <<< "$a"
    IFS='.' read -r -a bv <<< "$b"
    for i in 0 1 2 3; do
        local x="${av[$i]:-0}" y="${bv[$i]:-0}"
        if [ "$x" -lt "$y" ]; then return 0; fi
        if [ "$x" -gt "$y" ]; then return 1; fi
    done
    return 1
}

ask_yes_no() {
    local prompt="$1" ans=""
    if [ -e /dev/tty ] && [ -r /dev/tty ]; then
        printf '%s' "$prompt"
        read -r ans < /dev/tty || ans=""
    else
        printf '%s 无终端, 默认 [N]\n' "$prompt"
        ans="N"
    fi
    case "$ans" in
        y|Y|yes|YES) return 0 ;;
        *) return 1 ;;
    esac
}

read_java_path() {
    local cfg="${INSTALL_DIR}/config.txt" key="" val="" found=""
    if [ -f "$cfg" ]; then
        while IFS='=' read -r key val || [ -n "$key" ]; do
            case "$key" in
                java_path) found=1; break ;;
            esac
        done < "$cfg"
    fi
    if [ "$found" = "1" ] && [ -n "$val" ] && [ "$val" != "default" ]; then
        echo "$val"
    else
        echo "java"
    fi
}

local_version() {
    local java_bin
    java_bin="$(read_java_path)"
    if [ "$java_bin" = "java" ]; then
        command -v java >/dev/null 2>&1 || return 0
        java_bin="$(command -v java)"
    else
        [ -x "$java_bin" ] || return 0
    fi
    "$java_bin" -jar "${INSTALL_DIR}/LingConsole.jar" --version 2>/dev/null | head -n 1
}

cloud_version() {
    local v
    if command -v wget >/dev/null 2>&1; then
        v="$(wget -q -O- "$VERSION_URL" 2>/dev/null)"
    else
        v="$(curl -fsSL "$VERSION_URL" 2>/dev/null)"
    fi
    printf '%s' "$v" | tr -d ' \t\r\n'
}

stop_lingconsole() {
    local pids
    pids="$(pgrep -f 'LingConsole.jar' 2>/dev/null || true)"
    if [ -n "$pids" ]; then
        kill $pids 2>/dev/null || true
        sleep 2
        pids="$(pgrep -f 'LingConsole.jar' 2>/dev/null || true)"
        [ -n "$pids" ] && kill -9 $pids 2>/dev/null || true
    fi
}

update_app() {
    local old="$1" new="$2"
    log "正在停止旧进程 ..."
    stop_lingconsole
    log "下载新版本 JAR ..."
    download_file "$JAR_URL" "${INSTALL_DIR}/LingConsole.jar" || die "新版本 JAR 下载失败: ${JAR_URL}"
    verify_sha256 "${INSTALL_DIR}/LingConsole.jar" "$JAR_SHA256" "JAR"
    log "已更新: ${old} -> ${new}"
    if [ -x "${INSTALL_DIR}/start.sh" ]; then
        log "重新启动 LingConsole ..."
        start_lingconsole || warn "启动失败, 请手动启动: ${INSTALL_DIR}/start.sh"
    else
        warn "start.sh 不存在, 请手动启动: ${INSTALL_DIR}/start.sh"
    fi
}

register_autostart() {
    if command -v systemctl >/dev/null 2>&1; then
        cat > /etc/systemd/system/lingconsole.service <<EOF
[Unit]
Description=LingConsole Server Control Panel
After=network.target

[Service]
Type=simple
ExecStart=${INSTALL_DIR}/start.sh
Restart=on-failure
RestartSec=5

[Install]
WantedBy=multi-user.target
EOF
        systemctl daemon-reload >/dev/null 2>&1 || true
        if systemctl enable lingconsole >/dev/null 2>&1; then
            log "已注册开机自启: systemd (lingconsole.service)"
            return 0
        fi
        warn "systemctl enable 失败, 尝试 rc.local 兜底"
    fi
    if [ -f /etc/rc.local ]; then
        if ! grep -q "lingconsole/start.sh" /etc/rc.local; then
            sed -i "\#^exit 0#i${INSTALL_DIR}/start.sh &" /etc/rc.local 2>/dev/null || true
        fi
        log "已注册开机自启: /etc/rc.local"
        return 0
    fi
    warn "无法注册开机自启, 请手动配置"
    return 1
}

start_lingconsole() {
    if command -v systemctl >/dev/null 2>&1 && [ -f /etc/systemd/system/lingconsole.service ]; then
        if systemctl start lingconsole >/dev/null 2>&1; then
            log "LingConsole 已启动, 正在等待初始密码生成"
            return 0
        fi
        warn "systemctl start 失败, 尝试直接后台启动"
    fi
    if [ -x "${INSTALL_DIR}/start.sh" ]; then
        nohup "${INSTALL_DIR}/start.sh" >/dev/null 2>&1 </dev/null &
        sleep 1
        log "LingConsole 已后台启动, 正在等待初始密码生成"
        return 0
    fi
    return 1
}

show_first_launch_password() {
    local file="${DATA_DIR}/first-launch-password.txt" i=0
    while [ "$i" -lt 30 ]; do
        [ -f "$file" ] && break
        sleep 1
        i=$((i + 1))
    done
    if [ -f "$file" ]; then
        echo
        log "首次启动已生成初始账户, 请妥善保存并立即登录修改密码:"
        sed 's/^/    /' "$file"
        log "控制面板：http://IP:55600"
        log "守护进程：ws://IP:55700"
        log "建议使用反向代理为面板及其守护进程添加TLS支持，以实现最佳安全，为了使用，需要放行55600和55700端口"
        rm -f "$file"
        echo
    else
        warn "未检测到首次启动密码文件 ${file} (非首次启动/旧版 jar 则为正常; 全新安装请确认所用 LingConsole.jar 已包含首次启动写密码功能)"
    fi
}

uninstall_app() {
    log "正在卸载 LingConsole ..."
    stop_lingconsole
    if command -v systemctl >/dev/null 2>&1; then
        systemctl disable lingconsole >/dev/null 2>&1 || true
        rm -f /etc/systemd/system/lingconsole.service
        systemctl daemon-reload >/dev/null 2>&1 || true
    fi
    if [ -f /etc/rc.local ]; then
        sed -i "\#${INSTALL_DIR}/start.sh#d" /etc/rc.local 2>/dev/null || true
    fi
    rm -f /usr/local/bin/lingconsole
    rm -rf "${INSTALL_DIR}" "${DATA_DIR}"
    log "卸载完成"
}

handle_existing_install() {
    local local_ver cloud_ver
    local_ver="$(local_version)"
    cloud_ver="$(cloud_version)"

    if [ -z "$cloud_ver" ]; then
        die "无法获取云端版本 (${VERSION_URL}), 请检查网络后重试"
    fi

    case "$local_ver" in
        [0-9]*.[0-9]*.[0-9]*) ;;
        *)
            log "无法解析本地版本 (${local_ver:-未知}), 按 0.0.0 处理"
            local_ver="0.0.0"
            ;;
    esac

    if version_lt "$local_ver" "$cloud_ver"; then
        log "检测到新版本: 本地 ${local_ver} -> 云端 ${cloud_ver}"
        if ask_yes_no "[LingConsole] 是否自动更新? [y/N]: "; then
            update_app "$local_ver" "$cloud_ver"
            exit 0
        else
            echo "已取消更新, 退出"
            exit 1
        fi
    else
        log "目前没有更新! 本地版本: ${local_ver}"
        if ask_yes_no "[LingConsole] 您是否想要卸载 LingConsole? [y/N]: "; then
            uninstall_app
            exit 0
        else
            echo "已取消, 退出"
            exit 1
        fi
    fi
}

download_jar() {
    log "下载 LingConsole.jar ..."
    download_file "$JAR_URL" "${INSTALL_DIR}/LingConsole.jar" || die "JAR 下载失败: ${JAR_URL}"
    verify_sha256 "${INSTALL_DIR}/LingConsole.jar" "$JAR_SHA256" "JAR"
}

download_start_script() {
    log "下载启动脚本 ..."
    download_file "$START_SCRIPT_URL" "${INSTALL_DIR}/start.sh" || die "启动脚本下载失败: ${START_SCRIPT_URL}"
    verify_sha256 "${INSTALL_DIR}/start.sh" "$START_SCRIPT_SHA256" "start.sh"
    chmod +x "${INSTALL_DIR}/start.sh"
}

download_config() {
    log "下载配置文件 config.txt ..."
    download_file "$CONFIG_URL" "${INSTALL_DIR}/config.txt" || die "配置文件下载失败: ${CONFIG_URL}"
    verify_sha256 "${INSTALL_DIR}/config.txt" "$CONFIG_SHA256" "config.txt"
}

register_lingconsole_command() {
    local cmd="/usr/local/bin/lingconsole"
    log "注册系统命令: ${cmd} (lingconsole start / end)"
    mkdir -p "$(dirname "$cmd")"
    cat > "$cmd" <<EOF
#!/usr/bin/env bash
# LingConsole 系统命令
INSTALL_DIR="$INSTALL_DIR"
ACTION="\${1:-}"
case "\$ACTION" in
  start)
    if command -v systemctl >/dev/null 2>&1 && [ -f /etc/systemd/system/lingconsole.service ]; then
      systemctl start lingconsole
      echo "LingConsole 已启动"
    elif [ -x "\$INSTALL_DIR/start.sh" ]; then
      if pgrep -f 'LingConsole.jar' >/dev/null 2>&1; then
        echo "LingConsole 已在运行"
      else
        nohup "\$INSTALL_DIR/start.sh" >/dev/null 2>&1 </dev/null &
        echo "LingConsole 已后台启动 (断开 SSH 不影响运行)"
      fi
    else
      echo "未找到 \$INSTALL_DIR/start.sh"; exit 1
    fi
    ;;
  end)
    if command -v systemctl >/dev/null 2>&1 && [ -f /etc/systemd/system/lingconsole.service ]; then
      systemctl stop lingconsole
      echo "LingConsole 已停止"
    else
      pids="\$(pgrep -f 'LingConsole.jar' 2>/dev/null || true)"
      if [ -n "\$pids" ]; then
        kill \$pids 2>/dev/null
        echo "LingConsole 已停止"
      else
        echo "LingConsole 未在运行"
      fi
    fi
    ;;
  *)
    echo "用法: lingconsole {start|end}"
    echo "  start  启动 LingConsole (后台, 断开 SSH 不影响运行)"
    echo "  end    停止 LingConsole"
    exit 1
    ;;
esac
EOF
    chmod +x "$cmd"
    log "已注册: lingconsole start / lingconsole end"
}

main() {
    ensure_tools

    if [ -d "$INSTALL_DIR" ] && [ -f "$INSTALL_DIR/LingConsole.jar" ]; then
        handle_existing_install
    fi

    log "开始安装 LingConsole..."

    install_java
    log "Java 25 已就绪: ${INSTALL_DIR}/java/bin/java"

    mkdir -p "${INSTALL_DIR}" || die "无法创建安装目录: ${INSTALL_DIR}"
    download_jar
    download_start_script
    download_config
    register_lingconsole_command

    mkdir -p "${DATA_DIR}" || die "无法创建数据目录: ${DATA_DIR}"
    log "已准备数据目录: ${DATA_DIR}"

    register_autostart
    start_lingconsole
    show_first_launch_password

    echo
    log "安装完成!"
    echo "  程序安装目录 : ${INSTALL_DIR}"
    echo "  数据/配置目录 : ${DATA_DIR}"
    echo "  Java         : ${INSTALL_DIR}/java/bin/java"
    echo
    echo "  服务状态  : systemctl status lingconsole (systemd 自启已注册)"
    echo "  停止服务  : sudo lingconsole end"
    echo "  启动服务  : sudo lingconsole start"
    echo "  卸载      : systemctl disable lingconsole; rm -f /usr/local/bin/lingconsole; rm -rf ${INSTALL_DIR} ${DATA_DIR}"
    echo
}

main "$@"

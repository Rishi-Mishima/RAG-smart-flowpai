#!/usr/bin/env bash

set -u

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RUNTIME_DIR="${ROOT_DIR}/.runtime"
PID_DIR="${RUNTIME_DIR}/pids"
LOG_DIR="${RUNTIME_DIR}/logs"

mkdir -p "${PID_DIR}" "${LOG_DIR}"

MINIO_DIR="/Users/itwanger/Downloads/minio1"
MINIO_API_PORT="9000"
MINIO_CONSOLE_PORT="9001"
MINIO_CMD="./minio server data/ --console-address :${MINIO_CONSOLE_PORT}"

KAFKA_DIR="/Users/itwanger/Downloads/kafka/kafka_2.13-3.9.0"
KAFKA_PORT="9092"
KAFKA_CMD="./start-kafka.sh"

ELASTICSEARCH_DIR="/Users/itwanger/Downloads/elasticsearch-8.10.0"
ELASTICSEARCH_PORT="9200"
ELASTICSEARCH_SCHEME="${ELASTICSEARCH_SCHEME:-https}"
ELASTICSEARCH_CMD='ES_JAVA_OPTS="-Xms500M -Xmx500M" ./bin/elasticsearch'

SERVICES=("minio" "kafka" "elasticsearch")

service_dir() {
    case "$1" in
        minio) echo "${MINIO_DIR}" ;;
        kafka) echo "${KAFKA_DIR}" ;;
        elasticsearch) echo "${ELASTICSEARCH_DIR}" ;;
        *) return 1 ;;
    esac
}

service_cmd() {
    case "$1" in
        minio) echo "${MINIO_CMD}" ;;
        kafka) echo "${KAFKA_CMD}" ;;
        elasticsearch) echo "${ELASTICSEARCH_CMD}" ;;
        *) return 1 ;;
    esac
}

pid_file() {
    echo "${PID_DIR}/$1.pid"
}

log_file() {
    echo "${LOG_DIR}/$1.log"
}

service_port() {
    case "$1" in
        minio) echo "${MINIO_API_PORT}" ;;
        kafka) echo "${KAFKA_PORT}" ;;
        elasticsearch) echo "${ELASTICSEARCH_PORT}" ;;
        *) return 1 ;;
    esac
}

service_aux_port() {
    case "$1" in
        minio) echo "${MINIO_CONSOLE_PORT}" ;;
        *) return 1 ;;
    esac
}

service_pattern() {
    case "$1" in
        minio) echo 'minio server data/' ;;
        kafka) echo 'kafka\.Kafka|start-kafka\.sh' ;;
        elasticsearch) echo 'org\.elasticsearch\.bootstrap\.Elasticsearch|org\.elasticsearch\.server|jdk\.module\.main=org\.elasticsearch\.server|elasticsearch-8\.10\.0' ;;
        *) return 1 ;;
    esac
}

discover_pid() {
    local service="$1"
    local pattern
    pattern="$(service_pattern "${service}")" || return 1

    local pid
    pid="$(pgrep -f "${pattern}" | head -n 1)"
    if [ -n "${pid}" ]; then
        echo "${pid}"
        return 0
    fi

    return 1
}

discover_pids() {
    local service="$1"
    local pattern
    pattern="$(service_pattern "${service}")" || return 1
    pgrep -f "${pattern}" || true
}

port_is_listening() {
    local port="$1"
    if lsof -nP -iTCP:"${port}" -sTCP:LISTEN > /dev/null 2>&1; then
        return 0
    fi
    return 1
}

http_ok() {
    local url="$1"
    if curl --silent --fail --max-time 2 "${url}" > /dev/null 2>&1; then
        return 0
    fi
    return 1
}

java_major_version() {
    local java_bin="$1"
    local version_line version
    version_line="$("${java_bin}" -version 2>&1 | head -n 1)"
    version="$(printf '%s\n' "${version_line}" | sed -E 's/.*version "([^"]+)".*/\1/')"

    if [[ "${version}" == 1.* ]]; then
        echo "${version#1.}" | cut -d. -f1
        return 0
    fi

    echo "${version}" | cut -d. -f1
}

detect_java_home() {
    local project_java_version project_java_home

    if [ -f "${ROOT_DIR}/.java-version" ] && command -v jenv > /dev/null 2>&1; then
        project_java_version="$(tr -d '[:space:]' < "${ROOT_DIR}/.java-version")"
        if [ -n "${project_java_version}" ]; then
            project_java_home="$(jenv prefix "${project_java_version}" 2>/dev/null || true)"
            if [ -n "${project_java_home}" ] && [ -x "${project_java_home}/bin/java" ]; then
                echo "${project_java_home}"
                return 0
            fi
        fi
    fi

    if [ -n "${JAVA_HOME:-}" ] && [ -x "${JAVA_HOME}/bin/java" ]; then
        local current_major
        current_major="$(java_major_version "${JAVA_HOME}/bin/java")"
        if [ "${current_major}" -ge 17 ]; then
            echo "${JAVA_HOME}"
            return 0
        fi
    fi

    if command -v /usr/libexec/java_home > /dev/null 2>&1; then
        local detected_home
        detected_home="$(/usr/libexec/java_home -v 17+ 2>/dev/null || true)"
        if [ -n "${detected_home}" ] && [ -x "${detected_home}/bin/java" ]; then
            echo "${detected_home}"
            return 0
        fi
    fi

    return 1
}

elastic_http_status() {
    local scheme="$1"
    curl \
        --silent \
        --output /dev/null \
        --write-out '%{http_code}' \
        --max-time 2 \
        --insecure \
        "${scheme}://127.0.0.1:${ELASTICSEARCH_PORT}" 2>/dev/null || true
}

elastic_health_check() {
    local status

    if [ "${ELASTICSEARCH_SCHEME}" = "https" ]; then
        status="$(elastic_http_status "https")"
        case "${status}" in
            200|401|403) return 0 ;;
        esac
        status="$(elastic_http_status "http")"
        case "${status}" in
            200|401|403) return 0 ;;
        esac
        port_is_listening "${ELASTICSEARCH_PORT}" && return 0
        return 1
    fi

    status="$(elastic_http_status "http")"
    case "${status}" in
        200|401|403) return 0 ;;
    esac
    status="$(elastic_http_status "https")"
    case "${status}" in
        200|401|403) return 0 ;;
    esac
    port_is_listening "${ELASTICSEARCH_PORT}" && return 0
    return 1
}

http_health_check() {
    local service="$1"
    case "${service}" in
        minio)
            http_ok "http://127.0.0.1:${MINIO_API_PORT}/minio/health/live" || return 1
            http_ok "http://127.0.0.1:${MINIO_CONSOLE_PORT}" || return 1
            ;;
        elasticsearch)
            elastic_health_check || return 1
            ;;
        kafka)
            port_is_listening "${KAFKA_PORT}" || return 1
            ;;
        *)
            return 1
            ;;
    esac

    return 0
}

wait_for_service() {
    local service="$1"
    local i
    for i in $(seq 1 30); do
        if http_health_check "${service}"; then
            return 0
        fi
        sleep 1
    done

    return 1
}

is_running() {
    local service="$1"
    if http_health_check "${service}"; then
        local discovered_pid
        discovered_pid="$(discover_pid "${service}" || true)"
        if [ -n "${discovered_pid}" ]; then
            echo "${discovered_pid}" > "$(pid_file "${service}")"
        fi
        return 0
    fi

    local pid_path
    pid_path="$(pid_file "${service}")"

    if [ ! -f "${pid_path}" ]; then
        return 1
    fi

    local pid
    pid="$(cat "${pid_path}")"
    if [ -z "${pid}" ]; then
        return 1
    fi

    if ps -p "${pid}" > /dev/null 2>&1; then
        return 0
    fi

    local discovered_pid
    discovered_pid="$(discover_pid "${service}" || true)"
    if [ -n "${discovered_pid}" ]; then
        echo "${discovered_pid}" > "${pid_path}"
        return 0
    fi

    rm -f "${pid_path}"
    return 1
}

validate_service() {
    local service="$1"
    local dir
    dir="$(service_dir "${service}")" || {
        echo "不支持的服务: ${service}"
        return 1
    }

    if [ ! -d "${dir}" ]; then
        echo "${service} 目录不存在: ${dir}"
        return 1
    fi

    if [ "${service}" = "elasticsearch" ] && ! detect_java_home > /dev/null; then
        echo "启动 Elasticsearch 需要 Java 17+，但当前未找到可用的 JDK"
        return 1
    fi

    return 0
}

start_service() {
    local service="$1"
    validate_service "${service}" || return 1

    if is_running "${service}"; then
        echo "${service} 已在运行，PID: $(cat "$(pid_file "${service}")")"
        return 0
    fi

    local dir cmd log_path pid_path
    dir="$(service_dir "${service}")"
    cmd="$(service_cmd "${service}")"
    log_path="$(log_file "${service}")"
    pid_path="$(pid_file "${service}")"

    echo "启动 ${service}..."
    (
        cd "${dir}" || exit 1
        if [ "${service}" = "elasticsearch" ]; then
            export ES_JAVA_HOME
            ES_JAVA_HOME="$(detect_java_home)" || exit 1
        fi
        if command -v setsid > /dev/null 2>&1; then
            nohup setsid bash -lc "${cmd}" >> "${log_path}" 2>&1 < /dev/null &
        else
            nohup bash -lc "${cmd}" >> "${log_path}" 2>&1 < /dev/null &
        fi
        echo $! > "${pid_path}"
    )

    if wait_for_service "${service}"; then
        local discovered_pid
        discovered_pid="$(discover_pid "${service}" || true)"
        if [ -n "${discovered_pid}" ]; then
            echo "${discovered_pid}" > "${pid_path}"
        fi
    fi

    if is_running "${service}"; then
        echo "${service} 启动成功，PID: $(cat "${pid_path}")"
        echo "日志: ${log_path}"
        return 0
    fi

    echo "${service} 启动失败，请检查日志: ${log_path}"
    return 1
}

stop_service() {
    local service="$1"
    local pid_path
    pid_path="$(pid_file "${service}")"

    if ! is_running "${service}"; then
        echo "${service} 未运行"
        return 0
    fi

    local pid
    pid="$(cat "${pid_path}")"
    if [ -z "${pid}" ]; then
        pid="$(discover_pid "${service}" || true)"
    fi

    if [ -z "${pid}" ]; then
        echo "${service} 未运行"
        rm -f "${pid_path}"
        return 0
    fi

    local pids
    pids="$(discover_pids "${service}")"
    if [ -z "${pids}" ]; then
        pids="${pid}"
    fi

    echo "停止 ${service} (PID: ${pids//$'\n'/ })..."
    while IFS= read -r current_pid; do
        [ -n "${current_pid}" ] || continue
        kill "${current_pid}" 2>/dev/null || true
    done <<< "${pids}"

    for _ in 1 2 3 4 5; do
        if ! is_running "${service}"; then
            rm -f "${pid_path}"
            echo "${service} 已停止"
            return 0
        fi
        sleep 1
    done

    echo "${service} 未在预期时间内退出，执行强制停止"
    while IFS= read -r current_pid; do
        [ -n "${current_pid}" ] || continue
        kill -9 "${current_pid}" 2>/dev/null || true
    done <<< "${pids}"
    rm -f "${pid_path}"
    echo "${service} 已强制停止"
}

status_service() {
    local service="$1"
    if is_running "${service}"; then
        local pid port aux_port
        pid="$(cat "$(pid_file "${service}")" 2>/dev/null || true)"
        port="$(service_port "${service}" || true)"
        aux_port="$(service_aux_port "${service}" || true)"
        if [ -n "${aux_port}" ]; then
            echo "${service}: 运行中 (PID: ${pid:-unknown}, Port: ${port:-unknown}, Extra: ${aux_port})"
        else
            echo "${service}: 运行中 (PID: ${pid:-unknown}, Port: ${port:-unknown})"
        fi
    else
        echo "${service}: 未运行"
    fi
}

logs_service() {
    local service="$1"
    local log_path
    log_path="$(log_file "${service}")"

    if [ ! -f "${log_path}" ]; then
        echo "${service} 暂无日志: ${log_path}"
        return 1
    fi

    tail -f "${log_path}"
}

show_urls() {
    cat <<EOF
MinIO API:        http://127.0.0.1:${MINIO_API_PORT}
MinIO Console:    http://127.0.0.1:${MINIO_CONSOLE_PORT}
Kafka Broker:     127.0.0.1:${KAFKA_PORT}
Elasticsearch:    ${ELASTICSEARCH_SCHEME}://127.0.0.1:${ELASTICSEARCH_PORT}
EOF
}

run_for_services() {
    local action="$1"
    shift

    local selected_services=()
    if [ "$#" -eq 0 ]; then
        selected_services=("${SERVICES[@]}")
    else
        selected_services=("$@")
    fi

    local service
    for service in "${selected_services[@]}"; do
        case "${action}" in
            start) start_service "${service}" ;;
            stop) stop_service "${service}" ;;
            status) status_service "${service}" ;;
            restart)
                stop_service "${service}"
                start_service "${service}"
                ;;
            *) return 1 ;;
        esac
        echo ""
    done
}

show_help() {
    cat <<'EOF'
用法:
  ./infra.sh start [service...]
  ./infra.sh stop [service...]
  ./infra.sh restart [service...]
  ./infra.sh status [service...]
  ./infra.sh logs <service>
  ./infra.sh urls

可选服务:
  minio
  kafka
  elasticsearch

示例:
  ./infra.sh start
  ./infra.sh start minio kafka
  ./infra.sh stop elasticsearch
  ./infra.sh status
  ./infra.sh logs kafka
  ./infra.sh urls
EOF
}

main() {
    local command="${1:-}"
    case "${command}" in
        start|stop|restart|status)
            shift
            run_for_services "${command}" "$@"
            ;;
        logs)
            if [ $# -lt 2 ]; then
                echo "请指定要查看日志的服务"
                exit 1
            fi
            logs_service "$2"
            ;;
        urls)
            show_urls
            ;;
        -h|--help|help|"")
            show_help
            ;;
        *)
            echo "不支持的命令: ${command}"
            echo ""
            show_help
            exit 1
            ;;
    esac
}

main "$@"

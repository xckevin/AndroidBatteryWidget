#!/system/bin/sh

# Read-only, on-device recorder for a real unplugged/screen-off standby observation.
# Run under toybox nohup before disconnecting USB. This script deliberately does not
# change battery simulation, clocks, idle state, wake locks, radios, power saver, or apps.

umask 077
set -o pipefail

PACKAGE="com.github.xckevin927.android.battery.widget"
SERVICE="$PACKAGE/.service.WidgetUpdateService"
DEFAULT_DURATION_SECONDS=7200
DEFAULT_INTERVAL_SECONDS=300
WAIT_INTERVAL_SECONDS=1
DUMPSYS_TIMEOUT_SECONDS=20
BATTERYSTATS_TIMEOUT_SECONDS=60

MODE=record
if [ "${1:-}" = "--probe" ]; then
    MODE=probe
    shift
fi
OUT_DIR=${1:-}
REQUESTED_DURATION=${2:-$DEFAULT_DURATION_SECONDS}
REQUESTED_INTERVAL=${3:-$DEFAULT_INTERVAL_SECONDS}

usage() {
    echo "usage: $0 [--probe] /data/local/tmp/independent-directory [duration-seconds] [sample-interval-seconds]" >&2
    exit 2
}

case "$OUT_DIR" in
    /data/local/tmp/?*) ;;
    *) usage ;;
esac

case "$REQUESTED_DURATION" in
    ''|*[!0-9]*) usage ;;
esac
case "$REQUESTED_INTERVAL" in
    ''|*[!0-9]*) usage ;;
esac
[ "$REQUESTED_DURATION" -gt 0 ] || usage
[ "$REQUESTED_INTERVAL" -gt 0 ] || usage

mkdir -p "$OUT_DIR" || exit 1
chmod 700 "$OUT_DIR" || exit 1

STATE_FILE="$OUT_DIR/state.env"
META_FILE="$OUT_DIR/protocol.env"
SAMPLES_FILE="$OUT_DIR/samples.tsv"
EVENTS_FILE="$OUT_DIR/events.tsv"
PID_FILE="$OUT_DIR/observer.pid"

now_epoch() { date +%s; }
now_iso() { date -u +%Y-%m-%dT%H:%M:%SZ; }

one_line() {
    printf '%s' "$1" | tr '\t\r\n' '   '
}

event() {
    EVENT_NAME=$1
    EVENT_DETAIL=${2:-}
    printf '%s\t%s\t%s\t%s\n' "$(now_epoch)" "$(now_iso)" "$EVENT_NAME" "$(one_line "$EVENT_DETAIL")" >> "$EVENTS_FILE"
}

read_env_value() {
    KEY=$1
    FILE=$2
    sed -n "s/^${KEY}=//p" "$FILE" 2>/dev/null | tail -n 1
}

write_state() {
    PHASE=$1
    REASON=$2
    ENDED=${3:-}
    TMP="$STATE_FILE.tmp.$$"
    {
        echo "phase=$PHASE"
        echo "reason=$(one_line "$REASON")"
        echo "observer_pid=$$"
        echo "updated_wall_epoch=$(now_epoch)"
        echo "updated_wall_iso=$(now_iso)"
        echo "protocol_start_epoch=${PROTOCOL_START_EPOCH:-}"
        echo "deadline_epoch=${DEADLINE_EPOCH:-}"
        echo "last_sample_epoch=${LAST_SAMPLE_EPOCH:-}"
        echo "ended_wall_epoch=$ENDED"
        echo "target_duration_seconds=${DURATION_SECONDS:-$REQUESTED_DURATION}"
        echo "sample_interval_seconds=${INTERVAL_SECONDS:-$REQUESTED_INTERVAL}"
    } > "$TMP"
    chmod 600 "$TMP"
    mv "$TMP" "$STATE_FILE"
}

# Refuse a concurrent recorder, while allowing the same directory to recover after a killed shell.
if [ -f "$PID_FILE" ]; then
    OLD_PID=$(sed -n '1p' "$PID_FILE" 2>/dev/null)
    case "$OLD_PID" in
        ''|*[!0-9]*) OLD_PID= ;;
    esac
    if [ -n "$OLD_PID" ] && kill -0 "$OLD_PID" 2>/dev/null; then
        OLD_COMMAND=$(tr '\000' ' ' < "/proc/$OLD_PID/cmdline" 2>/dev/null)
        case "$OLD_COMMAND" in
            *samsung_standby_recorder.sh*)
                echo "recorder already running as pid $OLD_PID" >&2
                exit 3
                ;;
        esac
    fi
fi
echo $$ > "$PID_FILE"
chmod 600 "$PID_FILE"
cleanup_pid() {
    CURRENT_PID=$(sed -n '1p' "$PID_FILE" 2>/dev/null)
    [ "$CURRENT_PID" = "$$" ] && rm -f "$PID_FILE"
}
handle_signal() {
    SIGNAL_NAME=$1
    SIGNAL_NOW=$(now_epoch)
    if [ -n "${PROTOCOL_START_EPOCH:-}" ]; then
        write_state incomplete "observer_signal_$SIGNAL_NAME" "$SIGNAL_NOW"
        event observer_incomplete "signal=$SIGNAL_NAME"
    else
        write_state waiting "observer_signal_$SIGNAL_NAME"
        event observer_stopped_before_start "signal=$SIGNAL_NAME"
    fi
    exit 128
}
trap cleanup_pid EXIT
trap 'handle_signal HUP' HUP
trap 'handle_signal INT' INT
trap 'handle_signal TERM' TERM

if [ ! -f "$EVENTS_FILE" ]; then
    printf 'wall_epoch\twall_iso\tevent\tdetail\n' > "$EVENTS_FILE"
fi
if [ ! -f "$SAMPLES_FILE" ]; then
    printf '%s\n' 'wall_epoch	wall_iso	phase	wall_elapsed_s	gap_s	uptime_s	battery_ac	battery_usb	battery_wireless	battery_status	battery_level	battery_scale	charge_counter_uah	temperature_tenths_c	power_wakefulness	power_is_powered	power_plug_type	stay_on_while_plugged	interactive	deviceidle_deep	deviceidle_light	package_pid	process_start_ticks	service_present	monitoring_enabled	monitoring_running	service_screen_interactive	refresh_count	last_refresh_elapsed_ms	service_battery_level	last_start_error	sample_errors' > "$SAMPLES_FILE"
fi

capture_battery() {
    BATTERY_OUTPUT=$(/system/bin/timeout "$DUMPSYS_TIMEOUT_SECONDS" dumpsys battery 2>/dev/null | sed -n '1,40p')
    BATTERY_RC=$?
    BATTERY_HEAD=$(printf '%s\n' "$BATTERY_OUTPUT" | sed -n '1,40p')
    B_AC=$(printf '%s\n' "$BATTERY_HEAD" | sed -n 's/^[[:space:]]*AC powered:[[:space:]]*//p' | head -n 1)
    B_USB=$(printf '%s\n' "$BATTERY_HEAD" | sed -n 's/^[[:space:]]*USB powered:[[:space:]]*//p' | head -n 1)
    B_WIRELESS=$(printf '%s\n' "$BATTERY_HEAD" | sed -n 's/^[[:space:]]*Wireless powered:[[:space:]]*//p' | head -n 1)
    B_STATUS=$(printf '%s\n' "$BATTERY_HEAD" | sed -n 's/^[[:space:]]*status:[[:space:]]*//p' | head -n 1)
    B_LEVEL=$(printf '%s\n' "$BATTERY_HEAD" | sed -n 's/^[[:space:]]*level:[[:space:]]*//p' | head -n 1)
    B_SCALE=$(printf '%s\n' "$BATTERY_HEAD" | sed -n 's/^[[:space:]]*scale:[[:space:]]*//p' | head -n 1)
    B_COUNTER=$(printf '%s\n' "$BATTERY_HEAD" | sed -n 's/^[[:space:]]*Charge counter:[[:space:]]*//p' | head -n 1)
    B_TEMP=$(printf '%s\n' "$BATTERY_HEAD" | sed -n 's/^[[:space:]]*temperature:[[:space:]]*//p' | head -n 1)
}

capture_power() {
    # Samsung's full dump can exceed the shell's single-argument limit; filter in the pipe.
    POWER_OUTPUT=$(/system/bin/timeout "$DUMPSYS_TIMEOUT_SECONDS" dumpsys power 2>/dev/null | sed -n \
        '/^[[:space:]]*mWakefulness=/p; /^[[:space:]]*mIsPowered=/p; /^[[:space:]]*mPlugType=/p; /^[[:space:]]*mStayOnWhilePluggedInSetting=/p; /^[[:space:]]*mInteractive=/p')
    POWER_RC=$?
    P_WAKEFULNESS=$(printf '%s\n' "$POWER_OUTPUT" | sed -n 's/^[[:space:]]*mWakefulness=//p' | head -n 1)
    P_POWERED=$(printf '%s\n' "$POWER_OUTPUT" | sed -n 's/^[[:space:]]*mIsPowered=//p' | head -n 1)
    P_PLUG_TYPE=$(printf '%s\n' "$POWER_OUTPUT" | sed -n 's/^[[:space:]]*mPlugType=//p' | head -n 1)
    P_STAY_ON=$(printf '%s\n' "$POWER_OUTPUT" | sed -n 's/^[[:space:]]*mStayOnWhilePluggedInSetting=//p' | head -n 1)
    P_INTERACTIVE=$(printf '%s\n' "$POWER_OUTPUT" | sed -n 's/^[[:space:]]*mInteractive=//p' | head -n 1)
    if [ -z "$P_INTERACTIVE" ]; then
        case "$P_WAKEFULNESS" in
            Awake|Dreaming) P_INTERACTIVE=true ;;
            Asleep|Dozing) P_INTERACTIVE=false ;;
            *) P_INTERACTIVE=unknown ;;
        esac
    fi
}

conditions_ready() {
    [ "$BATTERY_RC" -eq 0 ] || return 1
    [ "$POWER_RC" -eq 0 ] || return 1
    [ "$B_AC" = "false" ] || return 1
    [ "$B_USB" = "false" ] || return 1
    [ "$B_WIRELESS" = "false" ] || return 1
    [ "$P_POWERED" = "false" ] || return 1
    [ "$P_PLUG_TYPE" = "0" ] || return 1
    [ "$P_INTERACTIVE" = "false" ] || return 1
    return 0
}

capture_scoped_artifacts() {
    LABEL=$1
    /system/bin/timeout "$BATTERYSTATS_TIMEOUT_SECONDS" dumpsys batterystats --charged "$PACKAGE" > "$OUT_DIR/batterystats-$LABEL.txt" 2>&1
    BS_RC=$?
    /system/bin/timeout "$DUMPSYS_TIMEOUT_SECONDS" logcat -d -v epoch -t 1000 \
        'WidgetUpdateService:V' 'BatteryRepo:V' 'BootReceiver:V' \
        'BatteryWorker:V' 'WM-WorkerWrapper:V' '*:S' > "$OUT_DIR/logcat-$LABEL.txt" 2>&1
    LOG_RC=$?
    event "scoped_artifacts_$LABEL" "batterystats_rc=$BS_RC logcat_rc=$LOG_RC"
}

capture_sample() {
    SAMPLE_PHASE=$1
    SAMPLE_NOW=$(now_epoch)
    SAMPLE_ISO=$(now_iso)
    SAMPLE_ERRORS=

    capture_battery
    [ "$BATTERY_RC" -eq 0 ] || SAMPLE_ERRORS="battery_rc=$BATTERY_RC"
    capture_power
    if [ "$POWER_RC" -ne 0 ]; then
        [ -n "$SAMPLE_ERRORS" ] && SAMPLE_ERRORS="$SAMPLE_ERRORS;"
        SAMPLE_ERRORS="${SAMPLE_ERRORS}power_rc=$POWER_RC"
    fi

    DEEP=$(/system/bin/timeout "$DUMPSYS_TIMEOUT_SECONDS" cmd deviceidle get deep 2>/dev/null)
    DEEP_RC=$?
    LIGHT=$(/system/bin/timeout "$DUMPSYS_TIMEOUT_SECONDS" cmd deviceidle get light 2>/dev/null)
    LIGHT_RC=$?
    if [ "$DEEP_RC" -ne 0 ] || [ "$LIGHT_RC" -ne 0 ]; then
        [ -n "$SAMPLE_ERRORS" ] && SAMPLE_ERRORS="$SAMPLE_ERRORS;"
        SAMPLE_ERRORS="${SAMPLE_ERRORS}deviceidle_rc=$DEEP_RC/$LIGHT_RC"
    fi

    PACKAGE_PID=$(/system/bin/timeout 5 pidof "$PACKAGE" 2>/dev/null)
    PROCESS_START_TICKS=
    case "$PACKAGE_PID" in
        *' '*) FIRST_PID=$(printf '%s\n' "$PACKAGE_PID" | awk '{print $1}') ;;
        *) FIRST_PID=$PACKAGE_PID ;;
    esac
    if [ -n "$FIRST_PID" ] && [ -r "/proc/$FIRST_PID/stat" ]; then
        PROCESS_START_TICKS=$(sed 's/^[^)]*) //' "/proc/$FIRST_PID/stat" 2>/dev/null | awk '{print $20}')
    fi

    SERVICE_OUTPUT=$(/system/bin/timeout "$DUMPSYS_TIMEOUT_SECONDS" dumpsys activity service "$SERVICE" 2>/dev/null)
    SERVICE_RC=$?
    S_PRESENT=
    if [ "$SERVICE_RC" -eq 0 ]; then
        if printf '%s\n' "$SERVICE_OUTPUT" | grep -q '^[[:space:]]*monitoringEnabled='; then
            S_PRESENT=1
        elif printf '%s\n' "$SERVICE_OUTPUT" | grep -q 'No services match'; then
            S_PRESENT=0
        else
            SAMPLE_ERRORS="${SAMPLE_ERRORS};service_dump_unreadable"
        fi
    fi
    S_ENABLED=$(printf '%s\n' "$SERVICE_OUTPUT" | sed -n 's/^[[:space:]]*monitoringEnabled=//p' | head -n 1)
    S_RUNNING=$(printf '%s\n' "$SERVICE_OUTPUT" | sed -n 's/^[[:space:]]*monitoringRunning=//p' | head -n 1)
    S_SCREEN=$(printf '%s\n' "$SERVICE_OUTPUT" | sed -n 's/^[[:space:]]*screenInteractive=//p' | head -n 1)
    S_COUNT=$(printf '%s\n' "$SERVICE_OUTPUT" | sed -n 's/^[[:space:]]*refreshCount=//p' | head -n 1)
    S_REFRESH_ELAPSED=$(printf '%s\n' "$SERVICE_OUTPUT" | sed -n 's/^[[:space:]]*lastRefreshElapsedRealtime=//p' | head -n 1)
    S_BATTERY=$(printf '%s\n' "$SERVICE_OUTPUT" | sed -n 's/^[[:space:]]*batteryLevel=//p' | head -n 1)
    S_ERROR=$(printf '%s\n' "$SERVICE_OUTPUT" | sed -n 's/^[[:space:]]*lastStartError=//p' | head -n 1)
    if [ "$SERVICE_RC" -ne 0 ]; then
        [ -n "$SAMPLE_ERRORS" ] && SAMPLE_ERRORS="$SAMPLE_ERRORS;"
        SAMPLE_ERRORS="${SAMPLE_ERRORS}service_rc=$SERVICE_RC"
    fi

    UPTIME_SECONDS=$(sed -n 's/[[:space:]].*//p' /proc/uptime 2>/dev/null)
    WALL_ELAPSED=
    [ -n "${PROTOCOL_START_EPOCH:-}" ] && WALL_ELAPSED=$((SAMPLE_NOW - PROTOCOL_START_EPOCH))
    GAP_SECONDS=
    [ -n "${LAST_SAMPLE_EPOCH:-}" ] && GAP_SECONDS=$((SAMPLE_NOW - LAST_SAMPLE_EPOCH))

    printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' \
        "$SAMPLE_NOW" "$SAMPLE_ISO" "$SAMPLE_PHASE" "$WALL_ELAPSED" "$GAP_SECONDS" "$UPTIME_SECONDS" \
        "$B_AC" "$B_USB" "$B_WIRELESS" "$B_STATUS" "$B_LEVEL" "$B_SCALE" "$B_COUNTER" "$B_TEMP" \
        "$P_WAKEFULNESS" "$P_POWERED" "$P_PLUG_TYPE" "$P_STAY_ON" "$P_INTERACTIVE" \
        "$(one_line "$DEEP")" "$(one_line "$LIGHT")" "$(one_line "$PACKAGE_PID")" "$PROCESS_START_TICKS" \
        "$S_PRESENT" "$S_ENABLED" "$S_RUNNING" "$S_SCREEN" "$S_COUNT" "$S_REFRESH_ELAPSED" "$S_BATTERY" \
        "$(one_line "$S_ERROR")" "$(one_line "$SAMPLE_ERRORS")" >> "$SAMPLES_FILE"

    LAST_SAMPLE_EPOCH=$SAMPLE_NOW
}

finish_protocol() {
    FINAL_PHASE=$1
    FINAL_REASON=$2
    FINAL_NOW=$(now_epoch)
    capture_scoped_artifacts end
    write_state "$FINAL_PHASE" "$FINAL_REASON" "$FINAL_NOW"
    event "$FINAL_PHASE" "$FINAL_REASON actual_wall_seconds=$((FINAL_NOW - PROTOCOL_START_EPOCH))"
    exit 0
}

if [ "$MODE" = "probe" ]; then
    [ ! -f "$META_FILE" ] || { echo 'probe requires a separate directory' >&2; exit 2; }
    capture_sample probe
    write_state probe diagnostic_only_no_standby_started "$(now_epoch)"
    event probe_finished diagnostic_only
    exit 0
fi

if [ -f "$META_FILE" ]; then
    PROTOCOL_START_EPOCH=$(read_env_value protocol_start_epoch "$META_FILE")
    DEADLINE_EPOCH=$(read_env_value deadline_epoch "$META_FILE")
    DURATION_SECONDS=$(read_env_value target_duration_seconds "$META_FILE")
    INTERVAL_SECONDS=$(read_env_value sample_interval_seconds "$META_FILE")
    PRIOR_PHASE=$(read_env_value phase "$STATE_FILE")
    case "$PRIOR_PHASE" in
        complete|interrupted|incomplete|error)
            echo "protocol already finalized as $PRIOR_PHASE in $OUT_DIR" >&2
            exit 4
            ;;
    esac
    LAST_SAMPLE_EPOCH=$(tail -n 1 "$SAMPLES_FILE" 2>/dev/null | cut -f 1)
    case "$LAST_SAMPLE_EPOCH" in
        ''|*[!0-9]*) LAST_SAMPLE_EPOCH= ;;
    esac
    event observer_resumed "previous_phase=$PRIOR_PHASE"
    write_state running observer_resumed
else
    DURATION_SECONDS=$REQUESTED_DURATION
    INTERVAL_SECONDS=$REQUESTED_INTERVAL
    PROTOCOL_START_EPOCH=
    DEADLINE_EPOCH=
    LAST_SAMPLE_EPOCH=
    write_state waiting waiting_for_real_unplug_and_noninteractive
    event observer_started "duration=$DURATION_SECONDS interval=$INTERVAL_SECONDS"
    capture_sample waiting
    capture_scoped_artifacts armed

    while :; do
        capture_battery
        capture_power
        if conditions_ready; then
            break
        fi
        WAIT_DETAIL="ac=$B_AC usb=$B_USB wireless=$B_WIRELESS powered=$P_POWERED plugType=$P_PLUG_TYPE interactive=$P_INTERACTIVE wakefulness=$P_WAKEFULNESS battery_rc=$BATTERY_RC power_rc=$POWER_RC"
        write_state waiting "$WAIT_DETAIL"
        sleep "$WAIT_INTERVAL_SECONDS"
    done

    PROTOCOL_START_EPOCH=$(now_epoch)
    DEADLINE_EPOCH=$((PROTOCOL_START_EPOCH + DURATION_SECONDS))
    {
        echo "format_version=1"
        echo "package=$PACKAGE"
        echo "service=$SERVICE"
        echo "protocol_start_epoch=$PROTOCOL_START_EPOCH"
        echo "protocol_start_iso=$(now_iso)"
        echo "deadline_epoch=$DEADLINE_EPOCH"
        echo "target_duration_seconds=$DURATION_SECONDS"
        echo "sample_interval_seconds=$INTERVAL_SECONDS"
        echo "start_requirements=ac_false,usb_false,wireless_false,power_false,plug_type_0,interactive_false"
    } > "$META_FILE"
    chmod 600 "$META_FILE"
    event protocol_started "deadline=$DEADLINE_EPOCH"
    write_state running protocol_started
    capture_sample running
    capture_scoped_artifacts start
fi

while :; do
    LOOP_NOW=$(now_epoch)
    if [ "$LOOP_NOW" -ge "$DEADLINE_EPOCH" ]; then
        # If deep sleep delayed this shell, record the late observation without extending the
        # nominal protocol window or automatically waking the device.
        capture_sample post_deadline
        finish_protocol complete "deadline_observed;observer_delay_seconds=$((LOOP_NOW - DEADLINE_EPOCH))"
    fi

    capture_sample running
    if ! conditions_ready; then
        VIOLATION="ac=$B_AC usb=$B_USB wireless=$B_WIRELESS powered=$P_POWERED plugType=$P_PLUG_TYPE interactive=$P_INTERACTIVE wakefulness=$P_WAKEFULNESS"
        finish_protocol interrupted "standby_condition_changed;$VIOLATION"
    fi
    write_state running sampled

    REMAINING=$((DEADLINE_EPOCH - LAST_SAMPLE_EPOCH))
    [ "$REMAINING" -le 0 ] && continue
    SLEEP_SECONDS=$INTERVAL_SECONDS
    [ "$REMAINING" -lt "$SLEEP_SECONDS" ] && SLEEP_SECONDS=$REMAINING
    sleep "$SLEEP_SECONDS"
done

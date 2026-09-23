#!/system/bin/sh
set -u
MODDIR=${0%/*}
BB=/data/adb/ksu/bin/busybox
boot_id=$(cat /proc/sys/kernel/random/boot_id)
restore_bt=0
changed=0
xml_changed=0
status() { printf 'boot=%s\nstage=service\nstate=%s\n' "$boot_id" "$1" > "$MODDIR/status.txt"; }
cleanup() {
    if [ "$restore_bt" = 1 ]; then /system/bin/cmd bluetooth_manager enable >> "$MODDIR/boot.log" 2>&1; fi
}
fail() { echo "$*" >> "$MODDIR/boot.log"; status "failed: $*"; exit 1; }
trap cleanup EXIT
trap 'exit 1' HUP INT TERM
attempt=0
while [ "$(/system/bin/getprop sys.boot_completed)" != 1 ]; do
    [ "$attempt" -lt 120 ] || fail 'boot completion timeout'
    sleep 1
    attempt=$((attempt + 1))
done
[ ! -e "$MODDIR/disable" ] && [ ! -e "$MODDIR/remove" ] || exit 0

# Remember whether an already-running audio HAL needs to reload its XML.
while IFS='|' read -r kind original patched relative target context uid gid mode; do
    [ "$kind" = vendor ] || continue
    result=$(/system/bin/nsenter -t 1 -m -- "$BB" sha256sum "$target" 2>> "$MODDIR/boot.log") || fail "cannot inspect $target"
    [ "${result%% *}" = "$patched" ] || xml_changed=1
done < "$MODDIR/manifest.txt"

apply_namespace() {
    result=$(/system/bin/nsenter -t "$1" -m -- "$BB" sh "$MODDIR/mount.sh" "$2" 2>> "$MODDIR/boot.log") || return 1
    case $result in changed) changed=1 ;; ready) ;; *) return 1 ;; esac
    printf 'namespace=%s scope=%s result=%s\n' "$1" "$2" "$result" >> "$MODDIR/boot.log"
}
apply_namespace 1 all || fail 'init mount guard or application failed'
zygotes=$(pidof zygote64 zygote) || fail 'no zygote process found'
for process in $zygotes; do
    apply_namespace "$process" apex || fail "zygote namespace failed: $process"
done
# Existing Bluetooth can have an independent mount namespace.
for process in $(pidof com.android.bluetooth 2>/dev/null || true); do
    if ! apply_namespace "$process" apex; then
        [ ! -d "/proc/$process" ] || fail "Bluetooth namespace failed: $process"
    fi
done

bt_state() {
    /system/bin/dumpsys bluetooth_manager | sed -n '1,12{s/^[[:space:]]*state:[[:space:]]*//p;}'
}
wait_state() {
    attempt=0
    while [ "$attempt" -lt 30 ]; do
        [ "$(bt_state)" = "$1" ] && return 0
        sleep 1
        attempt=$((attempt + 1))
    done
    return 1
}
if [ "$changed" = 1 ]; then
    if [ "$(/system/bin/settings get global bluetooth_on)" = 1 ]; then
        restore_bt=1
        /system/bin/cmd bluetooth_manager disable >> "$MODDIR/boot.log" 2>&1 || fail 'Bluetooth disable failed'
        wait_state OFF || fail 'Bluetooth OFF timeout'
    fi
    if [ "$xml_changed" = 1 ]; then
        /system/bin/setprop ctl.restart vendor.audio-hal || fail 'audio HAL restart failed'
        sleep 2
    fi
    if [ "$restore_bt" = 1 ]; then
        /system/bin/cmd bluetooth_manager enable >> "$MODDIR/boot.log" 2>&1 || fail 'Bluetooth enable failed'
        restore_bt=0
        wait_state ON || fail 'Bluetooth ON timeout'
    fi
fi
status ready
exit 0

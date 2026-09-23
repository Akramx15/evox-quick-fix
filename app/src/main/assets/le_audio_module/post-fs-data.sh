#!/system/bin/sh
MODDIR=${0%/*}
BB=/data/adb/ksu/bin/busybox
boot_id=$(cat /proc/sys/kernel/random/boot_id)
: > "$MODDIR/boot.log"
if result=$(/system/bin/nsenter -t 1 -m -- "$BB" sh "$MODDIR/mount.sh" all 2>> "$MODDIR/boot.log"); then
    printf 'boot=%s\nstage=post-fs-data\nstate=%s\n' "$boot_id" "$result" > "$MODDIR/status.txt"
else
    # APEX activation may not have finished; service.sh retries after boot.
    printf 'boot=%s\nstage=post-fs-data\nstate=deferred\n' "$boot_id" > "$MODDIR/status.txt"
fi
exit 0

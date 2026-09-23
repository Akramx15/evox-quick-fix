#!/system/bin/sh
# Switch the saved policy. The new mode takes effect after reboot.
set -eu
MODDIR=${0%/*}
[ "$(id -u)" = 0 ] || { echo 'Root is required.' >&2; exit 1; }
case ${1:-} in music|duplex) mode=$1 ;; *) echo 'Usage: set-mode.sh music|duplex (then reboot)' >&2; exit 2 ;; esac
source=$MODDIR/profiles/$mode.xml
[ -f "$source" ] && [ -f "$MODDIR/manifest.txt" ] || exit 1
digest=$(sha256sum "$source")
digest=${digest%% *}
case $mode:$digest in
  music:966c0521a05e2a4020b65d25e565dae982d6d2678f0cf03dfdfbef9b8f2c509b|duplex:59615b080e62635347c24511168fad0f6e1f959e520d9cf402e7e6f98be4b914) ;;
  *) echo 'Profile checksum mismatch; nothing changed.' >&2; exit 1 ;;
esac
next=$MODDIR/manifest.next.$$
trap 'rm -f "$next"' EXIT
# Select an immutable profile with one atomic manifest replacement. Never
# overwrite a policy inode that may already be bound into the running system.
awk -F '|' -v OFS='|' -v digest="$digest" -v relative="profiles/$mode.xml" '
  $5 == "/vendor/etc/bluetooth_audio_policy_configuration.xml" {
    $3=digest; $4=relative; matches++
  }
  { print }
  END { if (matches != 1) exit 1 }
' "$MODDIR/manifest.txt" > "$next"
chown 0:0 "$next"
chmod 0644 "$next"
mv "$next" "$MODDIR/manifest.txt"
printf '%s\n' "$mode" > "$MODDIR/mode.txt"
echo "Saved mode: $mode. Reboot to apply."
[ "$mode" != duplex ] || echo "Duplex restores the headset microphone and can reproduce the ROM's LIVE-context audio issue."

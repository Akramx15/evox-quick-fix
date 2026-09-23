#!/system/bin/sh
# Runs inside the destination mount namespace. stdout is changed or ready.
set -u
MODDIR=${0%/*}
case ${1:-} in all|apex) scope=$1 ;; *) echo 'usage: mount.sh all|apex' >&2; exit 2 ;; esac
rows=$(awk -F '|' -v scope="$scope" 'NF && $0 !~ /^#/ && (scope == "all" || $1 == "apex")' "$MODDIR/manifest.txt") || exit 1
[ -n "$rows" ] || { echo 'empty manifest selection' >&2; exit 1; }
new_binds=
rollback() {
    for target in $new_binds; do
        umount "$target" || echo "rollback failed: $target" >&2
    done
}
fail() { echo "$*" >&2; rollback; exit 1; }
trap 'fail "mount interrupted"' HUP INT TERM
file_hash() { result=$(sha256sum "$1") || return 1; printf '%s\n' "${result%% *}"; }

# Validate every selected destination and source before changing anything.
while IFS='|' read -r kind original patched relative target context uid gid mode; do
    [ -n "$mode" ] || fail 'invalid manifest row'
    [ -f "$target" ] || fail "target unavailable: $target"
    current=$(file_hash "$target") || fail "cannot hash: $target"
    case $current in "$original"|"$patched") ;; *) fail "ROM configuration changed: $target" ;; esac
    source_hash=$(file_hash "$MODDIR/$relative") || fail "source unavailable: $relative"
    [ "$source_hash" = "$patched" ] || fail "source checksum mismatch: $relative"
done <<EOF
$rows
EOF

while IFS='|' read -r kind original patched relative target context uid gid mode; do
    current=$(file_hash "$target") || fail "cannot recheck: $target"
    [ "$current" != "$patched" ] || continue
    [ "$current" = "$original" ] || fail "target changed during application: $target"
    source=$MODDIR/$relative
    chown "$uid:$gid" "$source" && chmod "$mode" "$source" && chcon "$context" "$source" || fail "cannot prepare source: $relative"
    mount -o bind "$source" "$target" || fail "bind failed: $target"
    new_binds="$target $new_binds"
    current=$(file_hash "$target") || fail "cannot verify bind: $target"
    [ "$current" = "$patched" ] || fail "bind checksum mismatch: $target"
done <<EOF
$rows
EOF
trap - HUP INT TERM
if [ -n "$new_binds" ]; then printf 'changed\n'; else printf 'ready\n'; fi

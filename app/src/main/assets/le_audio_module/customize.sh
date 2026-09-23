#!/system/bin/sh
set_perm_recursive "$MODPATH" 0 0 0755 0644
for script in mount.sh post-fs-data.sh service.sh customize.sh set-mode.sh; do
    set_perm "$MODPATH/$script" 0 0 0755
done

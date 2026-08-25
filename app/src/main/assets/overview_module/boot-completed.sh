#!/system/bin/sh

TARGET_PACKAGE="com.android.launcher3"
DARK_NAME="CodexTransparentOverviewDark"
LIGHT_NAME="CodexTransparentOverviewLight"
DARK_RESOURCE="com.android.launcher3:color/overview_scrim_dark"
LIGHT_RESOURCE="com.android.launcher3:color/overview_scrim"

apply_overlay() {
    overlay_name="$1"
    target_resource="$2"
    overlay_id="com.android.shell:${overlay_name}"

    /system/bin/cmd overlay fabricate --target "$TARGET_PACKAGE" \
        --name "$overlay_name" "$target_resource" 0x1c 0x00000000 \
        >/dev/null 2>&1 || return 1
    /system/bin/cmd overlay enable --user 0 "$overlay_id" \
        >/dev/null 2>&1 || return 1

    overlay_value="$(/system/bin/cmd overlay lookup --user 0 "$TARGET_PACKAGE" \
        "$target_resource" 2>/dev/null | /system/bin/tr '[:upper:]' '[:lower:]')"
    [ "$overlay_value" = "#0" ] || [ "$overlay_value" = "#00000000" ]
}

disable_both() {
    /system/bin/cmd overlay disable --user 0 \
        "com.android.shell:${DARK_NAME}" >/dev/null 2>&1
    /system/bin/cmd overlay disable --user 0 \
        "com.android.shell:${LIGHT_NAME}" >/dev/null 2>&1
}

attempt=0
while [ "$attempt" -lt 30 ]; do
    if apply_overlay "$DARK_NAME" "$DARK_RESOURCE" \
        && apply_overlay "$LIGHT_NAME" "$LIGHT_RESOURCE"; then
        exit 0
    fi
    attempt=$((attempt + 1))
    sleep 2
done

disable_both
/system/bin/log -t EvoXQuickFix \
    "Overview transparency boot apply failed; both overlays disabled"
exit 1

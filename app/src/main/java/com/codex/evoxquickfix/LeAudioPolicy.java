package com.codex.evoxquickfix;

import java.util.LinkedHashMap;
import java.util.Map;

/** Exact v1.2 duplex payload and complete v1.1 music payload; no per-file version mixing. */
final class LeAudioPolicy {
    static final String ID = "s23_le_audio_fix";
    static final String CURRENT = "/data/adb/modules/" + ID;
    static final String UPDATE = "/data/adb/modules_update/" + ID;
    static final String OWNER = ".evox-quick-fix-owner";
    static final String OWNER_VALUE = "com.codex.evoxquickfix:s23_le_audio_fix:1.2";
    static final String LEGACY_OWNER_VALUE = "com.codex.evoxquickfix:s23_le_audio_fix:1.1";
    // KernelSU's installer removes this hook after executing it. It stays mandatory in the ZIP.
    static final String INSTALLER_REMOVED_FILE = "customize.sh";
    // The bundled set-mode.sh music helper selects the identical immutable music profile.
    static final String HELPER_MUSIC_MANIFEST_SHA256 =
            "4eb57067cca60a036afd2df38776528b599f6929d0bf6ccd4cf0f1beb6920815";
    static final String HELPER_DUPLEX_MANIFEST_SHA256 =
            "62abf976129ffab0545f3757808c6e24e12b840ab8aa7c5f4c95e56c5957e789";
    static final Map<String, String> LEGACY_FILES = legacyFiles();
    static final Map<String, String> FILES = pinnedFiles();
    static final String[][] ROM = {
            {"/vendor/etc/audio/sku_kalama_qssi/audio_policy_configuration.xml",
                    "6cd4b12cbbef0a2c570c520f72f6abcdd82929cefae73aa3a9c73b232af1d09a",
                    "aa40b5c0fc5b87fadf715c20f061474eb8cb74852194dfa3fdb497d76da64a19"},
            {"/vendor/etc/bluetooth_audio_policy_configuration.xml",
                    "877d8745fe542a2e1070d773d64c7c6b3d03168679d3d8355dd8ee283c5ec884",
                    "59615b080e62635347c24511168fad0f6e1f959e520d9cf402e7e6f98be4b914"},
            {"/apex/com.android.bt/etc/bluetooth/le_audio/audio_set_scenarios.json",
                    "116e5e9c08d7bd02ec1e6d43d5d5bf25f80ddff4d6c5e4de158130745af4304b",
                    "766643afc2cd256e5b5bd5976f5c2808ab094fdbda03b62336ed023733e19860"}
    };
    private static final String[] ROM_PAYLOADS = {
            "files/primary_policy.xml", "files/bluetooth_policy.xml", "files/audio_set_scenarios.json"
    };

    private LeAudioPolicy() {}

    static boolean deviceAllowed(DiagnosticReport report) {
        return report.root && report.exactEnvironmentGate && report.kernelSuReady
                && !report.magiskPresent;
    }

    private static Map<String, String> pinnedFiles() {
        Map<String, String> files = new LinkedHashMap<>(LEGACY_FILES);
        files.put("files/audio_set_scenarios.json", "766643afc2cd256e5b5bd5976f5c2808ab094fdbda03b62336ed023733e19860");
        files.put("files/bluetooth_policy.xml", "59615b080e62635347c24511168fad0f6e1f959e520d9cf402e7e6f98be4b914");
        files.put("manifest.txt", "aaf3a8e4bd6caad009582de9e25c7a8e13184596120497ea9e1ad103e9796a6c");
        files.put("mode.txt", "c5c6f8d6bc15500b6cef6581942afa5b033f9519e9f1701b45eb2d862c26945a");
        files.put("module.prop", "b6dc0934c55c761d79a1905068e7e0fd5326a8e6d38c0da9750f48aa5b0961e0");
        files.put("set-mode.sh", "1c581ffad0c4127b692246b2686f3a600ad0a58b5321f37dc87e472e5f9893c8");
        return java.util.Collections.unmodifiableMap(files);
    }

    private static Map<String, String> legacyFiles() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("customize.sh", "2ae11736120e5f973477502bfe368a4967821cd305548c103332406447fb2bbb");
        files.put("files/audio_set_scenarios.json", "a4252be819cc254d2520983dc035886f6c1e40eeadaad0ab786eddecfea1bf1f");
        files.put("files/bluetooth_policy.xml", "966c0521a05e2a4020b65d25e565dae982d6d2678f0cf03dfdfbef9b8f2c509b");
        files.put("files/primary_policy.xml", "aa40b5c0fc5b87fadf715c20f061474eb8cb74852194dfa3fdb497d76da64a19");
        files.put("manifest.txt", "988050e3970431aab44c5e468ec53bb52705ef3dedc054b01bb135aa146aace2");
        files.put("mode.txt", "d543a2f9ecb1161115da947409497a0a31683864c13b34b181e5d786b6724435");
        files.put("module.prop", "11d6c27fe1e710c37db09cc071df6d1f474df60332800ad411d758846deb00ae");
        files.put("mount.sh", "7df2b0cbfe5a166d89ead859d984a942133f8a9bf61148f2d39f669c4b76a60b");
        files.put("originals/audio_set_scenarios.json", "116e5e9c08d7bd02ec1e6d43d5d5bf25f80ddff4d6c5e4de158130745af4304b");
        files.put("originals/bluetooth_policy.xml", "877d8745fe542a2e1070d773d64c7c6b3d03168679d3d8355dd8ee283c5ec884");
        files.put("originals/primary_policy.xml", "6cd4b12cbbef0a2c570c520f72f6abcdd82929cefae73aa3a9c73b232af1d09a");
        files.put("post-fs-data.sh", "70e1fd8b123d5e5add777f255d13c68563937ff80072020da26da84aeb069d73");
        files.put("profiles/duplex.xml", "59615b080e62635347c24511168fad0f6e1f959e520d9cf402e7e6f98be4b914");
        files.put("profiles/music.xml", "966c0521a05e2a4020b65d25e565dae982d6d2678f0cf03dfdfbef9b8f2c509b");
        files.put("service.sh", "d5f704d673d5327215a2c7803836ce876adb03ad7a41fb17c1e2c6daa972787a");
        files.put("set-mode.sh", "864ba85558487df6c0364c332dcce4305525e049ff71dc74823ac84a9416edb8");
        files.put("skip_mount", "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
        return java.util.Collections.unmodifiableMap(files);
    }

    /** Also used in host tests against adversarial temporary module trees. */
    static String verificationFunctions() {
        StringBuilder script = new StringBuilder("""
                hash_is() {
                    [ -f "$1" ] && [ ! -L "$1" ] || return 1
                    digest=$(sha256sum "$1") || return 1
                    [ "${digest%% *}" = "$2" ]
                }
                owner_is() {
                    [ ! -e "$1/.evox-quick-fix-owner" ] ||
                        [ "$(cat "$1/.evox-quick-fix-owner")" = "$2" ]
                }
                safe_tree() {
                    [ -d "$1" ] && [ ! -L "$1" ] || return 1
                    unsafe=$(find "$1" -name '*
                *' -print) || return 1
                    [ -z "$unsafe" ] || return 1
                    entries=$(find "$1" -mindepth 1) || return 1
                    while IFS= read -r entry; do
                        [ -n "$entry" ] || continue
                        [ ! -L "$entry" ] || return 1
                        relative=${entry#"$1"/}
                        if [ -d "$entry" ]; then
                            case "$relative" in files|originals|profiles) ;; *) return 1 ;; esac
                        else
                            [ -f "$entry" ] || return 1
                            case "$relative" in
                """);
        script.append(String.join("|", FILES.keySet()))
                .append("|boot.log|status.txt|disable|remove|update|" + OWNER + ") ;;\n")
                .append("*) return 1 ;;\nesac\nfi\ndone <<EOF\n$entries\nEOF\n}\n");
        appendVerifier(script, "verify_current", FILES, OWNER_VALUE, HELPER_DUPLEX_MANIFEST_SHA256);
        appendVerifier(script, "verify_legacy", LEGACY_FILES, LEGACY_OWNER_VALUE,
                HELPER_MUSIC_MANIFEST_SHA256);
        // KernelSU copies new module.prop into the running directory while staging an update.
        // This exception is reachable only with a fully verified v1.2 update and update marker.
        Map<String, String> pendingLegacy = new LinkedHashMap<>(LEGACY_FILES);
        pendingLegacy.put("module.prop", FILES.get("module.prop"));
        appendVerifier(script, "verify_legacy_pending", pendingLegacy, LEGACY_OWNER_VALUE,
                HELPER_MUSIC_MANIFEST_SHA256);
        script.append("""
                verify_module() { verify_current "$1" || verify_legacy "$1"; }
                module_version() {
                    if verify_current "$1"; then printf current;
                    elif verify_legacy "$1"; then printf legacy;
                    else return 1; fi
                }
                verify_stub() {
                    safe_tree "$1" || return 1
                    [ -f "$1/update" ] || return 1
                    for entry in "$1"/* "$1"/.[!.]* "$1"/..?*; do
                        [ -e "$entry" ] || continue
                        case "${entry##*/}" in module.prop|update|disable|remove|.evox-quick-fix-owner) ;;
                            *) return 1 ;; esac
                    done
                    case "$2" in
                """);
        appendStubVersion(script, "current", FILES.get("module.prop"), OWNER_VALUE);
        appendStubVersion(script, "legacy", LEGACY_FILES.get("module.prop"), LEGACY_OWNER_VALUE);
        script.append("*) return 1 ;;\nesac\n}\n");
        return script.toString();
    }

    private static void appendStubVersion(StringBuilder script, String version, String hash,
                                          String owner) {
        script.append(version).append(") hash_is \"$1/module.prop\" ").append(hash)
                .append(" && owner_is \"$1\" ").append(ShellEscaper.quote(owner)).append(" ;;\n");
    }

    private static void appendVerifier(StringBuilder script, String name, Map<String, String> files,
                                       String owner, String alternateManifest) {
        script.append(name).append("() {\nsafe_tree \"$1\" || return 1\nowner_is \"$1\" ")
                .append(ShellEscaper.quote(owner)).append(" || return 1\n");
        files.forEach((path, hash) -> {
            if (INSTALLER_REMOVED_FILE.equals(path))
                script.append("if [ -e \"$1/").append(path).append("\" ]; then\n");
            script.append("hash_is \"$1/").append(path).append("\" ").append(hash);
            if ("manifest.txt".equals(path))
                script.append(" || hash_is \"$1/manifest.txt\" ").append(alternateManifest);
            script.append(" || return 1\n");
            if (INSTALLER_REMOVED_FILE.equals(path)) script.append("fi\n");
        });
        script.append("}\n");
    }

    static String preflight() {
        return verificationFunctions() + """
                fail() { printf 'LE_AUDIO_BLOCKED:%s\\n' "$1"; exit 41; }
                [ "$(id -u)" = 0 ] || fail root
                [ "$(getprop ro.product.model)" = SM-S918B ] || fail model
                [ "$(getprop ro.build.version.sdk)" = 36 ] || fail sdk
                [ "$(am get-current-user)" = 0 ] || fail user
                users=$(cmd user list) || fail users
                [ "$(printf '%s\\n' "$users" | grep -c 'UserInfo{')" = 1 ] || fail profiles
                printf '%s\\n' "$users" | grep -q 'UserInfo{0:' || fail user
                ksud -V >/dev/null 2>&1 && ksud module list >/dev/null 2>&1 || fail kernelsu
                [ -x /system/bin/nsenter ] && [ -x /data/adb/ksu/bin/busybox ] || fail namespace
                for parent in /data /data/adb /data/adb/modules /data/adb/modules_update /data/adb/evox-quick-fix; do
                    [ ! -L "$parent" ] || fail parent_symlink
                    [ ! -e "$parent" ] || [ -d "$parent" ] || fail parent_type
                done
                """ + "current=" + ShellEscaper.quote(CURRENT) + "\nupdate="
                + ShellEscaper.quote(UPDATE) + "\n" + moduleState();
    }

    static String moduleState() {
        return """
                present=0
                staged=0
                stub=0
                disabled=0
                removed=0
                current_version=none
                update_version=none
                for directory in "$current" "$update"; do
                    [ ! -L "$directory" ] || fail module_symlink
                    [ ! -e "$directory" ] || [ -d "$directory" ] || fail module_type
                done
                if [ -d "$update" ]; then
                    update_version=$(module_version "$update") || fail unknown_update
                    staged=1
                    present=1
                fi
                if [ -d "$current" ]; then
                    if current_version=$(module_version "$current"); then :;
                    elif [ "$update_version" = current ] && [ -f "$current/update" ] && verify_legacy_pending "$current"; then
                        current_version=legacy
                    elif [ "$staged" = 1 ] && verify_stub "$current" "$update_version"; then
                        current_version=$update_version
                        stub=1
                    else
                        fail unknown_module
                    fi
                    present=1
                fi
                for directory in "$current" "$update"; do
                    # KernelSU preserves either disable marker when promoting a staged update.
                    [ ! -f "$directory/disable" ] || disabled=1
                    [ ! -f "$directory/remove" ] || removed=1
                done
                """;
    }

    static String romChecks() {
        StringBuilder script = new StringBuilder("rom_ok=1\npatched=0\n");
        for (int index = 0; index < ROM.length; index++) {
            String[] row = ROM[index];
            String legacyHash = LEGACY_FILES.get(ROM_PAYLOADS[index]);
            script.append("digest=$(/system/bin/nsenter -t 1 -m -- /data/adb/ksu/bin/busybox sha256sum ")
                    .append(ShellEscaper.quote(row[0])).append(" 2>/dev/null) || digest=unavailable\n")
                    .append("case ${digest%% *} in\n").append(row[1]).append(") ;;\n");
            if (legacyHash.equals(row[2])) {
                script.append(row[2]).append(") patched=$((patched + 1)); ")
                        .append("[ \"$stub\" = 0 ] && [ \"$current_version\" != none ] || rom_ok=0 ;;\n");
            } else {
                script.append(row[2]).append(") patched=$((patched + 1)); ")
                        .append("[ \"$stub\" = 0 ] && [ \"$current_version\" = current ] || rom_ok=0 ;;\n")
                        .append(legacyHash).append(") patched=$((patched + 1)); ")
                        .append("[ \"$stub\" = 0 ] && [ \"$current_version\" = legacy ] || rom_ok=0 ;;\n");
            }
            script.append("*) rom_ok=0 ;;\nesac\n");
        }
        return script.toString();
    }

    static String inspectScript() {
        return preflight() + romChecks() + """
                ready=0
                boot_failed=0
                if [ -f "$current/status.txt" ]; then
                    boot=$(cat /proc/sys/kernel/random/boot_id) || exit 41
                    if grep -qx "boot=$boot" "$current/status.txt"; then
                        if grep -qx 'stage=service' "$current/status.txt" && grep -qx 'state=ready' "$current/status.txt"; then ready=1; fi
                        if grep -q '^state=failed' "$current/status.txt"; then boot_failed=1; fi
                    fi
                fi
                printf 'present=%s\\nstaged=%s\\ndisabled=%s\\nremoved=%s\\nrom_ok=%s\\npatched=%s\\nready=%s\\nboot_failed=%s\\ncurrent_version=%s\\nupdate_version=%s\\n' "$present" "$staged" "$disabled" "$removed" "$rom_ok" "$patched" "$ready" "$boot_failed" "$current_version" "$update_version"
                """;
    }
}

package com.codex.evoxquickfix;

import java.util.HashMap;
import java.util.Map;

record LeAudioStatus(State state, boolean canApply, boolean canDisable) {
    enum State {
        UNAVAILABLE(R.string.le_audio_unavailable),
        UNKNOWN(R.string.le_audio_unknown),
        ABSENT(R.string.le_audio_absent),
        ACTIVE(R.string.le_audio_active),
        PENDING(R.string.le_audio_pending),
        DISABLED(R.string.le_audio_disabled),
        DISABLE_PENDING(R.string.le_audio_disable_pending),
        REMOVING(R.string.le_audio_removing),
        ROM_CHANGED(R.string.le_audio_rom_changed),
        BOOT_FAILED(R.string.le_audio_boot_failed);

        final int label;
        State(int label) { this.label = label; }
    }

    static LeAudioStatus unavailable() {
        return new LeAudioStatus(State.UNAVAILABLE, false, false);
    }

    static LeAudioStatus parse(CommandResult result) {
        if (!result.ok()) {
            return new LeAudioStatus(State.UNKNOWN, false, false);
        }
        Map<String, String> values = new HashMap<>();
        for (String line : result.output.split("\n")) {
            String[] parts = line.split("=", 2);
            if (parts.length == 2) values.put(parts[0], parts[1]);
        }
        for (String key : new String[]{"present", "staged", "disabled", "removed",
                "rom_ok", "patched", "ready", "boot_failed"}) {
            String value = values.get(key);
            if (value == null || !(key.equals("patched") ? value.matches("[0-3]")
                    : value.matches("[01]"))) {
                return new LeAudioStatus(State.UNKNOWN, false, false);
            }
        }
        boolean present = "1".equals(values.get("present"));
        boolean disabled = "1".equals(values.get("disabled"));
        boolean compatible = "1".equals(values.get("rom_ok"));
        boolean canDisable = present && !disabled;
        if ("1".equals(values.get("removed"))) {
            return new LeAudioStatus(State.REMOVING, false, canDisable);
        }
        if (present && disabled) {
            return new LeAudioStatus("0".equals(values.get("patched"))
                    ? State.DISABLED : State.DISABLE_PENDING, compatible, false);
        }
        if (!compatible) {
            return new LeAudioStatus(State.ROM_CHANGED, false, canDisable);
        }
        if (!present) return new LeAudioStatus(State.ABSENT, true, false);
        if ("1".equals(values.get("boot_failed"))) {
            return new LeAudioStatus(State.BOOT_FAILED, true, true);
        }
        if ("0".equals(values.get("staged")) && "3".equals(values.get("patched"))
                && "1".equals(values.get("ready"))) {
            return new LeAudioStatus(State.ACTIVE, false, true);
        }
        return new LeAudioStatus(State.PENDING, true, true);
    }
}

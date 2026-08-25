package com.codex.evoxquickfix;

final class DebloatProfileEntry {
    final String packageName;
    final DebloatLocalizedText name;
    final DebloatLocalizedText function;
    final DebloatLocalizedText impact;
    final DebloatRisk risk;
    final boolean dangerous;

    DebloatProfileEntry(String packageName, DebloatLocalizedText name,
                        DebloatLocalizedText function, DebloatLocalizedText impact,
                        DebloatRisk risk, boolean dangerous) {
        this.packageName = packageName;
        this.name = name;
        this.function = function;
        this.impact = impact;
        this.risk = risk;
        this.dangerous = dangerous;
    }
}

package com.codex.evoxquickfix;

enum DebloatRisk {
    LOW(false),
    MEDIUM(false),
    HIGH(true),
    CRITICAL(true);

    final boolean dangerousByDefinition;

    DebloatRisk(boolean dangerousByDefinition) {
        this.dangerousByDefinition = dangerousByDefinition;
    }

    static DebloatRisk parse(String value) {
        try {
            return DebloatRisk.valueOf(value);
        } catch (RuntimeException failure) {
            throw new IllegalArgumentException("Unknown debloat risk: " + value, failure);
        }
    }
}

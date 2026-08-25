package com.codex.evoxquickfix.debloat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class DebloatResult {
    public final List<String> changed;
    public final List<String> absent;
    public final List<String> external;
    public final List<String> alreadyManaged;

    DebloatResult(List<String> changed, List<String> absent, List<String> external,
                   List<String> alreadyManaged) {
        this.changed = immutable(changed);
        this.absent = immutable(absent);
        this.external = immutable(external);
        this.alreadyManaged = immutable(alreadyManaged);
    }

    private static List<String> immutable(List<String> values) {
        return Collections.unmodifiableList(new ArrayList<>(values));
    }
}

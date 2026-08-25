package com.codex.evoxquickfix.debloat;

/** One requested package. Dangerous targets require a one-item request and a typed package id. */
public final class DebloatTarget {
    public final String packageName;
    public final boolean dangerous;

    public DebloatTarget(String packageName, boolean dangerous) {
        this.packageName = PackageId.requireValid(packageName);
        this.dangerous = dangerous;
    }
}

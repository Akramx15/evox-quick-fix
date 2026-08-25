package com.codex.evoxquickfix.debloat;

interface PackageStateSource {
    /** Returns null when the package is not installed for user 0. */
    PackageEnabledState stateForUserZero(String packageName);
}

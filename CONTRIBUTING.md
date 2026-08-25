# Contributing

This project changes rooted Android systems, so small and auditable pull requests are preferred.

1. Do not add INTERNET, analytics, a WebView, a terminal, automatic link opening, uninstall, clear-data, or a Disable All action.
2. Do not weaken model, Android version, user, KernelSU, Quick Search hash/signature, role-holder, contextual-provider, or protected-package gates.
3. Package profile changes must preserve deterministic canonicalization, include independent Arabic and English descriptions, update tests, and receive explicit review.
4. Ledger changes need tests for partial failure, reverse rollback, process death, replica conflict, symlinks, external changes and exact states 0..4.
5. Run:

   ~~~sh
   ./gradlew testDebugUnitTest lintDebug assembleDebug
   ~~~

6. Never submit signing files, passwords, local.properties, device state, or generated root ledgers.

The public CI build is unsigned and must not be presented as an official release.

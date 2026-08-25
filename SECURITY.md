# Security policy

## Supported versions

Security fixes are applied to the newest prerelease while the project is in beta. The verified v1.0.1 artifact remains available for rollback but is not a general-purpose debloat tool.

## Reporting

Do not post device identifiers, logs containing account data, signing material, or private recovery files in a public issue. Use GitHub's private vulnerability report form:

https://github.com/Akramx15/evox-quick-fix/security/advisories/new

## Trust model

- The APK intentionally requires root for mutations.
- It has no INTERNET permission and performs no analytics.
- Release APKs are signed locally. GitHub Actions produces unsigned CI artifacts only.
- Debloat uses only pm disable-user --user 0 and never uninstalls an APK or clears app data.
- A local/root ownership ledger and emergency script restore only packages owned by the app.
- The signing key, passwords, local.properties, device snapshots and root ledgers must never be committed.

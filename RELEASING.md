# Releasing

## Release Checklist

1. Pull latest `main`.
2. Update `CHANGELOG.md` with release notes.
3. Bump `versionCode` and `versionName` in `app/build.gradle.kts`.
4. Run checks:

```bash
./gradlew test
```

5. Build release artifacts:

```bash
./gradlew :app:assembleRelease
```

6. Tag the release (for example `v1.0.0`) and push tags.
7. Publish GitHub release notes from `CHANGELOG.md`.

## Hotfixes

- Keep hotfix pull requests minimal.
- Update `CHANGELOG.md` under the next patch version.

# Publishing (maintainers)

One-time setup (done for `com.vocabloot`):

1. **Namespace.** `com.vocabloot` is verified on the Central Portal (shared with BackupKit and WidgetBridge).
2. **GitHub secrets** (repo → Settings → Secrets and variables → Actions): `MAVEN_CENTRAL_USERNAME`, `MAVEN_CENTRAL_PASSWORD`, `SIGNING_KEY_ID`, `SIGNING_PASSWORD`, `GPG_KEY_CONTENTS`, the same values as BackupKit.
3. **Pages.** Settings → Pages → Source: GitHub Actions.

Each release:

1. Bump `version` in `assetdropkit/build.gradle.kts`, `assetdropkit-test/build.gradle.kts` and `AssetDropKit.version` in `Sources/AssetDropKit/AssetDropKit.swift`; move the CHANGELOG "Unreleased" block under the version; commit.
2. `git tag v0.1.1 && git push --tags`, then GitHub → Releases → Draft from the tag → Publish. Swift Package Manager resolves the same tag.
3. The Publish workflow uploads both artifacts to the Central Portal. Deployments → wait for validation → **Publish**. Public in about 30 minutes; searchable in a few hours.
4. klibs.io lists it automatically within a month; to skip the wait, open an indexing request at https://github.com/JetBrains/klibs-io/issues/new/choose.

# Releases

Releases are built and published by GitHub Actions (`.github/workflows/android.yml`).

## Every push and pull request

The workflow runs `testDebugUnitTest` and `assembleRelease`, and uploads the APK as a workflow artifact.

## Publishing a release

1. Bump `versionCode` and `versionName` in `app/build.gradle`.
2. Add release notes at `.github/release-notes/v<version>.md`.
3. Merge to `main`.
4. Publish:
   - From a machine that can push tags: `git tag v<version> && git push origin v<version>`.
   - From a cloud agent session (tag pushes are refused): run the **Android** workflow manually on `main` with the `tag` input set to `v<version>`. The workflow creates the tag and the release from that commit.

The release gets `SMSCostTracker-v<version>-arm64-v8a.apk`.

## Signing

The APK is signed with the release key from repository secrets `RELEASE_KEYSTORE_BASE64`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS` and `RELEASE_KEY_PASSWORD`. If they are missing, the workflow falls back to the debug key and the file name ends in `-debug-signed`. Such an APK cannot update an installed release build, so do not publish it as a release.

Local release builds read the same values from `local.properties` (not committed).

# Releases

Releases are built and published by GitHub Actions (`.github/workflows/android.yml`).

## Every push and pull request

The `build` job (required on `main`) runs, in order:

1. `testDebugUnitTest`: JVM unit tests.
2. `lintRelease`: Android lint. Any lint error fails the job and prints the report; warnings are allowed. The text and HTML reports are uploaded as the `lint-report` artifact.
3. `assembleRelease`: the APK, uploaded as a workflow artifact.

The `ui-tests` job runs the Compose instrumentation tests in `app/src/androidTest` on an API 30 emulator (`connectedDebugAndroidTest`). It runs for pull requests, pushes to `main` and manual runs, not for other branch pushes. Reports are uploaded as `ui-test-reports`.

Run the same checks locally with `./gradlew testDebugUnitTest lintRelease connectedDebugAndroidTest` (the last needs a device or emulator).

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

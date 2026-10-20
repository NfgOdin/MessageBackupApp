# Automatic Release Versioning Rule

Whenever the user explicitly instructs to create or prepare a release (e.g., *"make release 1.0.1"*, *"prepare release 1.1.0"*, *"create release 2.0.0"*), you MUST automatically execute the following standard versioning protocol across the project:

## 1. Version Code Calculation Formula
Parse the semantic version `MAJOR.MINOR.PATCH` and calculate `versionCode` using the multiplier formula:
$$\text{versionCode} = (\text{MAJOR} \times 10000) + (\text{MINOR} \times 100) + \text{PATCH}$$

Examples:
- `1.0.0` -> `versionCode = 10000`, `versionName = "1.0.0"`
- `1.0.1` -> `versionCode = 10001`, `versionName = "1.0.1"`
- `1.1.0` -> `versionCode = 10100`, `versionName = "1.1.0"`
- `2.0.0` -> `versionCode = 20000`, `versionName = "2.0.0"`

## 2. Mandatory Files to Update
When a release command is triggered, immediately update:
1. `app/build.gradle.kts`:
   - `versionCode = <calculated_code>`
   - `versionName = "<MAJOR.MINOR.PATCH>"`
2. `app/src/main/java/com/messagebackup/app/ui/settings/SettingsScreen.kt`:
   - Update the About section text: `Muninn v<MAJOR.MINOR.PATCH>`

## 3. Verification & Verification Output
1. Run `.\gradlew.bat testDebugUnitTest` to verify no regressions.
2. Confirm the updated `versionCode` and `versionName` to the user and report whether the release bundle (`.\gradlew.bat bundleRelease`) is ready to be built.

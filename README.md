# ChatAssistantMobile

Android Jetpack Compose app scaffold for Google login + backend token exchange + chat analysis.

## Implemented capabilities
- Google sign in with Credential Manager.
- Backend auth exchange (`/api/v1/auth/google/login`).
- Encrypted token storage.
- Auto token refresh (`/api/v1/auth/refresh`) with one retry on `401`.
- Manual chat paste flow for MVP.
- Analyze retry with backoff for transient failures (`429`, `502`, `504`, network IO).
- Analyze API call (`/api/v1/analyze-chat`) and result rendering.
- Encrypted local history of recent analyses (view + clear in Settings).
- Logout flow (`/api/v1/auth/logout`) with local session cleanup.
- Notification listener ingestion pipeline to capture chat notification lines and import to conversation input.
- Home screen widget (Glance) showing latest summary and quick reply.
- Privacy consent gate and per-feature capture controls in-app.
- Floating bubble overlay service with modes: set rule, analyze current screen, and quick options.
- Bubble analyze now shows an on-screen Compose result dialog (summary + suggestions) immediately after analysis and is easy to customize.
- Accessibility service scaffolding for future ingestion flow.
- Settings now includes accessibility capture preview (raw vs filtered) to tune input filtering before analysis.
- Accessibility capture auto-tags speaker by bubble position: left = `other`, right = `me`.

## Configure
Add these values in `gradle.properties` or user-level Gradle properties:

```properties
BACKEND_BASE_URL=https://api.jlpt.codes/
GOOGLE_WEB_CLIENT_ID=your_web_client_id_from_google_cloud
```

Create `local.properties` from `local.properties.example` and set `sdk.dir`.

Optional release signing properties (for `assembleRelease`):

```properties
RELEASE_STORE_FILE=/absolute/path/to/release-keystore.jks
RELEASE_STORE_PASSWORD=...
RELEASE_KEY_ALIAS=...
RELEASE_KEY_PASSWORD=...
```

## Build

```bash
./gradlew :app:assembleDebug
```

## Tests

```bash
./gradlew :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin
```

## CI/QA
- GitHub Actions workflow: `.github/workflows/android-ci.yml`
- Device checklist: `QA_ANDROID_10_14.md`

## Project structure
- `app/src/main/java/com/chatassistantmobile/data`: API, auth, repository, models.
- `app/src/main/java/com/chatassistantmobile/domain`: parsed output and use cases.
- `app/src/main/java/com/chatassistantmobile/ui`: Compose UI and app state.
- `TASK_BREAKDOWN.md`: phased implementation plan and checklist.

## Notes
- Gradle wrapper is included (`gradlew`, `gradlew.bat`, `gradle/wrapper/*`).
- Build requires Android SDK installed and `sdk.dir` configured in `local.properties`.

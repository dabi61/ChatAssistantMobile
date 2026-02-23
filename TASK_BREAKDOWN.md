# Jetpack Compose Mobile Task Breakdown

## Phase 0 - Environment
- [x] Create Android project skeleton with Compose + Kotlin DSL.
- [x] Add required dependencies: Credential Manager, Google ID token, Retrofit, OkHttp, Serialization, Security Crypto.
- [x] Add configurable `BACKEND_BASE_URL` and `GOOGLE_WEB_CLIENT_ID`.

## Phase 1 - MVP (Implemented)
- [x] Google sign-in trigger via Credential Manager.
- [x] Exchange Google `id_token` with backend `/api/v1/auth/google/login`.
- [x] Store `access_token`, `refresh_token`, and access token expiry in encrypted local storage.
- [x] Manual conversation paste screen.
- [x] Parse input text into `ChatMessage` list (`me`/`other`).
- [x] Analyze call to `/api/v1/analyze-chat`.
- [x] Result screen with suggestion cards and copy action.
- [x] Settings screen with logout and quick links to notification/accessibility settings.

## Phase 2 - Auth Hardening (Implemented)
- [x] Add `AuthInterceptor` for bearer token injection.
- [x] Add `TokenAuthenticator` to refresh tokens on `401`.
- [x] Retry original request once after successful refresh.
- [x] Clear local session if refresh fails.
- [x] Prevent concurrent refresh race using mutex (single-flight style).

## Phase 3 - Error Mapping (Implemented)
- [x] Map key backend errors in UI (`401`, `429`, `500`, `502`, `504`).
- [x] Show user-facing errors for sign-in/analyze failures.

## Phase 4 - Hardening (Implemented)
- [x] Persist recent analysis history (encrypted local store, max 20).
- [x] Add retry with backoff for transient server errors.
- [x] Add service scaffolding + manifest wiring for NotificationListener/Accessibility.

## Phase 5 - Next Work (Planned)
- [x] Implement NotificationListenerService ingestion pipeline.
- [x] Add App Widget (Glance) for latest quick replies.
- [x] Add explicit consent screens and privacy controls.
- [x] Add floating bubble overlay mode (set rule, analyze current screen, quick options).
- [x] Add instrumentation and unit tests for auth refresh flow.
- [x] Add release signing pipeline scaffolding (Gradle props + CI workflow).
- [ ] Execute QA on Android 10-14 devices/emulators.

## Validation Checklist
- [ ] Set valid `GOOGLE_WEB_CLIENT_ID` from Firebase/Google Cloud.
- [ ] Ensure backend `.env` has matching `GOOGLE_CLIENT_IDS`.
- [ ] Confirm emulator can hit backend (`10.0.2.2`).
- [ ] Verify login -> analyze -> refresh -> logout end-to-end.

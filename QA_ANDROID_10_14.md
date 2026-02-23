# Android 10-14 QA Checklist

## Scope
- Android API 29 (Android 10)
- Android API 30/31 (Android 11/12)
- Android API 33 (Android 13)
- Android API 34 (Android 14)

## Preconditions
- Backend running and reachable from test device/emulator.
- Valid `GOOGLE_WEB_CLIENT_ID` configured.
- Google OAuth SHA fingerprints registered.

## Test Cases
1. Login with Google
- Expected: app receives backend access/refresh tokens and navigates to consent or conversation input.

2. Privacy Consent Gate
- Expected: authenticated user cannot access conversation flow before accepting consent.
- Toggle notification capture and accessibility capture, then verify state persists after app restart.

3. Notification Ingestion
- Enable notification listener in system settings.
- Send test chat notification from another app.
- Import notifications from Conversation screen.
- Expected: imported lines appear with `other:` prefix.

4. Analyze Chat
- Paste/compose conversation and call Analyze.
- Expected: success response renders summary + suggestion cards.

5. Token Refresh
- Force access token expiry and call Analyze.
- Expected: refresh flow executes automatically and request retries once.

6. Logout
- Logout from Settings.
- Expected: session cleared locally and app returns to Login.

7. Widget Update
- Add home screen widget.
- Run Analyze once.
- Expected: widget shows latest summary/reply and opens app when tapped.

8. Data Deletion Controls
- Clear analysis history and notification drafts.
- Revoke privacy consent.
- Expected: local sensitive data removed and capture disabled.

## CI Hooks
- `./gradlew :app:testDebugUnitTest`
- `./gradlew :app:compileDebugAndroidTestKotlin`
- `./gradlew :app:connectedDebugAndroidTest` on emulator matrix (API 29/31/34)

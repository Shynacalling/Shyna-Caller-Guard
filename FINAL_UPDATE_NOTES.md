# Shyna Caller Guard 4.14.7-FINAL

This is the cleaned Shyna Caller Guard source project. The separate Shyna AI Android Integration Kit is intentionally NOT merged into this build.

## Final fixes included

- Manual email/password Login now authenticates directly with Firebase Auth instead of trying to read protected Firestore user profiles before authentication.
- Manual Signup profile/username reservation rules updated for the existing create-auth-first flow.
- Google Sign-In remains a separate action; duplicate Gmail/Google-style auth actions were removed.
- Password reset sends the real Firebase reset link and does not automatically launch Gmail.
- Shyna one-to-one voice/video call buttons are connected to real call signaling with visible failures instead of silent no-op callbacks.
- Firestore signaling fallback is available when the HTTP call-create/lifecycle API is temporarily unavailable.
- Incoming call listener ordering fixed so a new incoming Shyna call is not incorrectly treated as already handled.
- FCM event routing fixed so CALL_ACCEPTED/CALL_ENDED/etc. are not reinterpreted as new incoming calls.
- Voice-to-video upgrade enables camera and updates call type.
- Connected state, timer, LiveKit media setup, reconnect UI, call cleanup, call history and chat-call entries improved.
- New Call single-contact mode is separated from group-call selection so voice/video buttons do not become placeholder group actions.
- Group recipients keep their own accept/leave decision when another group participant accepts.
- Calls history listeners are realtime and listener lifecycle cleanup is included.
- Chat Room / Meetings now has working New Meeting, Join, Schedule and Share Screen entry flows.
- Secure random Meeting ID and passcode generation plus New ID/New Password actions.
- Meeting ID uniqueness validation before create/schedule.
- Host Start and participant Join are separate flows.
- Meeting passcode/status validation, participant registration, LiveKit room join, mic/video entry settings, leave/end and reconnect cleanup are wired.
- Meeting document creation uses an atomic Firestore transaction for generated-ID uniqueness, and duplicate local room-start taps are guarded in-process.
- Dead visible meeting Recording tab and fake meeting controls were removed/hidden rather than left as non-working UI.
- App version bumped to 4.14.7-FINAL (versionCode 80).

## Protected behavior

`InCallServiceImpl.kt`, containing the existing Telecom/SIM first-ring protection path, is byte-for-byte unchanged from the previous clean project.

## Backend / Firebase deployment required for production cross-device behavior

The updated source contains `shyna-link-token-server/server.js` and `firestore.rules`, but packaging a ZIP does not deploy cloud resources.

For full production cross-device Shyna calls/meetings, the currently deployed token/signaling service must use the included updated `server.js` and have these environment variables configured:

- `FIREBASE_SERVICE_ACCOUNT`
- `LIVEKIT_API_KEY`
- `LIVEKIT_API_SECRET`

The included `firestore.rules` must also be published to the same Firebase project used by `app/google-services.json`.

The Android client defaults to the existing configured token server and LiveKit project in `LiveKitConfig.kt`.

## Verification performed in this environment

- All 83 Kotlin source files passed Kotlin PSI syntax parsing.
- `shyna-link-token-server/server.js` passed `node --check`.
- Android Manifest/resource XML files parsed successfully.
- JSON config files parsed successfully.
- FIRST RING protected file hash matched the prior clean project.

A full Android Gradle build and live two-device LiveKit/FCM test could not be executed in this environment because the uploaded project does not include the Gradle wrapper executable/JAR and no system Gradle installation is available. Cloud deployment credentials are also not available here. Do not interpret static validation as a live production test.

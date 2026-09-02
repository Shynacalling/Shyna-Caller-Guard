# Shyna Caller Guard - Feature Completion and Bug Fixes

This plan outlines the fixes and completions for Shyna Caller Guard, focusing on authentication, calling, chat room meetings, and location accuracy.

## User Review Required

> [!IMPORTANT]
> The "Gmail-opening bug" was investigated. While no explicit code to open Gmail was found in the authentication flow, I will ensure that all buttons are correctly scoped and that no generic intents are fired during Signup/Login.

> [!NOTE]
> Shyna AI integration is explicitly excluded from this version. Call screening features will remain as they are or be hidden if they contain non-functional AI placeholders.

## Proposed Changes

### [Component] Chat Room / Meetings
- **Z-Order Fix**: Move `NewMeetingScreen`, `JoinMeetingScreen`, and `ScheduleMeetingScreen` to the end of `MeetingsHubContent` to ensure they appear as overlays over the main content.
- **Meeting ID Generation**: Update `generateMeetingId` and `pmi` logic to produce 10-digit IDs (e.g., `845 729 6314`) to match user requirements.
- **Random Password**: Ensure password generation produces secure 6-digit passcodes.

#### [MODIFY] [SmartCommunicationScreen.kt](file:///D:/Shyna_Caller_Guard_Final_Updated/Shyna_Caller_Guard_Final_Updated/app/src/main/java/com/example/callruleblocker/ui/SmartCommunicationScreen.kt)
- Wrap `MeetingsHubContent` in a `Box`.
- Move overlay screen emission to the end of the `Box`.
- Update `generateMeetingId` to 10 digits.
- Update `pmi` derivation to 10 digits.

### [Component] Authentication
- **Gmail Bug Prevention**: Audit and ensure no `startActivity` calls with generic `ACTION_VIEW` or `ACTION_SEND` exist in `LoginScreen` and `SignUpScreen`.
- **Manual Login/Signup**: Ensure Firebase Auth flow is direct and profile creation/lookup happens post-authentication.
- **Google Sign-In**: Verify separation from manual flow and ensure it opens the account chooser.

#### [MODIFY] [SmartCommunicationScreen.kt](file:///D:/Shyna_Caller_Guard_Final_Updated/Shyna_Caller_Guard_Final_Updated/app/src/main/java/com/example/callruleblocker/ui/SmartCommunicationScreen.kt)
- Refine `LoginScreen` and `SignUpScreen` button click listeners.

### [Component] Shyna Voice & Video Calling
- **Signaling Completion**: Ensure all call entry points (Chat, Contact, History) are wired to `CallSignalingManager.startCall`.
- **Failure Handling**: Add visible error messages (Toasts) for call signaling failures instead of silent no-ops.

#### [MODIFY] [SmartCommunicationScreen.kt](file:///D:/Shyna_Caller_Guard_Final_Updated/Shyna_Caller_Guard_Final_Updated/app/src/main/java/com/example/callruleblocker/ui/SmartCommunicationScreen.kt)
- Update call buttons in `CallsListContent`, `PeerDetailScreen`, and other areas.

### [Component] Location
- **Accuracy Improvement**: Ensure "Send current location" always requests a fresh fix via `getCurrentLocation` if the existing one is stale or inaccurate.
- **Map Link**: Verify `geo:` URI format for exact pin accuracy.

#### [MODIFY] [SendLocationScreen.kt](file:///D:/Shyna_Caller_Guard_Final_Updated/Shyna_Caller_Guard_Final_Updated/app/src/main/java/com/example/callruleblocker/ui/SendLocationScreen.kt)
- Refine `LocationActionItem` for "Send your current location".

## Verification Plan

### Automated Tests
- Run `assembleDebug` to ensure compilation.

### Manual Verification
- **Auth**: Test Manual Signup and Login. Verify Gmail does not open.
- **Meetings**: Click "New Meeting", "Join", "Schedule". Verify overlays appear and IDs are 10 digits.
- **Calls**: Test Voice and Video calls between two devices (simulated or real).
- **Location**: Verify location accuracy and Map pin opening.

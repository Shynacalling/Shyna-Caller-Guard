# Shyna Caller Guard

Merged Android source project.

## Preserved from 1.zip
- Rule database and rule model
- Rule priority and matching behavior
- SIM-slot resolution
- Incoming-call allow/block decision flow and one-second fail-open timeout

## Improved UI and settings
- Shyna Caller Guard branding across the launcher and app UI
- Phone-style bottom navigation: Keypad, Recents, Contacts
- Editable dialer field with Android phone keyboard
- Working add-to-contacts action with entered number
- Search field for recent calls and contacts
- Missed-call filter
- Contact call, message and info actions
- Separate Block & SIM Rules screen
- Separate Call Recording settings: Off, Manual, Automatic and excluded numbers
- Permission-safe empty/error states

## Important recording limitation
Android and phone manufacturers may prevent third-party apps from capturing two-way call audio. The settings are persisted safely; actual recording must use a device-supported recording API or OEM dialer integration.

Open this folder in Android Studio, let Gradle sync, then build/install on an Android 8+ test phone. Set Shyna Caller Guard as the default Phone app and grant requested permissions.

## Advanced in-call UI upgrade
- Full incoming, outgoing and active-call screen.
- Animated, haptic Answer/Decline/End buttons.
- Speaker, mute, Bluetooth audio route, DTMF keypad, hold/resume.
- Add-call flow and merge when Telecom exposes a conferenceable call.
- Video-call upgrade request when the carrier/provider exposes VideoCall.
- Manual/automatic best-effort recording with excluded numbers and safe failure.
- Add current caller to Contacts.

Platform note: video upgrade, Bluetooth routing, conference merge and remote-party recording are controlled by Android, the carrier and the phone manufacturer. Unsupported capabilities remain disabled or fail safely rather than crashing the app.


## Smart dialer upgrade
- Real-feel animated keypad with press scaling, depth and haptic feedback.
- Live contact-name lookup while entering a phone number.
- T9 name matching: keypad digits can match letters in saved contact names.
- Tap a suggestion to complete the number or call it directly.
- New-number fallback to Add Contact.

## v1.2 dual-SIM and full call settings upgrade
- Dialer now shows **Call from: Ask every time / SIM 1 / SIM 2**.
- The selection is remembered permanently until changed.
- In Ask mode, the app shows a SIM picker before placing each call.
- Calls are placed through Android Telecom with the selected PhoneAccountHandle.
- Expanded Samsung-style Call settings UI with caller information, spam protection,
  captions, ringtone, answering/ending preferences, quick-decline message, in-app
  display mode, Wi-Fi calling, voicemail, supplementary services, permissions and About.
- OS/carrier-controlled features open the matching Android call settings instead of
  pretending to control unsupported hardware/carrier capabilities.
- Existing 1.zip call rules and SIM-rule repository remain unchanged.


## Version 4.14.6 Premium Upgrade
- **Universal Brand Themes**: 20+ dedicated themes for Apple, Google, Samsung, OnePlus, Xiaomi, Nothing, etc., for both dialer and call screen.
- **Premium MicRecorder**: Upgraded call recording with 128kbps AAC quality and intelligent audio source fallbacks (Mic, Voice Communication, Camcorder).
- **Intelligent Font Scaling**: Fixed 12/16/3 dialer rule with automatic multi-line wrapping and scrolling.
- **Integrated SIM Badge**: Modern solid SIM icons with integrated number display across the entire app.
- **Improved Performance**: Background contact suggestions and memoized keypad interactions for zero-lag typing.

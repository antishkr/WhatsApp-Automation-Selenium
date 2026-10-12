# Paytm Deep Inspector 0.2 — read-only Android diagnostics

This build uses supported Android APIs only. It **cannot** access `/data/user/0/net.one97.paytm/databases/passbook.db`, bypass app sandboxing, root the phone, disable secure-window flags, or obtain undisclosed Paytm server data.

## Consent and features
- Only the **user's own receiving phone**. Explicit consent and 180-second arm for Paytm screen inspection.
- Detect whether the Accessibility Service is actually enabled and connected. Record Paytm event counts, window counts, inspected node counts, and errors.
- Scan active window + other interactive Paytm windows with `FLAG_RETRIEVE_INTERACTIVE_WINDOWS`.
- Collect Android-exposed event text and visible UI nodes, with password fields redacted.
- When permitted, attempt up to three Android `AccessibilityService.takeScreenshot` captures during the armed session; run **bundled on-device ML Kit Latin OCR**. No image is saved. A secure-window error is reported, not bypassed.
- Alternative: manually select your own ordinary screenshot/image using Android's system file picker for on-device OCR.
- Log a few most recent Paytm notification updates and text-bearing extras when Notification Access is enabled.
- No Internet permission; data stays in app-private storage, auto-cleared after 24 hours or manually deleted.
- No automation of Paytm UI or financial actions. No premium activation.

## Use
1. Install APK artifact on Android (different application ID from v0.1, so can coexist).
2. Enable Accessibility Service, optionally Notification Access (Android restricted settings may apply).
3. Check consent, press Arm 180s, manually open a RECEIVED Paytm Passbook transaction where DS code is visible.
4. Return and Refresh. Look at **Diagnostics**, **Accessible UI**, and **OCR** separately.
5. If secure-screen capture is denied and accessible UI lacks note, capture cannot recover the code. This is not proof that Paytm lacks a private cache.
6. Do not share raw unredacted screen output with others.

Built with Gradle/Android API 35 and Google ML Kit text-recognition:16.0.1.

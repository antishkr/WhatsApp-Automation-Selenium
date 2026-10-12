# Paytm Note Inspector (read-only Android diagnostic)

This is a privacy-focused proof-of-concept for inspecting the receiver-side Paytm app UI and Paytm notification extras with explicit user permission.

## Capabilities
- User-armed 90-second Android AccessibilityService session reading only `net.one97.paytm` UI text.
- Locally inspect Paytm NotificationListenerService extras and actions.
- Extract DS-XXXXXX code candidates, amounts and labelled UPI references.
- No INTERNET permission, no private Paytm database access, no payments, no automatic screen actions, no root.
- All information stays on the phone; user can delete captured records.

## Install
Download `app-debug.apk` from the GitHub Actions artifact `Paytm-Inspector-APK`, install on your Android, open the app, and enable its accessibility service and (optional) notification access.
Read the in-app consent and privacy description. Arm capture, open a **received** transaction in Paytm, and return to inspect the observed fields.

This does not authenticate bank settlements or guarantee Paytm exposes a QR-prefilled note to accessibility. Production unattended scraping would require checking Paytm's terms and permission.

## Build
Github Actions workflow runs Gradle 8.9 and Android API 35, produces a debug-signed installable APK. Don't share sensitive raw diagnostics.

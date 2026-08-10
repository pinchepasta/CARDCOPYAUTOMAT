# CardCopyAutomat

Android Studio (Kotlin) project, `minSdk 28` (Android 9+), `compileSdk/targetSdk 34`.

## What it does

1. Watches for a USB card reader being attached (`UsbAttachReceiver` +
   `USB_DEVICE_ATTACHED`).
2. Scans the reader's volume for images and RAW files (including Canon `.CR2`, `.CR3`,
   `.CRW`, and other common formats like `.DNG`, `.JPG`, `.PNG`, etc.) in any subfolder
   (`RawFileScanner`).
3. Copies them into the app's internal storage folder
   (`Android/data/com.cardcopyautomat.app/files/CanonRawCopies`).
4. Shows preview thumbnails of the copied images on the main screen.
5. If Google Drive is selected in Settings, uploads each copy to a
   `CardCopyAutomat` folder in the signed-in user's Drive
   (`GoogleDriveUploader`, Drive v3 REST API, `drive.file` scope — the app
   only ever sees files it created itself, not the rest of the user's Drive).
5. Deletes the originals from the card once copy (and upload, if enabled)
   succeeded.
6. Releases the app's SAF permission to the card volume and plays a beep +
   two short vibration bursts as the "safe to remove the card" signal
   (`FeedbackHelper`).

## How to open it

Open the project root in Android Studio (Iguana or newer). Let it sync —
Android Studio will regenerate the Gradle wrapper jar automatically on
first sync even though it isn't checked into this bundle.

## Required one-time setup before it can build & run Google Sign-In

Google Sign-In / Drive upload needs an OAuth client registered to *your*
app's package name + signing certificate — this can't be hardcoded
generically in source you're handed:

1. In Google Cloud Console, create (or reuse) a project, enable the
   **Google Drive API**.
2. Under **APIs & Services → Credentials**, create an **OAuth 2.0 Client ID**
   of type **Android**, using package name `com.cardcopyautomat.app` and the
   SHA-1 fingerprint of the signing key you'll build with (debug keystore
   fingerprint for testing: `keytool -list -v -keystore ~/.android/debug.keystore`,
   password `android`).
3. Configure the OAuth consent screen (add your own Google account as a
   test user while the app is unverified).

No other code changes are needed — `play-services-auth` reads the matching
client automatically from your app's signature + package name at runtime.

## Important limitations (read before relying on this for real shoots)

- **Android apps can't force-eject a USB mass-storage device** without
  root — there's no public API for it. What the app does instead once it's
  finished: release its own access permission to the volume, then give the
  beep/vibrate signal that it's safe for *you* to physically remove the
  card. That's the same trust model iOS/Android's own "Files" apps use.
- **USB card swaps without a physical unplug/replug of the reader may not
  re-trigger `USB_DEVICE_ATTACHED`.** Whether swapping a card in an
  already-connected multi-slot reader re-enumerates the USB device (and
  therefore re-fires the attach broadcast) depends on the reader hardware
  and the phone's USB host controller — some do, some don't. As a reliable
  fallback for that case, the main screen has a **"Copy now"** button that
  runs the exact same job on demand.
- The very first time you use a given reader, you'll need to open the app
  and grant it access via the Storage Access Framework picker in Settings
  (`Select card reader volume`) — Android doesn't allow apps to grant
  themselves storage access silently, for good reason.

## Project layout

```
app/src/main/kotlin/com/cardcopyautomat/app/
  MainActivity.kt        entry screen, "Copy now" manual trigger
  SettingsActivity.kt     SAF picker, upload target, Google sign-in
  UsbAttachReceiver.kt     catches USB attach, starts the service
  CardCopyService.kt      the actual scan → copy → upload → delete → signal job
  RawFileScanner.kt       recursive .CR2/.CR3/.CRW finder over a SAF tree
  GoogleDriveUploader.kt  Drive v3 REST multipart upload
  FeedbackHelper.kt       beep + double short vibration
  Prefs.kt                typed SharedPreferences wrapper
```

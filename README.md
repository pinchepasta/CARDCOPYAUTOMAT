# CardCopyAutomat

<img src="https://github.com/pinchepasta/CARDCOPYAUTOMAT/blob/main/app/src/main/res/drawable/automat.jpg" alt="LOGO" width="50%" height="50%">

 An awesome Android tool for filmmakers!

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

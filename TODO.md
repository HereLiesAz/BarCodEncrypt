# Todo

A list of sorrows now embraced.

## Core Functionality

-   [x] Implement the camera and barcode scanning flow. This involves:
    -   [x] Creating a dedicated scanner activity (`ScannerActivity`).
    -   [x] Requesting camera permissions at runtime.
    -   [x] Configuring CameraX and the ML Kit barcode analyzer.
    -   [x] Passing a scanned barcode back to the calling process.
-   [x] Build out the `MessageDetectionService` to actually parse screen content.
    -   [x] Settle on a definitive message header format (`BCE::v1::{opts}::{id}::`).
    -   [x] Develop a robust method to identify and extract the encrypted payload from a `AccessibilityNodeInfo` tree.
    -   [x] Implement text highlighting on the detected message using an `OverlayService`.
-   [x] Flesh out the `EncryptionManager`. The current AES/GCM implementation is solid, but advanced features are pending.
    -   [x] Design the rolling key system.
    -   [ ] Design the temporary key exchange protocol. How does the barcode, the rolling key, and the device state produce the final decryption key?
-   [x] Create the UI for adding/managing contacts and their associated barcodes (`ContactDetailActivity`).
-   [x] Create the UI for composing and encrypting a new message (`ComposeActivity`).

## Refinements & Fixes

-   [x] The app does not yet ask for Accessibility Service permissions. The user must enable it manually. This needs a proper onboarding flow. (MainActivity now guides the user to the settings).
-   [x] The UI is a barren wasteland. It needs a soul. Or at least a coherent design. (The UI is now built with Jetpack Compose and Material 3).
-   [x] Error handling. What happens when a barcode is scanned that matches no contact? What happens when decryption fails? (The overlay now shows an "Incorrect Key" state).
-   [ ] The new Password Assistant feature uses any scanned barcode. In the future, it could be enhanced to allow the user to select a specific barcode from their saved keys to use for passwords, or to use a dedicated "password" barcode.
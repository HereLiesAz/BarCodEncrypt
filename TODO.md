# Todo

A list of sorrows yet to be embraced.

## Core Functionality

-   [ ] Implement the camera and barcode scanning flow. This involves:
    -   [ ] Creating a dedicated scanner activity/fragment.
    -   [ ] Requesting camera permissions at runtime.
    -   [ ] Configuring CameraX and the ML Kit barcode analyzer.
    -   [ ] Passing a scanned barcode back to the calling process.
-   [ ] Build out the `MessageDetectionService` to actually parse screen content.
    -   [ ] Settle on a definitive message header format (e.g., `BCE::{version}::{opts}::`).
    -   [ ] Develop a robust method to identify and extract the encrypted payload from a `AccessibilityNodeInfo` tree.
    -   [ ] Implement text highlighting on the detected message. This is non-trivial and may require drawing over other apps.
-   [ ] Flesh out the `EncryptionManager`. The current implementation is a joke.
    -   [ ] Design the rolling key system.
    -   [ ] Design the temporary key exchange protocol. How does the barcode, the rolling key, and the device state produce the final decryption key?
-   [ ] Create the UI for adding/managing contacts and their associated barcodes.
-   [ ] Create the UI for composing and encrypting a new message.

## Refinements & Fixes

-   [ ] The app does not yet ask for Accessibility Service permissions. The user must enable it manually. This needs a proper onboarding flow.
-   [ ] The UI is a barren wasteland. It needs a soul. Or at least a coherent design.
-   [ ] Error handling. What happens when a barcode is scanned that matches no contact? What happens when decryption fails? Right now, the app just stares back into the void.
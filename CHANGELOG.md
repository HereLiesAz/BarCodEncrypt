# Changelog

All notable changes to this project will be documented in this file. The format is based on, but does not strictly adhere to, [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

## [Unreleased]

### 0.1.0 - The Ghost in the Machine - 2025-09-06

#### Added
-   **Core Functionality:** Implemented the end-to-end message encryption and decryption flow.
-   **`ComposeActivity`:** A dedicated screen for composing messages, selecting recipients and their keys, and setting message options (`single-use`, `ttl`).
-   **`ContactDetailActivity`:** A screen for managing a contact's associated barcodes (keys). Allows adding new barcodes via the scanner and deleting existing ones.
-   **`ScannerActivity`:** A fully functional barcode scanning screen using CameraX and ML Kit.
-   **`MessageDetectionService`:** The service now actively detects messages on screen, checks for revoked single-use messages, and launches the `OverlayService`.
-   **`OverlayService`:** Displays a tappable overlay on detected messages, handles the decryption flow, and enforces `single-use` and `ttl` options.
-   **`ScannerManager`:** A new singleton to decouple scan requests from the UI, allowing services to request scans from the main activity.
-   **Permissions Flow:** The `MainActivity` now checks for and guides the user to enable all necessary permissions (Accessibility, Overlay, Contacts).
-   **Jetpack Compose UI:** All screens are now built with Jetpack Compose and Material 3.

#### Fixed
-   Corrected several bugs related to inconsistent `Intent` extra keys, ensuring reliable data passing between activities.

### 0.0.2 - The Naming of Parts - 2025-09-06

#### Added

-   KDoc documentation across all Kotlin source files, because even ghosts need labels.
-   `README.md`: A public declaration of intent.
-   `AGENTS.md`: A dramatis personae for the code.
-   `CHANGELOG.md`: This very file. A memory of memories.
-   `TODO.md`: A map of the labyrinth yet to be explored.

### 0.0.1 - The Skeleton - 2025-09-06

#### Added

-   Initial project structure for the Barcodencrypt Android app.
-   Gradle dependencies for CameraX, ML Kit Barcode Scanning, and Room Persistence.
-   `AndroidManifest.xml` with permissions for Camera and a declaration for the future `MessageDetectionService`.
-   Room database entities (`Contact`, `Barcode`), DAO (`ContactDao`), and `AppDatabase` singleton.
-   Placeholder `EncryptionManager` for future cryptographic logic.
-   `MessageDetectionService` class with `accessibility_service_config.xml` to define its observational capabilities.
-   Basic UI layouts for the main activity (`activity_main.xml`) and a contact list item (`list_item_contact.xml`).
-   `MainActivity.kt` as the entry point, currently a hollow shell.
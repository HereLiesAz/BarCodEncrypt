# Changelog

All notable changes to this project will be documented in this file. The format is based on, but does not strictly adhere to, [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

## [Unreleased]

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
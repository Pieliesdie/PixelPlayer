# Agent Instructions for PixelPlayer

## Project Structure & Architecture
- **Modules**: This is a multi-module Android application comprising `:app` (main phone app), `:wear` (WearOS app), `:shared` (DTOs and shared logic), and `:baselineprofile` (Macrobenchmark/startup profiles).
- **Tech Stack**: 100% Kotlin targeting JVM 21, Jetpack Compose (Material Design 3), Media3 ExoPlayer, and Room DB.
- **Architecture**: MVVM with StateFlow/SharedFlow, Dependency Injection via Hilt.

## Build & Testing Commands
- **Build Debug APKs**: `gradlew :app:assembleDebug -Ppixelplay.enableAbiSplits=true` (Creates split APKs for different architectures).
- **Run Unit Tests**: `gradlew test` (Configured for JUnit 5 via `useJUnitPlatform()`, but supports legacy JUnit 4 tests via vintage engine).

## Quirks & Configurations
- **Local Properties**: API keys for external services should be placed in `local.properties` (e.g., `TELEGRAM_API_ID` and `TELEGRAM_API_HASH`). Build defaults to fallback keys if missing.
- **Room Schemas**: Room database schema exports are located in `app/schemas` (`room.schemaLocation`).
- **Compose Compiler Reports**: To debug Compose stability and metrics, pass the gradle property: `gradlew assembleDebug -Ppixelplay.enableComposeCompilerReports=true`. Reports will output to the build directory.
- **KSP**: The project utilizes KSP2 (enabled via `ksp.useKSP2=true` in `gradle.properties`).
- **Shared Module Constraint**: The `:shared` module contains pure DTOs and serialization; do not add platform-specific (Android) APIs to it.

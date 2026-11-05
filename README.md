# MS Editor Media Hub — Native Android app

## Build configuration
- Android application ID: `com.mseditor.mediahub`
- Android Gradle Plugin: 8.6.1
- Kotlin: 2.0.20
- JDK: 17
- Compile SDK: 35; minimum SDK: 24
- GitHub Actions builds the debug APK using Gradle 8.9.

## Existing backend
- Uses the existing Supabase project URL and publishable client key.
- Reads existing `media_items`, `media_likes`, and `media_comments` data.
- Uses the existing public `media` storage bucket.
- Do not add a service-role/secret key to this Android client.
- Do not recreate, reset, or overwrite existing Supabase tables or stored media.

## App features
- Native Kotlin Android UI (not a WebView-only app).
- Home feed and category pages for Video Songs, Video Tutorial, Video Prompt, Image Prompt, and Presets & Assets.
- Media playback, prompt viewing/copying, likes, comments, sharing, and download controls.
- Admin login and admin-only upload/delete actions.

## Branding
- The approved square MS Editor Hub logo is used for launcher icon resources.
- The same logo is displayed on the black splash screen before Home.
- Splash screen includes the existing radar scanning animation.

## Build
Use the repository's GitHub Actions workflow **Android APK Build** to build a debug APK. The repository does not include the Gradle wrapper JAR, so use the workflow's installed Gradle 8.9 rather than `./gradlew`.

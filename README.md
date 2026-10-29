# MS Editor Media Hub — Native Android starter

This is a native Kotlin Android app (not a WebView wrapper) using the existing Supabase project configuration found in the website source.

## Verified existing categories
- Home (all media)
- Video Songs (`song`)
- Video Tutorial (`tutorial`)
- Video Prompt (`video_prompt`)
- Image Prompt (`image_prompt`)
- Presets & Assets (`assets`)

`Tutorials` is an existing shortcut to the `tutorial` category, not a separate database category.

## Safety and current scope
- Uses the existing Supabase URL, publishable key, `media` bucket, and `media_items` table.
- Never add a Supabase service-role/secret key to this app or GitHub.
- The app reads existing media, opens playable media links, supports download requests, and includes email/password sign-up and sign-in screens.
- Admin and appearance screens are intentionally cautious placeholders. Do not enable uploads/deletes or overwrite shared appearance settings until the live RLS/storage policies and the original logo/cover asset have been verified. No database tables are created or altered by this project.
- Shared likes/comments/views and write actions are not yet implemented in this initial package.

## Build
Open the repository in Android Studio, or use the included GitHub Actions workflow to build a debug APK. The project uses Android Gradle Plugin 8.6.1, Kotlin 2.0.20, JDK 17, compile SDK 35.


## Branding assets
- `1000000280.jpg` user-provided logo is included as Android launcher icon resources.
- `1000000281.jpg` user-provided wide banner is included on the native home screen.

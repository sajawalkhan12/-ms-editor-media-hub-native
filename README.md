# MS Editor — Native Android starter

This is a native Kotlin Android app (not a WebView wrapper) using the existing Supabase project configuration found in the website source.

## Verified existing categories
- Home (all media)
- Video Songs (`song`)
- Video Tutorial (`tutorial`)
- Video Prompt (`video_prompt`)
- Image Prompt (`image_prompt`)
- Presets & Assets (`assets`)

`Tutorials` is an existing shortcut to the `tutorial` category, not a separate database category.

## Features and safety
- Uses the existing Supabase URL, publishable key, `media` bucket, and `media_items` table.
- Never add a Supabase service-role/secret key to this app or GitHub.
- The app reads existing media, opens playable media links, supports download requests, and includes email/password sign-up and sign-in screens.
- Admin uploads and post deletion use the signed-in admin access token; server-side RLS policies remain the authorization boundary.
- Admins can delete comments using the existing verified-admin policy.
- Upload form categories match the website: Video Songs, Video Tutorial, Video Prompt, Image Prompt, Presets & Assets. Prompt-only posts save to the existing `prompt_text` column.
- Media deletes remove the database record and attempt to remove its stored media/poster files. No tables or schema are created or altered by this app update.
- Existing likes/comments and download/share controls are preserved.

## Build
Open the repository in Android Studio, or use the included GitHub Actions workflow to build a debug APK. The project uses Android Gradle Plugin 8.6.1, Kotlin 2.0.20, JDK 17, compile SDK 35.


## Branding assets
- `1000000280.jpg` user-provided logo is included as Android launcher icon resources.
- `1000000281.jpg` user-provided wide banner is included on the native home screen.

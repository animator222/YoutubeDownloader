# YT Downloader (Android)

A fast YouTube downloader app for Android. Paste or share a link to a **video, Short or live stream**,
pick a quality, and the download keeps running in the background. Finished files are published to
the shared **Movies/YT Downloader** folder (audio to **Music/YT Downloader**), so they appear in your
Gallery / Photos app.

## Features

- **Videos, Shorts and live streams** – anything yt-dlp understands from YouTube
  (`youtube.com/watch`, `youtu.be`, `/shorts/`, `/live/`).
- **Quality picker with audio info** – every resolution / frame rate the video offers, with codec and
  approximate size, plus a badge that tells you whether that stream:
  - 🔊 **Audio** – already contains sound,
  - 🔇 **No audio · added** – is video-only; the best audio track is downloaded too and merged in
    automatically (YouTube serves 720p+ this way),
  - 🎵 **Audio only** – sound track without video (M4A / Opus).
- **Maximum speed ("Turbo")** – downloads go through **aria2c with 16 parallel connections**;
  HLS/DASH fragments are fetched 8 at a time. If YouTube rejects aria2c the app retries automatically
  with yt-dlp's chunked downloader (10 MB ranges, which avoids per-connection throttling).
  Turbo can be switched off on the main screen.
- **Background downloads** – each download is a WorkManager job running as a foreground service with
  a progress notification (percent, speed, ETA) and a Cancel button. Several downloads can run at once.
- **Live recording** – records from the moment you start until the stream ends or you tap
  **Stop & save** (in the app or the notification). The recording is remuxed to MP4 and saved.
- **Saved to the gallery** – via MediaStore on Android 10+, or the public Movies folder + media scan
  on Android 8–9. A "Saved to gallery" notification opens the file.
- **Share target** – in the YouTube app tap *Share → YT Downloader*.
- **Self-updating engine** – yt-dlp updates itself at most once a day (YouTube changes often);
  you can also force it from the ⋮ menu.

## Getting the APK

Download the latest APK from the repository's **Releases** page. Every push also builds the app on
GitHub Actions (**Actions → Build APK → Artifacts**). To publish a new release, push a tag such as
`v1.0.1`, or run **Actions → Build APK → Run workflow** and enter a release tag.

Pick the APK that matches your phone:

| File | Phones |
| --- | --- |
| `YTDownloader-arm64-v8a-release.apk` | Almost every phone from the last ~7 years |
| `YTDownloader-armeabi-v7a-release.apk` | Older 32-bit phones |
| `YTDownloader-universal-release.apk` | Works everywhere (largest) |

Allow "Install unknown apps" for your browser / file manager when Android asks.

> Without a signing key configured, CI signs the APK with a throwaway debug key, so a newer build may
> refuse to install over an older one. Uninstall first, or add these repository secrets to get a
> stable signature: `RELEASE_KEYSTORE_BASE64` (base64 of a `.jks`), `RELEASE_KEYSTORE_PASSWORD`,
> `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`.

## Building locally

Requirements: Android Studio (Ladybug or newer) or JDK 17 + Android SDK 35.

```bash
./gradlew assembleDebug      # app/build/outputs/apk/debug/
./gradlew assembleRelease    # app/build/outputs/apk/release/
```

## How it works

| Part | File |
| --- | --- |
| Runtime setup + daily yt-dlp update | `engine/Engine.kt` |
| Reading qualities (yt-dlp `--dump-single-json`) | `engine/YtInfo.kt` |
| Background download, retries, live stop | `download/DownloadWorker.kt`, `download/DownloadControl.kt` |
| Saving into the gallery | `download/MediaSaver.kt` |
| UI (Jetpack Compose) | `ui/MainScreen.kt`, `ui/MainViewModel.kt` |

The heavy lifting is done by [yt-dlp](https://github.com/yt-dlp/yt-dlp) bundled through
[youtubedl-android](https://github.com/JunkFood02/youtubedl-android) together with Python, FFmpeg
(merging / remuxing) and aria2c (multi-connection downloads).

Minimum Android version: 8.0 (API 26).

## Disclaimer

Only download content you own or have permission to download. Respect YouTube's Terms of Service and
copyright law.

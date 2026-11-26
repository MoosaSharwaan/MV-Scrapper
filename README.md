# MV Downloader

A local video and audio downloader for Android, built on [yt-dlp](https://github.com/yt-dlp/yt-dlp).
No account, no ads, no server. Downloads open in whatever player you already use.

> Status: v1 in progress. The code is written but hasn't been run on a phone yet.
> Planning, mockups and rules live in the blueprint artifact.

## What v1 does

- Paste a link, or share one into the app from your browser.
- Fetch the title, length and available qualities.
- Pick a preset (MP4 up to 1080p, MKV best, MP3, M4A) or a specific resolution, up to 8K where the site offers it.
- Download queue with progress, cancel and retry. Partial downloads resume on retry.
- You set how many downloads run at once (1 to 5, default 2). The rest wait.
- Wi-Fi only (on by default).
- Keeps downloading in the background, with a progress notification.
- Saves video to `Movies/MV Downloader` and audio to `Music/MV Downloader`.
- Library list with search and a Video / Audio filter. Tap to open in your default player.
- Long-press to select several items and delete them together, from the list only or with their files.
  Nothing is cleared automatically.
- Updates yt-dlp on launch over Wi-Fi, or from Settings.

## Limits

- Works on the sites yt-dlp supports (1,800+), plus many pages that embed a plain video file or stream.
  If desktop yt-dlp can't download a link, this app can't either.
- No DRM content (Netflix, Disney+, Prime Video and similar).
- Videos that need a sign-in need cookie import, which comes in a later version.
- 4K and 8K files are large, merging them is slow on a phone, and not every phone plays them smoothly.
- Not on Google Play: its policy rejects YouTube downloaders. Install the APK directly.

Download only what you have the right to save.

## Build

GitHub Actions builds the APKs on every push (`.github/workflows/build.yml`).
Download them from the run's **apks** artifact. Pick `app-arm64-v8a-debug.apk` for most phones,
or `app-universal-debug.apk` if unsure.

Locally, with Android Studio or the Android SDK installed:

```bash
./gradlew assembleDebug
```

Requirements: JDK 17, Android SDK 35. Minimum Android version: 8.0 (API 26).

## Code map

| File | What it does |
| --- | --- |
| `Engine.kt` | Wraps youtubedl-android: fetch info, download, cancel, update yt-dlp |
| `Downloads.kt` | The queue: parallel limit, Wi-Fi rule, retry, delete, persistence |
| `DownloadService.kt` | Foreground service and progress notification |
| `Storage.kt` | Saves finished files to Movies / Music through MediaStore |
| `Store.kt` | Saves the queue and history as JSON |
| `Prefs.kt` | Settings |
| `ui/` | Compose screens: Notice, Home (with quality picker), Queue, Library, Settings |

## License

GPL-3.0, because the bundled [youtubedl-android](https://github.com/JunkFood02/youtubedl-android)
library is GPL-3.0. See `LICENSE`.

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
- Not on Google Play: its policy rejects YouTube downloaders. Install from GitHub Releases, ideally through Obtainium.

Download only what you have the right to save.

## Build

GitHub Actions builds debug APKs on every push (`.github/workflows/build.yml`). Signed releases come from tags (see below).
Download them from the run's **apks** artifact. Pick `app-arm64-v8a-debug.apk` for most phones,
or `app-universal-debug.apk` if unsure.

Locally, with Android Studio or the Android SDK installed:

```bash
./gradlew assembleDebug
```

Requirements: JDK 17, Android SDK 35. Minimum Android version: 8.0 (API 26).

## Releases and Obtainium

Releases are published on GitHub, so [Obtainium](https://github.com/ImranR98/Obtainium) can install the app
and keep it updated.

### Add the app in Obtainium

1. Tap **Add app** and paste `https://github.com/MoosaSharwaan/MV-Scrapper`.
2. Under **Filter APKs by regular expression**, enter `arm64-v8a` (most phones).
   Use `universal` if you're unsure what your phone needs.
3. If the repository is private, add a GitHub personal access token in Obtainium's GitHub settings.

Obtainium then checks for new releases and offers the update.

### Signing

Every build is signed with `app/signing.keystore`, which is committed to the repository (password `android`),
the same approach as Exo Player. No secrets to set up, and every release installs over the last one.
Because the key is public, anyone could sign a look-alike APK: only install builds from this repository's releases.

### Publish a release

In GitHub, open **Actions → Release → Run workflow**, enter a version such as `0.1.0`, and run it.
The workflow creates the tag `v0.1.0` itself. Pushing a `v*` tag from your computer works too.

It builds signed APKs (`mv-downloader-v0.1.0-arm64-v8a.apk`, `-armeabi-v7a`, `-x86_64`, `-universal`)
and attaches them to a GitHub release. Each release's version code is the workflow run number,
so it always goes up.

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

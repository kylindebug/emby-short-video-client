# Emby Short Video Client

[中文](README.md)

A simple Android client for browsing Emby videos with a short-video-style vertical feed.

An immersive Android player for NAS-hosted Emby libraries (Android 8.0+, arm64-v8a).

## Features

- Media3/MediaCodec hardware decoding by default, with automatic LibVLC software-decoding fallback. Software decoding can also be selected in Settings.
- A consistent modern dark interface with rounded cards, sans-serif typography, player-style icons, and ripple feedback.
- Automatic orientation changes only after the device remains within ±18° of the target orientation for 800 ms, plus manual orientation and orientation-lock controls.
- Double-tap to play or pause. Drag vertically to bring the previous or next video into view together with the current video, then release to switch or spring back smoothly.
- Exponential horizontal seeking for both fine and fast adjustments.
- Downward swipes beginning in the top 72 dp / 8% safe area do not switch videos, preventing conflicts with Android's notification shade. Seek feedback is always clamped to the video's valid duration.
- When paused, the player shows elapsed time, duration, and a draggable seek bar. During playback, a single tap shows the seek bar for three seconds with a fade-out; tapping again hides it immediately.
- Dragging the seek bar or swiping horizontally shows a thumbnail for the target playback position.
- Long-press the upper or lower half of the screen for separate temporary playback speeds from 1/8× to 8×. Releasing restores the previous speed.
- Long-press speed changes activate in about 140 ms with light haptic feedback. A low-opacity speed badge appears at the top so it does not cover the video, and beginning a swipe cancels the pending long press.
- Distraction-free video while playing; title, Settings, orientation, and lock controls appear while paused.
- The selected library or folder is shuffled on entry. The next two queue items are fully preloaded in the background, with a 1 GB disk-cache limit.
- Optional autoplay and configurable end behavior: next video, pause, or loop the current video.

## Build

```powershell
./gradlew.bat testDebugUnitTest assembleDebug
```

On first launch, open Settings and enter the Emby server address, port, and account. The password is hidden by default and can be revealed temporarily with the eye button. Select **Connect to server and choose...**, browse from a media library into the desired folder, select **Use current folder**, and save.

See `REQUIREMENTS.md` for the complete product and acceptance requirements.

Plain HTTP access is enabled for home-NAS use. Use HTTPS for internet-facing servers. Account credentials are stored only in the Android app's private `SharedPreferences`. Emby streaming URLs contain an access token, so do not share complete URLs or player logs containing them.

The client treats the NAS media library as strictly read-only. It never creates `.nfo` files, artwork, subtitles, or temporary files beside your media. Preloaded data is written only to the Android app's private `cacheDir`.

## Playback Controls

- **Single-tap anywhere on the video:** while playing, show the progress bar for three seconds; tap again while it is visible to hide it immediately.
- **Double-tap anywhere on the video:** play or pause.
- **Swipe up or down:** the next or previous video follows the finger into view, attached to the current video. Release after the threshold to switch, or release earlier to spring back. Downward swipes from the top safe area are ignored to avoid conflicts with Android system gestures.
- **Swipe left or right:** seek with an exponential curve. Short movements provide second-level precision, while longer movements seek faster. The target is always clamped between the start and end of the video, and a thumbnail previews the target position.
- **Long-press the upper half:** temporarily play at the configured upper-half speed. A translucent speed badge appears at the top; release to restore the previous speed.
- **Long-press the lower half:** temporarily play at the configured lower-half speed. A translucent speed badge appears at the top; release to restore the previous speed.
- **Drag the paused seek bar:** preview the target position with a thumbnail and seek there on release, while showing elapsed and total time.
- **Rotate the device:** switch orientation after the stability threshold is met. While paused, use the on-screen orientation and lock icons for manual control.
- **Paused view:** shows the filename, progress, elapsed and total time, Settings, orientation toggle, and orientation lock. All controls hide automatically during playback.

LibVLC includes native libraries, so the Debug APK is relatively large. The project packages only `arm64-v8a`, targeting Snapdragon 8 Gen 3 and newer devices.

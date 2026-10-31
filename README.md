# File Explorer CL

CL stands for Control: File Explorer CL is File Explorer Control. The app was renamed so the name fits on the home screen; the package name and update signing key are unchanged, so it installs over earlier versions.

An offline Android file manager with a compact yellow-and-white dashboard. It reads shared-storage folders directly, including documents in Download, subject to Android's access restrictions. All-files access does not unlock another app's private data or bypass protected Android/data and Android/obb locations.

## Features

- Compact storage dashboard with a per-group size summary (Images, Videos, Audio, Documents, Apps and an etc. row covering all remaining used space, including Android/data, Android/obb and app-private data that cannot be itemized) and category shortcuts; decimal storage units based on the user-data partition.
- Dashboard, library categories and recent files share one in-memory scan snapshot, so opening a category does not rescan all of storage each time. App-made changes (rename, delete, new folder, archive extraction) refresh the snapshot.
- Every file row has an overflow action menu (open, rename, share, delete); long-press still starts multi-select in the folder browser. Image files show thumbnails in the folder browser and in the Images library category.
- Folder browser: list/grid, breadcrumbs, sorting, hidden-files toggle, thumbnails and multi-select.
- Full-filename rename, including an editable extension. Unsafe paths and existing destination files are rejected. Rename does not alter source bytes.
- ZIP and RAR4/RAR5 contents and extraction into a new folder named after the archive: "Extract here" (beside the archive) or "Choose folder". Existing files are never replaced. No password-protected or split archives in this build. Maximum 10,000 entries, 10 GB extracted data and 64 MiB RAR dictionary.
- Internal picture viewer: pinch zoom and display-only rotation. Android-decodable still images; animated GIF/WebP show a still frame. Decoded image size is bounded to 2048 pixels on the longest edge.
- Internal PDF reader: page navigation and pinch zoom. No editing, text search or password-protected PDF support.
- Internal video player: always-visible play/pause and seek controls. MP4/H.264/AAC is tested; other containers and codecs depend on the device. Playback pauses when leaving the foreground.
- Library categories and the 200 most recently modified shared-storage files.
- English and German; white/yellow appearance in both system light and dark mode.
- No ads, tracking, network permission or cloud conversion. Network tab remains a placeholder.
- Plain-text viewer (txt, log, md, json, xml, csv, ini, conf, py, js, yml and similar; first 2 MB).
- Open with from other apps: pictures, videos, PDF, ZIP/RAR and text files opened from another app (for example a chat) can be shown here. The file is copied into the app cache for viewing; archives are copied into Download so extraction stays there.
- The chosen sort order in the folder browser and in category lists is remembered.
- Other file types, such as Word documents, still use an installed external app when opened.

## Install

Download the APK from the repository's Releases page, allow installation from your browser when Android asks, then grant All files access when the app opens.

Earlier 0.1.0 and 0.1.1 preview APKs were signed with different temporary debug keys. Switching from either to 0.2.0 requires one reinstall of the app (then named Stock File Explorer). App-specific data/settings and access grants reset; shared Downloads, documents and photos outside app-specific storage remain. From 0.2.0 the private repository Actions secrets restore the same persistent key for each build. The APK still has a debug build configuration.

## Build and validation

Requires JDK 17 and Android SDK platform 35.

```
./gradlew assembleDebug testDebugUnitTest
```

Local builds without repository signing secrets use a local debug key. Do not expect them to upgrade a published build. Never commit a private key or password. CI signs through private repository Actions secrets and removes its temporary runner copy afterward.

CI runs unit tests and Android 15 emulator tests in system light and night modes. Tests use real generated JPEG/PNG, a two-page PDF, H.264/AAC video, ZIP, stored RAR4/RAR5 and compressed solid RAR4/RAR5 fixtures. They exercise folder/Library/Recent opening, rendering, zoom, video controls, extraction output bytes, unsafe-path/corruption/duplicate rejection, cancellation cleanup, repeat extraction without overwrite and full-name/extension rename. Emulator validation does not establish compatibility with every device, codec or archive.

See THIRD_PARTY_NOTICES.md for licenses, signing fingerprint and limits.

# Vulpine for Android

The official Android app for [Vulpine](https://vulpine.pw), an end-to-end encrypted messenger.

This is a fully native app written in Java with plain Android views. It talks to the same Vulpine servers as the web app, so chats, groups, calls, stories and secret chats work across both.

## Features

- End-to-end encrypted direct messages and groups
- Secret chats protected by a shared password, stored only on your device
- Voice and video calls, group call rooms, screen sharing
- Stories, notes, polls, reactions, voice messages, albums and carousels
- PIN lock, Pro features, boosts, decorations and the shop
- Automatic updates

## Building

You need:

- JDK 17 or newer
- Android SDK with platform `android-36` and build-tools 34 or newer
- Python 3

```
export ANDROID_HOME=/path/to/android-sdk
python3 build.py
```

The APK is written to `build/vulpine.apk`.

Without a keystore the build is signed with a local debug key created on first run. To sign with your own key:

```
export VULPINE_KEYSTORE=/path/to/release.keystore
export VULPINE_KEY_ALIAS=yourkey
export VULPINE_KEY_PASS=yourpassword
python3 build.py
```

Optional variables: `BUILD` (output folder), `ABIS` (default `arm64-v8a,armeabi-v7a`), `VERSION_CODE`, `VERSION_NAME`, `BUILD_TOOLS`, `ANDROID_PLATFORM`.

Builds signed with a key other than the official one install as a separate signature and cannot update or be updated by the official app from vulpine.pw.

## Project layout

- `core/` networking, WebSocket session, encryption, local storage. Plain Java, no Android dependencies.
- `app/src/` the Android app: screens, chats, calls, stories, settings and services.
- `app/res/`, `app/assets/` resources, fonts, icons and decorations.
- `libs/` bundled third-party libraries (see `THIRD_PARTY_NOTICES.md`).

## Security

Messages are encrypted on the device before they are sent. The server never sees message contents or your keys.

If you find a security issue, please report it privately to the Vulpine team instead of opening a public issue.

## Credits

Made by tordev and the Vulpine team.

## License

The source code is released under the MIT License (see `LICENSE`). Third-party libraries keep their own licenses, listed in `THIRD_PARTY_NOTICES.md`. The Vulpine name, logo, artwork and decorations remain the property of the Vulpine team.

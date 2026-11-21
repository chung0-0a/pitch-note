# Pitch Note — Android native project

A native Java Android app (no WebView or web hosting). Features: sustained polyphonic synthesized keyboard notes while pressed, Android microphone permission, real-time YIN pitch estimation, note/cent comparison, 12-second graph, octave selection (C3–B5).

## Build APK
1. Open this folder as a project in Android Studio (recent version supporting Android Gradle Plugin 8.7.3).
2. Let Gradle sync and install Android SDK Platform 35 if prompted.
3. Select **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
4. Find `app/build/outputs/apk/debug/app-debug.apk` and install it on your Android phone.

The source uses the Android SDK and Java 17 only; no third-party libraries. RECORD_AUDIO is requested at runtime. Mic data stays on device and is not saved or uploaded.

## Notes
- This is a first-pass project, not a device-tested release APK.
- Pitch estimation can be affected by noise and speaker bleed; use headphones for cleaner monitoring.
- Target note is selected by touching a key. Press and hold for sustained sound; release to stop.

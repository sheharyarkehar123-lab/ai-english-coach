# AI English Coach

Android English-speaking coach with typed English practice.

Version 2.0 adds a live AI voice call alongside the offline practice session. The live call streams microphone audio over WebRTC, plays the coach's speech, shows learner and coach transcripts, supports mute/end controls, and instructs the coach to correct useful English mistakes naturally. Offline typed/speech practice, rule feedback, spoken playback, selectable modes, camera preview, captions, and session summaries remain available.

## Live AI call setup

The Android app requires a small HTTPS backend because the standard OpenAI API key must stay on a trusted server. The backend is in `backend/` and follows the Realtime API unified WebRTC flow.

1. Deploy `backend/` to a Node 20 host or container platform.
2. Set `OPENAI_API_KEY` only in the host's secret environment settings.
3. Set `ALLOWED_ORIGIN=https://appassets.androidplatform.net`.
4. Check `https://YOUR_HOST/health`; it should return `{"status":"ok"}`.
5. In the app, paste the HTTPS host address into **Secure backend URL** and select **Start live AI call**.

Local backend tests:

```bash
cd backend
npm ci
npm test
```

The backend validates SDP input, rate limits session creation, restricts browser origin, and never returns the standard API key. For a public release, add your own user authentication and replace the fallback safety identifier with a stable, privacy-preserving hashed user ID.

## Typed practice

Enter a sentence in **Typed English practice** and tap **Check sentence** (or the keyboard Done action). Feedback is labeled **Rule-based feedback (offline)** and contains the original sentence, a suggested correction, and short explanations. Editing the input clears the previous feedback. Empty input and input over 500 characters show validation messages.

The local checker supports narrow patterns for pronoun agreement with be/have/do, modal verbs followed by an incorrect `to`, lowercase `i`, and the pattern `I am working here since five years` (including other numeric durations). For example:

- `They is ready.` → `They are ready.`
- `She have a book.` → `She has a book.`
- `I can to swim.` → `I can swim.`
- `I am working here since five years.` → `I have been working here for five years.`

This is a limited rule checker, not an AI service or comprehensive grammar validator. When nothing matches, it says that unsupported errors may still exist. Typed practice is independent of the existing microphone/camera/caption controls. Speech recognition remains separate work.

## Build and test

Use JDK 17, Gradle 8.10.2, and the Android SDK with platform 35. This repository does not include a Gradle wrapper. Android Studio can install the SDK; set `ANDROID_HOME` or `android/local.properties` with your SDK path.

```bash
cd android
gradle --no-daemon testDebugUnitTest lintDebug assembleDebug
```

The APK is written to `android/app/build/outputs/apk/debug/app-debug.apk` (from the repository root). GitHub Actions runs the same checks on pushes to main and pull requests, and publishes the `ai-english-coach-apk` artifact.

Manual checks: deploy the backend and verify `/health`; start a live call, speak multiple turns, interrupt naturally, mute/unmute, confirm both transcripts, end and restart; then test supported/unsupported offline sentences, whitespace, 500-character validation, speech input, feedback playback, mode switching, camera permission/capture, captions, rotation, and session restart.

## Future AI integration

`GrammarCorrectionService` is a suspendable interface injected into `CallViewModel`; `RuleBasedGrammarCorrectionService` is the default offline implementation. A future implementation can return `GrammarFeedback` with its own accurate source label. The ViewModel already handles pending requests, duplicate submissions, cancellation, and retryable failures.

Route AI requests through an authenticated HTTPS backend. Store provider credentials only on that server, never in Kotlin, resources, BuildConfig, or the APK. The backend should validate input, enforce quotas, and handle provider calls. Add a network client when implementing that service; the manifest already declares Internet permission. No network calls or credentials are included in the offline implementation.

# AI English Coach

Android English-speaking coach with typed English practice.

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

Manual checks: submit different supported examples, an unsupported sentence, whitespace, and an input over 500 characters; edit and resubmit; rotate the device; check scrolling with the keyboard open; toggle Mic, Camera, and CC before and after submitting.

## Future AI integration

`GrammarCorrectionService` is a suspendable interface injected into `CallViewModel`; `RuleBasedGrammarCorrectionService` is the default offline implementation. A future implementation can return `GrammarFeedback` with its own accurate source label. The ViewModel already handles pending requests, duplicate submissions, cancellation, and retryable failures.

Route AI requests through an authenticated HTTPS backend. Store provider credentials only on that server, never in Kotlin, resources, BuildConfig, or the APK. The backend should validate input, enforce quotas, and handle provider calls. Add a network client when implementing that service; the manifest already declares Internet permission. No network calls or credentials are included in the offline implementation.

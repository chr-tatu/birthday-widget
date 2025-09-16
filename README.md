# Birthday Widget

An Android home-screen widget that surfaces upcoming birthdays from your Google Contacts.

## Features
- Google Sign-In with the People API (`contacts.readonly` scope).
- Background sync worker that fetches birthdays within the next 14 days.
- Lightweight contact photo cache for widget thumbnails.
- Jetpack Glance widget UI grouped by day with automatic refresh four times per day (via the widget provider `updatePeriodMillis`).

## Getting started
1. Create a Google Cloud project, enable the People API, and configure an OAuth 2.0 **Android** client.
2. Download the generated `google-services.json` and place it in `app/`.
3. Open the project in Android Studio Giraffe (or newer) and let it sync dependencies.
4. Provide a signing certificate fingerprint that matches your debug/release keystore in the Google Cloud Console.
5. Build & install the app on a device with Google Play services.
6. Launch the app, sign in with Google, then add the birthday widget to your home screen.

The widget refreshes automatically every six hours (four times per day) using the platform scheduler; no custom alarms are required.

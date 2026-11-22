# Price Watch

An Android app with a home-screen widget that watches retail prices and
notifies you when an item hits its lowest price.

## Install on your phone

1. On your phone, open the [latest release](../../releases/latest) and download `PriceWatch.apk`.
2. Open the file. Android will ask you to allow installs from your browser the first time.
3. Open **Price Watch** and allow notifications.
4. Long-press your home screen → **Widgets** → **Price Watch**, and drag it onto the screen.

New builds install over the old one and keep your price history.

## Using it

- Tap an item to edit it and paste product links, one per line. You can mix stores
  (Amazon, Best Buy, Walmart, Target, GameStop…). The app uses the cheapest one.
- Optional: set a target price to also be alerted at or below that price.
- Prices are checked every 6 hours while the phone has a connection. Tap **Check now**
  in the app, or ↻ on the widget, to check immediately.

You get a notification when:

- the price drops below the lowest price seen so far,
- the price climbs and then comes back down to its lowest, or
- it reaches your target price.

## How "lowest" works

This app is free and uses no paid price-history service, so it reads prices directly
from the store pages and keeps its own history. "Lowest" means the lowest price the
app has seen since you added the link, and it gets more meaningful the longer it runs.

Stores sometimes block automated requests or change their pages. If an item shows
"Couldn't read a price from any link", try a link from a different store.

## Building

GitHub Actions builds the APK on every push to `main` and attaches it to a release.
Locally: `./gradlew assembleDebug` with the Android SDK installed.

The debug signing key in `app/debug.keystore` is committed on purpose so that every
build has the same signature and installs as an update. It is not a secret.

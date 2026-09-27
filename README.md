# CoinSprout — Android Studio Prototype

A Jetpack Compose implementation of the core CoinSprout screens, matching the
visual language of the clickable HTML mockup shared earlier.

## Screens included (MVP core)
Splash -> Sign-up/OTP -> Risk Quiz -> Risk Result -> Dashboard -> Portfolio
(with growth simulator) -> Goals -> Compare Modes (AI vs. fixed + trust rating)

Dashboard, Portfolio, and Goals share a bottom tab bar, same as a real app shell.
The risk category you pick in the quiz drives the allocation donut, the "why
this split" copy, and the growth simulator numbers on every later screen.

## How to open this in Android Studio
1. Open Android Studio (Koala/2024.1 or newer recommended).
2. File -> Open... and select this `CoinSprout` folder.
3. Let Gradle sync (first sync needs internet access to Google's Maven repo).
4. Run on an emulator or device (minSdk 24).

## What's not included yet (kept out on purpose, to stay MVP-scoped)
- Transaction/Activity feed, Education, Notifications, and Settings screens
  (all mocked already in the HTML prototype — say the word and I'll port
  them into Compose too).
- Real navigation animations between screens (Navigation Compose's default
  fade/slide can be added via `enterTransition`/`exitTransition` per route).
- Fraunces/Manrope fonts — currently approximated with FontFamily.Serif /
  FontFamily.SansSerif. Drop the .ttf files into `res/font/` and reference
  them in `ui/theme/Type.kt` to match the mockup exactly.
- Any real AI model, NSE/CBK data, or backend — allocations and growth
  numbers are hardcoded illustrative values in `data/RiskData.kt`.

## Project structure
```
app/src/main/java/com/coinsprout/app/
  MainActivity.kt
  state/AppState.kt          shared UI state (risk category, mode, etc.)
  data/RiskData.kt           allocation %, descriptions, growth multipliers
  navigation/CoinSproutNavGraph.kt
  ui/theme/                  Color.kt, Type.kt, Theme.kt
  ui/components/             AllocationDonut, CoinSproutBottomBar
  ui/screens/                one file per screen
```

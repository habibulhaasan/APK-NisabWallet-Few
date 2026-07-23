# CLAUDE.md

This file gives Claude Code the context it needs to work in this repository.

## Project Overview

**Nisab Wallet** (package `com.hasan.nisabwallet`) is a native Android personal-finance
app with an Islamic-finance focus: accounts, transactions, transfers, and categories for
everyday money management, plus Zakat/Nisab tracking, Riba tracking, jewellery inventory
with live metal pricing, loans, lendings, goals, investments, tax preparation
(Bangladesh NBR-oriented), analytics/cashflow, subscription/settings, and two
power-user "admin" tools (Monthly Ledger, Monthly Grocery). See Feature Coverage below
for what is and isn't implemented.

There is no README in this repo — this CLAUDE.md is the primary map of the codebase.

Architecturally, this is a **lightweight, direct-to-Firebase app**: no local database, no
custom sync engine. Screens talk to Firebase Auth/Firestore straight from their
ViewModels, and rely on Firestore's own built-in disk cache for offline reads.

## Tech Stack

- **Language**: Kotlin, Jetpack Compose (Material 3, plus the non-M3 `compose-material`
  artifact for `PullRefreshIndicator`/`pullRefresh`, used in `DashboardScreen.kt` and
  `TransactionsScreen.kt`)
- **DI**: Hilt (single `di/FirebaseModule.kt`)
- **Backend**: Firebase Auth + Firestore only — no Room, no Retrofit, no WorkManager
- **Offline support**: Firestore's native `persistentCacheSettings`, configured in
  `FirebaseModule.kt` — not a custom sync layer
- **Metal price scraping**: `org.jsoup:jsoup` used directly inside `ZakatViewModel.kt`.
  Fetch order: scrape `goldr.org` first, fall back to `bajus.org/gold-price`, then fall
  back further to `gold-api.com` (XAU/XAG spot) + `open.er-api.com` (USD→BDT FX) — all via
  `Jsoup.connect(...)` calls made directly from the app, no backend intermediary.
- **Min/target/compile SDK**: minSdk 24, target/compileSdk 34, JVM target 17
- **Compose Compiler**: legacy mechanism — `composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }`
  with Kotlin 1.9.24. Do **not** switch to the Kotlin 2.0+
  `org.jetbrains.kotlin.plugin.compose` plugin approach without also upgrading Kotlin —
  the two mechanisms are mutually exclusive and this repo is pinned to the older one.
- **Firebase artifacts**: uses non-`.ktx` libraries (`firebase-auth`, `firebase-firestore`)
  — a comment in `build.gradle.kts` notes ".ktx removed," so don't reflexively re-add
  `-ktx` suffixes when touching these dependencies.
- **Build**: Gradle Kotlin DSL, version catalog at `gradle/libs.versions.toml`. The Gradle
  wrapper (`gradlew`, `gradlew.bat`, wrapper jar) is committed and ready to use — no setup
  step needed to generate it.

## Repo Contents Beyond Source

- **`app/release/app-release.apk`** — a committed, pre-built release APK (~19 MB) plus
  `baselineProfiles/` and `output-metadata.json`. Treat as a build artifact, not something
  to hand-edit. Regenerate via `./gradlew assembleRelease` if it needs updating, and check
  with the user before overwriting or deleting it — it may be a deliberately preserved
  release build.
- **`APK/123456`** — an oddly-named file/folder at the repo root. Inspect its actual
  contents before assuming its purpose; the name suggests placeholder/test content rather
  than a real artifact.
- **`app/google-services.json`** — verify whether this is a real Firebase config before
  relying on it. If it is real, treat it as sensitive: don't paste its contents into
  commits, PRs, or issue text.

## Setup

1. Open the project root in Android Studio.
2. Gradle wrapper is already committed — no manual wrapper generation needed.
3. Confirm `app/google-services.json` is a valid config registered under
   `com.hasan.nisabwallet`, or replace it if it's a placeholder.
4. Sync Gradle, then run on an emulator or device.

No test source sets or CI config are present in this repo.

## Architecture

```
app/src/main/java/com/hasan/nisabwallet/
  MainActivity.kt          # @AndroidEntryPoint, sets content to NisabWalletRootNav()
  NisabWalletApp.kt        # Application class (Hilt entry point)
  core/util/
    CurrencyFormatter.kt
    HijriConverter.kt       # Hijri calendar conversion used by Zakat due-date logic
    NetworkMonitor.kt
    TaxCategoryUtils.kt
    ZakatCalculator.kt
  di/
    FirebaseModule.kt       # Provides FirebaseAuth + FirebaseFirestore (with persistent cache settings)
  navigation/
    NavGraph.kt             # Routes object + NavHost + bottom-nav tab config, all in one file
  ui/
    theme/                  # Color.kt, Theme.kt
    screens/
      auth/                 # LoginScreen, RegisterScreen, AuthViewModel
      dashboard/, transactions/, accounts/, categories/, cashflow/, analytics/
      zakat/, riba/, jewellery/
      goals/ (+ goals/detail/), investments/ (+ investments/detail/),
      loans/ (+ loans/detail/), lendings/ (+ lendings/detail/)
      tax/ (+ tax/setup/, tax/detail/)
      subscription/, settings/
      admin/ledger/, admin/grocery/   # Monthly Ledger + Monthly Grocery power-user tools
      common/ComingSoonScreen.kt      # Placeholder screen for not-yet-built features
```

Each feature is a flat `Screen.kt` + `ViewModel.kt` pair — **there is no
`domain/`/`repository/` layer**. ViewModels call Firebase Auth/Firestore directly. When
adding a new feature, follow this same flat pattern: put a new `ui/screens/<feature>/`
package with `<Feature>Screen.kt` and `<Feature>ViewModel.kt`, and let the ViewModel own
its own Firestore reads/writes rather than introducing a repository abstraction.

### Routes (`navigation/NavGraph.kt` → `object Routes`)

```
login, register, dashboard,
dashboard/transactions, dashboard/accounts, dashboard/categories, dashboard/transfer,
dashboard/goals, dashboard/jewellery, dashboard/analytics, dashboard/cashflow,
dashboard/riba, dashboard/zakat,
dashboard/tax, dashboard/tax/setup, dashboard/tax/{id},
dashboard/subscription,
dashboard/investments, dashboard/investments/{investmentId},
dashboard/loans, dashboard/loans/{loanId},
dashboard/lendings, dashboard/lendings/{lendingId},
dashboard/admin/monthly-ledger, dashboard/admin/monthly-grocery-2,
dashboard/settings
```

Note: a `TRANSFER` route constant exists in `Routes` but wasn't confirmed wired to a
`composable(...)` destination during review — verify whether it's reachable in the UI
(it may be embedded in another screen, e.g. a modal on Accounts/Dashboard, rather than a
standalone route) before assuming it's a complete, navigable feature.

## Feature Coverage

**Present**: Auth, Dashboard, Transactions, Accounts, Categories, Goals (+ detail),
Jewellery, Investments (+ detail), Loans (+ detail), Lendings (+ detail),
Tax (+ setup/detail), Zakat, Riba, Analytics, Cashflow, Subscription, Settings,
Monthly Ledger, Monthly Grocery.

**Not present**: Budgets, Recurring Transactions, Shopping Lists, Notifications, any
offline sync engine beyond Firestore's built-in cache. If asked to add any of these,
treat it as new feature work with no existing scaffolding to extend — follow the flat
`Screen + ViewModel` pattern used throughout the rest of the app.

## Conventions & Gotchas

- **No repository/domain layer** — ViewModels own their Firebase calls directly. Don't
  introduce a repository abstraction unless explicitly asked to refactor.
- **Metal price fetching is fragile, HTML-scraping-based logic** living entirely inside
  `ZakatViewModel.kt` (goldr.org → bajus.org → gold-api.com/open.er-api.com fallback
  chain via Jsoup). If goldr.org or bajus.org change their page structure, this will
  silently fall through to the spot-price fallback — check `ZakatViewModel.kt`'s
  scraping functions first if Zakat/jewellery prices look wrong or stale.
- **Compose Compiler version is pinned to the legacy mechanism.** Don't upgrade Kotlin to
  2.0+ without also removing the `composeOptions {}` block and switching to the
  `org.jetbrains.kotlin.plugin.compose` Kotlin-plugin approach — mixing the two causes
  Compose Compiler/Kotlin version mismatches.
- Three independent Hijri-date conversion routines can exist across a codebase like this
  over time (in case this app is later ported elsewhere) — if Hijri-date accuracy ever
  matters for a Zakat due-date decision, validate `HijriConverter.kt`'s output rather than
  assuming correctness.
- Route strings mirror likely equivalent web-app URL paths (e.g.
  `dashboard/admin/monthly-grocery-2`) — keep that naming convention for any new routes
  you add, for consistency with the rest of the route table.

## Working in This Repo

- Keep Firestore query/write logic inside the relevant `ViewModel.kt` — that's the
  established pattern for every feature in this app.
- Before modifying Zakat/jewellery pricing logic, read all of `ZakatViewModel.kt`'s
  fetch/fallback chain first; it's one function with several nested fallbacks, not
  several independent code paths.
- Run a full Gradle sync/build after any dependency or Compose Compiler version change,
  given how version-sensitive the current pinned setup is.
- When adding a new screen, mirror the existing `ui/screens/<feature>/` structure
  (`Screen.kt` + `ViewModel.kt`, and a `detail/` subpackage if the feature needs a
  detail view) and register its route in `navigation/NavGraph.kt`.
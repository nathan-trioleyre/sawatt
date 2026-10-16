# Contributing to SaWatt

This guide is for developers: how to build SaWatt, how the code is organised, and the rules every
change follows. For what the app does, see the [README](README.md).

## Development setup

You need JDK 17 or 21 (Gradle 8.14 does not run on JDK 25) and the Android SDK with platform 36.
`local.properties` (not versioned) points to the SDK, e.g. `sdk.dir=/home/<you>/Android/Sdk`.

```sh
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
./gradlew :core:test                    # unit tests (protocol, formulas, interval engine, history)
./gradlew :app:lintDebug :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Application id: `app.sawatt`. Every pull request also attaches a ready-to-install debug APK to its
CI run (see [Continuous integration](#continuous-integration)).

## Project structure

```
core/   Pure Kotlin/JVM, unit-tested
        ble/       Cycling Power / Heart Rate / advertising parsers, cadence calculation
        trainer/   TrainerConnection interface + SimulatedTrainer (hardware-free trainer)
        ride/      virtual speed, kcal, FTP and max HR estimates, power zones
        workout/   WorkoutPlan, WorkoutSession (interval engine), statistics
        history/   week/month/year aggregation, JSON configuration
app/    Android app
        ble/       BleManager (TrainerConnection over Bluetooth), SwitchingTrainerConnection
                   (switches between real and simulated trainer from the "Simulated bike" setting)
        workout/   WorkoutController (1 Hz clock, beeps, saving), WorkoutService (foreground service)
        data/      JSON-file repositories (profile, settings, plans, history)
        ui/        Compose screens
```

## How it works

```
Bike ────BLE──► TrainerConnection ──► WorkoutController ──► WorkoutSession ──► Screens
HR strap ─BLE──┘ (BleManager or       (1 Hz clock,          (interval          (Compose)
                  SimulatedTrainer)    beeps, saving)        engine)
                                                                  │
                                                                  ▼
                                                  HistoryRepository (JSON files)
```

- All sensor communication goes through the `TrainerConnection` interface (`core`). `BleManager`
  implements it over Bluetooth, `SimulatedTrainer` without any hardware; unit tests and the demo
  mode use the latter.
- The bike sends about two measurements per second: instantaneous power and a timestamped crank
  revolution counter. Cadence is derived from that counter: `60 × Δrevolutions × 1024 / Δtime`.
- The engine (`WorkoutSession`) has no clock of its own. `tick()` is called once per second of
  riding. On each call it:
  - averages the readings received during that second into one sample;
  - adds the sample to the whole session and to the current interval;
  - advances the plan.
- Pausing stops the clock. With no data for 3 s, power drops to 0 W.
- When a workout ends, the summary and per-interval statistics are saved to `history/index.json`,
  and the second-by-second trace to `history/<id>.samples.json`.

## Workflow: GitHub Flow

- `main` is always stable: it compiles and its tests pass.
- Every task is done on a short-lived branch created from an up-to-date `main`.
- Releases are tags on `main`.
- There is no `develop` branch, and no `release` or `hotfix` branches, unless the maintainer
  explicitly asks for one.

### Branches

- A branch is named after the type of task, followed by a short kebab-case topic:
  - `feat/`: new feature;
  - `fix/`: bug fix;
  - `refactor/`: restructuring without behaviour change;
  - `docs/`: documentation;
  - `chore/`: tooling, dependencies, CI.

  For example: `feat/countdown-beeps`, `fix/cadence-overflow`.
- A branch covers **one topic only**. If a task grows beyond that, split it into several branches.

### Protecting `main`

- Never commit directly to `main`.
- Never force-push to `main` or rewrite its history.
- Everything reaches `main` through a pull request.
- **Nothing is pushed, no PR is merged and no tag is created without the maintainer's explicit
  approval.**

## Commits

### Before every commit

Run lint and tests. Do not commit if anything fails.

```sh
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
./gradlew :core:test :app:lintDebug :app:assembleDebug
```

- Never commit secrets, local configuration (`local.properties`, IDE files) or build artifacts.
- Keep commits **small**, with one logical change per commit.

### Messages: Conventional Commits

```
<type>(<optional scope>): <description>

<optional body: the why, when it is not obvious>

<BREAKING CHANGE: … when needed>
```

- **Types**: `feat`, `fix`, `refactor`, `test`, `docs`, `chore`, `ci`, `perf`.
- **Common scopes**: `ble`, `workout`, `history`, `ui`, `core`, `build`.
- **Description**:
  - in English;
  - in the imperative mood;
  - lowercase;
  - no trailing period;
  - under 72 characters.
- Add a **body** when the reason is not obvious.
- Flag a **breaking change** with `!` after the type (`feat(core)!: …`) or a `BREAKING CHANGE: …`
  line.

Examples:

```
feat(ble): add FTMS trainer discovery
fix(workout): keep interval timer running after reconnect
refactor(core)!: rename StatsSummary fields
```

## Pull requests

- The title uses the same format as commit messages.
- The description states:
  - **what changes**;
  - **why**;
  - **how to test**.

  The `.github/pull_request_template.md` template provides these three sections.
- CI must pass before merging.
- Pull requests are **squash-merged** by default; the squash commit takes the PR title, so `main`
  keeps a clean Conventional Commits history.

## Continuous integration

The `.github/workflows/ci.yml` workflow runs on every PR and on `main`:

| Job | Purpose |
|---|---|
| `changes` | Detects whether the PR only touches documentation (`*.md`, `docs/`); if so, the next jobs are skipped. |
| `unit tests` | `./gradlew :core:test` |
| `lint and build` | `./gradlew :app:lintDebug :app:assembleDebug`. On a PR, the debug APK is attached to the run (`sawatt-debug-apk` artifact, kept 14 days) so it can be installed on a phone. |
| `ci` | Summary job and the **only check required** by `main`'s branch protection. It fails if any job failed. |

- The Gradle cache (dependencies and build cache) is only written by runs on `main`. PRs read it, so
  only what changed is rebuilt.
- Actions are pinned to commit SHAs. Dependabot proposes updates every month.

## Versions and releases

- Semantic versioning: `vMAJOR.MINOR.PATCH`.
- A release is a tag on `main`, created **only when the maintainer asks for it**.
- The changelog is generated from commit messages.

## Architecture rules

- All Bluetooth communication goes through an **abstract interface**.
- A **simulated** trainer implementation is used for tests.
- Interval workout logic **never depends directly** on the Bluetooth layer.
- **No unit test may require real hardware.**

## Documentation

Repository documentation (README, this guide, PR template) is written in English.

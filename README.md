# SaWatt

> *Ça watt ? Ça watt !* — French for "Pushing the watts? Pushing the watts!": are you pedalling hard?
> Yes you are.

**Interval training for indoor cycling, built around one number: your average power on the
interval you are riding right now.**

Most indoor bike apps show either your instantaneous watts, which jump around with every pedal
stroke, or the average of the whole session, which tells you nothing about the effort you are in.
SaWatt shows what you actually need during intervals: **the average power since the current
interval started**, colour-coded against your target. Ride `3 × 15' @ 250 W` and you know, block by
block, whether you are on target.

## What you can do with SaWatt

### Ride structured workouts
- Build your own sessions from blocks: duration, target power in watts or as a percentage of your
  FTP, and, if you want, target cadence. Percentage targets follow your FTP as it improves.
- Repeat sets in one tap: `10' warm-up → 3 × [15' @ 250 W + 5' recovery] → 10' cool-down`.
- During the ride, see the current target, the time left in the interval, a progress bar and what
  comes next.
- A beep marks every transition, so you can keep your eyes off the screen.

### Know exactly how each interval went
- Live **interval average power**, green when you are on target, blue below, red above.
- Max power, average cadence and average heart rate for the interval.
- At the end, a summary table with **one row per interval**, saved in your history.

### Split a free ride whenever you want
- Press **Lap** at any time to start a new interval with its own averages: perfect for climbs,
  sprints or unplanned efforts.

### Follow your progress
- Every workout keeps its interval table, a power chart with your targets and the time spent in
  each power zone.
- Weekly, monthly and yearly summaries: time, distance, calories, average and max power.

### Train on your numbers
- Enter your FTP and max heart rate, or let SaWatt estimate them from your profile.
- Metric or imperial units.

### Private by design
- No account, no ads, no tracking.
- SaWatt does not even request internet access: your workouts stay on your phone.

## Compatibility

### Bikes, trainers and power meters
SaWatt works with any device that broadcasts power over the standard **Bluetooth Cycling Power
service**. That includes:

- **indoor and spin bikes with Bluetooth power output**, including self-powered bikes that wake up
  when you start pedalling (SaWatt reconnects automatically);
- **smart trainers**: most of them broadcast the Cycling Power service alongside their trainer
  protocol;
- **Bluetooth power meters**: pedals, cranks and spiders.

Cadence comes from the same device when it reports crank revolutions (most power meters and many
bikes do; some smart trainers do not).

### Heart-rate monitors
Any chest strap or armband using the standard **Bluetooth Heart Rate service**, which covers the
vast majority of Bluetooth heart-rate monitors.

### Phones
- Android 8.0 or later with Bluetooth Low Energy.
- Tested on a Google Pixel 9a (Android 17).

### Not supported yet
- **ANT+** sensors: Bluetooth only for now.
- **Trainer resistance control (ERG mode)** and trainers that only expose the FTMS protocol: SaWatt
  reads power from the Cycling Power service and does not set the resistance.
- **Speed/cadence-only sensors** without power measurement.
- Bikes that only talk to their own app through a proprietary protocol.
- iPhone.

Not sure about your device? Look for "Bluetooth Cycling Power", "BLE power" or "CPS" in its
specifications. A trainer that only advertises the trainer control protocol (FTMS) is not
supported yet.

## Try it without a bike

Turn on **Settings → Simulated bike**: a virtual rider and heart-rate strap connect instantly and
follow your workout's targets. It is the quickest way to discover the app.

## Getting the app

SaWatt is not on the Play Store yet. In the meantime you can install a test build (debug APK)
attached to each pull request's CI run, or build it yourself: see
[CONTRIBUTING.md](CONTRIBUTING.md).

## Roadmap

- Countdown beeps (3-2-1) before each transition.
- French interface.
- Workout export (TCX/FIT) and Strava upload.
- Data backup and restore.
- FTP test, VO2max test, levels and weekly challenges.

## Contributing

SaWatt is developed in the open. Read [CONTRIBUTING.md](CONTRIBUTING.md) for the development setup,
how the app works under the hood, and the workflow rules.

## License

No license has been chosen yet: until then, all rights are reserved.

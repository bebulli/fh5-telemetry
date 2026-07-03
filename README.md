# FH5 Telemetry

A little telemetry tool for Forza Horizon 5. It listens for the game's UDP "Data Out" feed, decodes the packets, and turns a short driving sample into a starting tuning setup. Built to run on a PC while the game itself runs on a PS5 on the same network.

## What it does

- Listens for FH5's live telemetry over UDP and parses the Sled and Dash packet formats.
- Shows live telemetry: speed, RPM, gear, tire slip, tire temp, suspension travel, acceleration.
- Tells the difference between the car sitting still and actually driving, so tuning math only uses real driving data.
- Records a session to a file and can replay it later without the game running.
- Given the car's weight, drivetrain, power and Performance Index, suggests tire pressures, camber/toe/caster, ride height, aero, brake balance/pressure, differential lock, anti-roll bar stiffness, spring/damper rates and gearing direction, either for grip or for drift.
- A small local web UI to do all of the above without touching the command line.

## Setup

You need a JDK (21 or newer). You do not need Maven installed, the project ships with the Maven wrapper.

```
./mvnw compile
```

### Point the PS5 at this PC

1. On the PS5, in Forza Horizon 5 go to **Settings > HUD and Gameplay** and scroll to the bottom.
2. Set **Data Out** to On.
3. Set **Data Out IP Address** to this PC's LAN IP (find it with `ipconfig` on Windows).
4. Set **Data Out IP Port** to **6767**.

The PS5 and PC need to be on the same local network. That's it, the game will start streaming telemetry as soon as you're in a car.

## Running it

```
java -cp target/classes com.fh5.telemetry.Main
```

This starts the UDP listener on port 6767 and a small web UI at `http://localhost:7070`. Open that in a browser to see live telemetry, start/stop recordings, replay a saved session, and get a tuning recommendation. There's a units toggle (English/Metric) for speed, tire temp and pressure, plus a separate spring rate unit picker (lb/in, N/mm, kgf/mm) since that's independent of the rest.

Other modes, all run the same way with an argument after `Main`:

| Mode | What it does |
|---|---|
| `ui` (default) | Full app: listener, API, web UI |
| `listen` | Console-only live telemetry, no UI |
| `sample` | Runs a synthetic driving session through the parser and tuning engine, no game or network needed |
| `sniff` | The step-zero raw UDP sniffer, just prints packet size and the first bytes, useful for confirming the PS5 is actually reaching the PC before trusting the parser |

### Example console output

```
[DRIVING] speed= 78.3 mph  rpm= 4500/ 7800  gear=1  power=295hp torque=450Nm fuel=0%
  slip ratio     FL=  0.05 FR=  0.05 RL=  0.03 RR=  0.03
  tire temp (C)  FL=  80.0 FR=  80.0 RL=  75.0 RR=  75.0
  susp travel    FL=  0.40 FR=  0.40 RL=  0.35 RR=  0.35
  accel (g)      x= 0.02 y=-1.00 z= 0.11
```

## How the tuning recommendations work

Drivetrain and Performance Index are already in the telemetry, so the web UI fills those in for you, along with an estimate of power (the highest power reading seen so far in the driving sample, not the car's official rated figure). Weight is the one thing that genuinely isn't in the telemetry (Forza only sends a car/class ordinal, no lookup table for mass), so that stays manual. If the PI changes mid-session (you swapped cars or changed the build), the sample window, peak power, top speed and tuning history all reset automatically since none of it describes what you're driving now.

There are two reset buttons: **Reset sample window** clears everything (slip/temp averages, peak power, top speed, tuning history). **Reset peak power & max speed** only clears those two figures (handy after a straight-line pull, so you can see a fresh peak without losing the rest of the session); it's a no-op if both are already zero.

The tuning engine combines the car spec with whatever the tires and suspension are reporting from the driving sample:

- **Tire pressure** starts from a class-based baseline (lower for higher Performance Index, closer to race tire pressures) and shifts based on how hot the tires are actually running versus a normal operating window.
- **Camber, toe and front caster** start from drivetrain and class baselines, then camber gets nudged by comparing front vs rear tire slip angle. Front slip angle running noticeably higher than rear is an understeer signature and adds front camber; the reverse adds rear camber. More caster is suggested when understeer is reported since it increases camber gain while turning.
- **Ride height** is a relative 0-10 level, matching how FH5 actually presents it (the real mm range behind that slider differs car to car). It leans lower for higher PI, with a bit of front rake for grip-style setups.
- **Aero** is in kgf (or lbf in English units), bounded to roughly 122-267 to match what a race-modified car's aero sliders actually allow. It scales up with PI and drops to the floor for a drift setup, since downforce fights the rotation a drift needs.
- **Brake balance and pressure** default to a front-biased split adjusted for weight distribution, with pressure trending higher for higher-PI cars.
- **Differential lock** starts from typical per-drivetrain baselines and responds differently depending on drivetrain: a fully locked FWD diff under power tends to pull the car straight, so reported understeer loosens the accel lock; snap oversteer on lift-off/trail-braking is a RWD/AWD trait, so reported oversteer loosens the decel lock instead. Traction loss increases accel lock on any drivetrain. AWD cars get separate front and rear diff settings plus a center transfer case split (0% = all torque to the front axle, 100% = all to the rear), instead of the single diff a FWD or RWD car has. Drift setups push accel/decel lock and the center split further toward the rear so the car holds a slide predictably.
- **Anti-roll bars** follow the classic drivetrain tendencies (FWD leans soft front/stiff rear, RWD the opposite) scaled by weight and PI, then adjusted the same way as camber.
- **Springs** scale with weight on each axle. The N/mm range this produces (roughly 528 to 2641) matches the bounds of FH5's own spring rate slider on a race-modified car.
- **Dampers** are derived from the spring rates.
- **Gearing** is descriptive rather than exact ratios, since the ratio table isn't in the telemetry, it leans toward acceleration or top speed based on power-to-weight.

Choosing **Drift** instead of **Grip** doesn't rerun different math from scratch, it applies a second pass of adjustments on top: less rear pressure and camber (looser rear end), more front bite, a stiffer rear bar, minimum aero, more differential lock and center split toward the rear, and shorter gearing to hold the car in its torque band mid-slide.

The web UI also has a checklist (understeer, oversteer, traction loss) so you can tell it directly what the car is doing instead of waiting for the telemetry averages to pick it up. Checking one applies its own corrective nudge on top of whatever the telemetry already suggested, and shows up as a separate note in the result so you can see which adjustments came from your input versus the data.

The app remembers your last 5 recommendations for the current car. If the same issue (say, the same understeer signature) shows up again on your next request, it says so explicitly in the notes rather than silently repeating the same small nudge, that repeat is the signal that the last tune, if you applied it, might need a bigger change than the automatic adjustment gives. This history resets whenever the sample window resets (either reset button, or a detected PI change) since it's tied to the car currently being tuned.

This is a starting point for further adjustment on track, not a physics solver, Forza doesn't publish the internal formulas it uses for its own tuning screen.

## Tests

```
./mvnw test
```

Covers the packet parser (known sample packets in, correct fields out) and the tuning engine (known car specs in, sane and correctly-ordered output ranges out).

## Project layout

```
net/       UDP listener
parser/    Packet format and decoding
model/     Telemetry data types
tuning/    Heuristics engine, car spec, sample aggregation
recording/ Session recording and replay
sample/    Synthetic packets for tests and offline demos
app/       Wires it all together
api/       Local REST API + static file serving for the web UI
display/   Console output
```

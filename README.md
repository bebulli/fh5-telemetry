# FH5 Telemetry

A little telemetry tool for Forza Horizon 5. It listens for the game's UDP "Data Out" feed, decodes the packets, and turns a short driving sample into a starting tuning setup. Built to run on a PC while the game itself runs on a PS5 on the same network.

## What it does

- Listens for FH5's live telemetry over UDP and parses the Sled and Dash packet formats.
- Shows live telemetry: speed, RPM, gear, tire slip, tire temp, suspension travel, acceleration.
- Tells the difference between the car sitting still and actually driving, so tuning math only uses real driving data.
- Records a session to a file and can replay it later without the game running.
- Given the car's weight, drivetrain, power and Performance Index, suggests tire pressures, camber/toe, anti-roll bar stiffness, spring/damper rates and gearing direction, either for grip or for drift.
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

Forza doesn't expose a car's weight, power, drivetrain or gear ratios over telemetry, only a car/class ordinal with no lookup table sent over the wire. So you enter those manually, and the tuning engine combines them with whatever the tires and suspension are reporting from a short driving sample:

- **Tire pressure** starts from a class-based baseline (lower for higher Performance Index, closer to race tire pressures) and shifts based on how hot the tires are actually running versus a normal operating window.
- **Camber and toe** start from drivetrain and class baselines, then get nudged by comparing front vs rear tire slip angle. Front slip angle running noticeably higher than rear is an understeer signature and adds front camber; the reverse adds rear camber.
- **Anti-roll bars** follow the classic drivetrain tendencies (FWD leans soft front/stiff rear, RWD the opposite) scaled by weight and PI, then adjusted the same way as camber.
- **Springs** scale with weight on each axle and get stiffened further if suspension travel telemetry shows the car bottoming out.
- **Dampers** are derived from the spring rates.
- **Gearing** is descriptive rather than exact ratios, since the ratio table isn't in the telemetry, it leans toward acceleration or top speed based on power-to-weight.

Choosing **Drift** instead of **Grip** doesn't rerun different math from scratch, it applies a second pass of adjustments on top: less rear pressure and camber (looser rear end), more front bite, a stiffer rear bar, and shorter gearing to hold the car in its torque band mid-slide.

The web UI also has a checklist (understeer, oversteer, traction loss, bouncy/bottoming-out suspension) so you can tell it directly what the car is doing instead of waiting for the telemetry averages to pick it up. Checking one applies its own corrective nudge on top of whatever the telemetry already suggested, and shows up as a separate note in the result so you can see which adjustments came from your input versus the data.

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

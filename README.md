# Pac-Man

A Pac-Man clone written in Java Swing.

## Run

Requires a JDK (11+).

```sh
cd Pacman/src
javac *.java
java App
```

(Or open the `Pacman` folder in VS Code with the Java extension and run `App`.)

## Controls

| Key | Action |
| --- | --- |
| Arrow keys / WASD | Move (turns are buffered, so press just before a corner) |
| P / Esc | Pause |
| Enter / Space | Restart after game over |

## Gameplay

- Pellets are worth 10, power pellets 50. A cherry appears twice per level for bonus points.
- Eating a power pellet makes the ghosts scared for 8 seconds (they flash when it's about to wear off). Eating ghosts in a row scores 200, 400, 800, 1600.
- Each ghost behaves differently: Blinky (red) chases you directly, Pinky (pink) aims ahead of you, Inky (blue) flanks using Blinky's position, and Clyde (orange) backs off when he gets close. Ghosts alternate between chasing and scattering to their corners.
- The side tunnels wrap around the board.
- Clear all pellets to advance a level; ghosts get faster each level. You have 3 lives.

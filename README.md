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

## Difficulty

Choose a difficulty from the start menu (Up/Down or 1/2/3, then Enter):

| Mode | Lives | Ghosts | Power-up time |
| --- | --- | --- | --- |
| Easy | 5 | Slow, often wander randomly | 10 s |
| Medium | 3 | Classic speed and behaviour | 8 s |
| Hard | 3 | Fast, rarely make mistakes | 5 s |

Each difficulty keeps its own top 5 high score table. Press H on the menu to see them all.

## Controls

| Key | Action |
| --- | --- |
| Arrow keys / WASD | Move (turns are buffered, so press just before a corner) |
| P / Esc | Pause (Q while paused quits to the menu) |
| M | Sound on/off |
| Enter / Space | Start from the menu / back to the menu after game over |
| H | Show the top 5 high scores (from the menu) |

## Gameplay

- Pellets are worth 10, power pellets 50. Points you earn float up where you earned them.
- A bonus fruit appears twice per level. The fruit changes as you go further:

  | Level | Fruit | Points |
  | --- | --- | --- |
  | 1 | Cherry | 100 |
  | 2 | Strawberry | 300 |
  | 3 | Orange | 500 |
  | 4 | Apple | 700 |
  | 5 | Melon | 1000 |
  | 6+ | Grapes | 2000 |
- Eating a power pellet makes the ghosts scared for 8 seconds (they flash when it's about to wear off). Eating ghosts in a row scores 200, 400, 800, 1600.
- Each ghost behaves differently: Blinky (red) chases you directly, Pinky (pink) aims ahead of you, Inky (blue) flanks using Blinky's position, and Clyde (orange) backs off when he gets close. Ghosts alternate between chasing and scattering to their corners.
- The side tunnels wrap around the board.
- Clear all pellets to advance a level; ghosts get faster each level. There are four mazes (Classic, Arena, Tunnels, Cross), each with its own wall colour, and the game cycles through them. You have 3 lives.
- Retro sound effects are generated in code (no audio files needed).
- If your score makes the top 5, you type your name at game over. Scores are saved per difficulty in `~/.pacman_scores_<difficulty>`, so they're kept between games.

import java.awt.Color;
import java.util.Random;

/** Maze layouts: eight of them, used in order for levels 1-8 and then picked at random. */
final class Mazes {

    // X = wall, O = empty (no food), P = pac man, * = power food, ' ' = food
    // F = food + where the bonus fruit appears
    // b = blue ghost, o = orange ghost, p = pink ghost, r = red ghost
    static final class Maze {
        final String name;
        /** null means draw walls with wall.png */
        final Color wallColor;
        final String[] rows;

        Maze(String name, Color wallColor, String[] rows) {
            this.name = name;
            this.wallColor = wallColor;
            this.rows = rows;
        }
    }

    static final Maze[] ALL = {
        new Maze("CLASSIC", null, new String[] {
            "XXXXXXXXXXXXXXXXXXX",
            "X        X        X",
            "X XX XXX X XXX XX X",
            "X*               *X",
            "X XX X XXXXX X XX X",
            "X    X       X    X",
            "XXXX XXXX XXXX XXXX",
            "OOOX X       X XOOO",
            "XXXX X XXrXX X XXXX",
            "O       bpo       O",
            "XXXX X XXXXX X XXXX",
            "OOOX X   F   X XOOO",
            "XXXX X XXXXX X XXXX",
            "X        X        X",
            "X XX XXX X XXX XX X",
            "X  X     P     X  X",
            "XX X X XXXXX X X XX",
            "X    X   X   X    X",
            "X XXXXXX X XXXXXX X",
            "X*               *X",
            "XXXXXXXXXXXXXXXXXXX"
        }),
        new Maze("ARENA", new Color(0xD63AF9), new String[] {
            "XXXXXXXXXXXXXXXXXXX",
            "X*   X       X   *X",
            "X XX X XXXXX X XX X",
            "X                 X",
            "XX X XXX X XXX X XX",
            "X  X     X     X  X",
            "X XXXXXX X XXXXXX X",
            "X    X       X    X",
            "XXXX X XXrXX X XXXX",
            "O       bpo       O",
            "XXXX X XXXXX X XXXX",
            "X    X   F   X    X",
            "X XX X XXXXX X XX X",
            "X  X           X  X",
            "XX X XXX X XXX X XX",
            "X      X P X      X",
            "X XXXX X X X XXXX X",
            "X*   X   X   X   *X",
            "X XX X XXXXX X XX X",
            "X                 X",
            "XXXXXXXXXXXXXXXXXXX"
        }),
        new Maze("TUNNELS", new Color(0xFF8A1F), new String[] {
            "XXXXXXXXXXXXXXXXXXX",
            "X   X    X    X   X",
            "X X X XX X XX X X X",
            "X*X             X*X",
            "X X XXX XXX XXX X X",
            "X                 X",
            "XXX X XXXXXXX X XXX",
            "O   X         X   O",
            "XXX X XXXrXXX X XXX",
            "O       bpo       O",
            "XXX X XXXXXXX X XXX",
            "O   X    F    X   O",
            "XXX X XXXXXXX X XXX",
            "X                 X",
            "X XX XX X X XX XX X",
            "X  X    XPX    X  X",
            "XX X XX X X XX X XX",
            "X*               *X",
            "X XXXX XXXXX XXXX X",
            "X                 X",
            "XXXXXXXXXXXXXXXXXXX"
        }),
        new Maze("CROSS", new Color(0x22B455), new String[] {
            "XXXXXXXXXXXXXXXXXXX",
            "X        X        X",
            "X XXXXXX X XXXXXX X",
            "X*X             X*X",
            "X X XX XXXXX XX X X",
            "X                 X",
            "XXXX X XX XX X XXXX",
            "X    X       X    X",
            "X XX X XXrXX X XX X",
            "X       bpo       X",
            "X XX X XXXXX X XX X",
            "X    X   F   X    X",
            "XXXX X XXXXX X XXXX",
            "X        X        X",
            "X XXX XX X XX XXX X",
            "X   X    P    X   X",
            "XXX X X XXX X X XXX",
            "X     X  X  X     X",
            "X*XXX XX X XX XXX*X",
            "X                 X",
            "XXXXXXXXXXXXXXXXXXX"
        }),
        new Maze("SPIRAL", new Color(0xFF5FA2), new String[] {
            "XXXXXXXXXXXXXXXXXXX",
            "X*               *X",
            "X XXXXXX X XXXXXX X",
            "X X      X      X X",
            "X X XXXX X XXXX X X",
            "X X X         X X X",
            "X   X XX X XX X   X",
            "XXX X X     X X XXX",
            "XXX X X XrX X X XXX",
            "O     X bpo X     O",
            "XXX X XXXXXXX X XXX",
            "XXX X    F    X XXX",
            "X   XXXX X XXXX   X",
            "X X      X      X X",
            "X X XXXX X XXXX X X",
            "X X X    P    X X X",
            "X X X XXX XXX X X X",
            "X   X         X   X",
            "X XXX XXXXXXX XXX X",
            "X*               *X",
            "XXXXXXXXXXXXXXXXXXX"
        }),
        new Maze("ISLANDS", new Color(0x1FC8E3), new String[] {
            "XXXXXXXXXXXXXXXXXXX",
            "X*               *X",
            "X X X X X X X X X X",
            "X                 X",
            "X X X X X X X X X X",
            "X                 X",
            "X X X X XXX X X X X",
            "X                 X",
            "XXX X X XrX X X XXX",
            "O     X bpo X     O",
            "XXX X XXXXXXX X XXX",
            "X        F        X",
            "X X X X X X X X X X",
            "X                 X",
            "X X X X X X X X X X",
            "X        P        X",
            "X X X X X X X X X X",
            "X                 X",
            "X X X X X X X X X X",
            "X*               *X",
            "XXXXXXXXXXXXXXXXXXX"
        }),
        new Maze("FORTRESS", new Color(0xE5383B), new String[] {
            "XXXXXXXXXXXXXXXXXXX",
            "X                 X",
            "X XXX XXXXXXX XXX X",
            "X X             X X",
            "X X XXXXXXXXXXX X X",
            "X X X         X X X",
            "X   X XX X XX X   X",
            "XXX X X     X X XXX",
            "XXX X X XrX X X XXX",
            "O     X bpo X     O",
            "XXX X XXXXXXX X XXX",
            "X   X    F    X   X",
            "X X XXXX X XXXX X X",
            "X X      X      X X",
            "X XXXX X X X XXXX X",
            "X      X P X      X",
            "XXX XX X X X XX XXX",
            "X*       X       *X",
            "X XXXXXX X XXXXXX X",
            "X                 X",
            "XXXXXXXXXXXXXXXXXXX"
        }),
        new Maze("ZIGZAG", new Color(0xC9B400), new String[] {
            "XXXXXXXXXXXXXXXXXXX",
            "X*       X       *X",
            "X XXXXXX X XXXXXX X",
            "O  X     O     X  O",
            "XX X XXXXXXXXX X XX",
            "X    X       X    X",
            "X XXXX XX XX XXXX X",
            "X                 X",
            "XXX X X XrX X X XXX",
            "O     X bpo X     O",
            "XXX X XXXXXXX X XXX",
            "X   X    F    X   X",
            "X XXXX X X X XXXX X",
            "O  X   X O X   X  O",
            "XX X XXX X XXX X XX",
            "X    X   P   X    X",
            "X XXXX XX XX XXXX X",
            "X      X   X      X",
            "X XXXX X X X XXXX X",
            "X*               *X",
            "XXXXXXXXXXXXXXXXXXX"
        })
    };

    /**
     * Which maze a level uses: levels 1-8 go through the mazes in order, and after that each level
     * picks one at random, never the same as the level before.
     */
    static int indexForLevel(int level, int previousIndex, Random random) {
        if (level <= ALL.length) return level - 1;
        int next = random.nextInt(ALL.length - 1);
        return next >= previousIndex ? next + 1 : next;   // skip the previous maze
    }

    private Mazes() {}
}

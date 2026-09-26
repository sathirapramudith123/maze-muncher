import java.awt.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Lets the player draw their own maze, checks that it can be played, and saves it to a text file.
 * Uses the same characters as Mazes.java, plus 'G' for the spot where all four ghosts start.
 */
final class MazeEditor {
    static final Color WALL = new Color(0x19C3B0);
    private static final Color LOCKED_WALL = new Color(0x0F6E64);
    static final char[] TOOLS = {'X', ' ', '*', 'O', 'P', 'G', 'F'};
    static final String[] TOOL_NAMES = {"WALL", "PELLET", "POWER", "EMPTY", "MUNCH", "GHOSTS", "FRUIT"};
    private static final String UNIQUE = "PGF"; // tools that place a single marker

    private final int rows;
    private final int cols;
    private final int tile;
    private final Path file;
    private final Image ghostImage;
    private final Image cherryImage;

    private char[][] grid;
    private int tool = 0;
    private boolean mirror = true;
    private String message = "";
    private boolean messageIsError = false;
    private Set<Integer> bad = new HashSet<>();
    /** frame when X was first pressed, or -1 when clearing isn't waiting for a second press */
    private long clearArmedAt = -1;
    private int hoverRow = -1;
    private int hoverCol = -1;

    MazeEditor(int rows, int cols, int tile, Path file, Image ghostImage, Image cherryImage) {
        this.rows = rows;
        this.cols = cols;
        this.tile = tile;
        this.file = file;
        this.ghostImage = ghostImage;
        this.cherryImage = cherryImage;
    }

    // ------------------------------------------------------------ saved maze

    /** @return the saved maze, or null if there is none (or it is damaged) */
    Mazes.Maze loadSaved() {
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            if (lines.size() < rows) return null;
            String[] out = new String[rows];
            for (int r = 0; r < rows; r++) {
                String line = lines.get(r);
                if (line.length() != cols || !line.matches("[XO *PGF]+")) return null;
                out[r] = line;
            }
            return new Mazes.Maze("MY MAZE", WALL, out);
        } catch (IOException e) {
            return null;
        }
    }

    boolean hasSaved() {
        return loadSaved() != null;
    }

    // ------------------------------------------------------------ editing

    void open() {
        Mazes.Maze saved = loadSaved();
        grid = saved != null ? toGrid(saved.rows) : classic();
        bad.clear();
        say(saved != null ? "Your saved maze. Click or drag on the maze to draw."
                          : "Starting from the Classic maze. Click or drag on the maze to draw.", false);
    }

    private char[][] toGrid(String[] lines) {
        char[][] g = new char[rows][];
        for (int r = 0; r < rows; r++) g[r] = lines[r].toCharArray();
        return g;
    }

    /** The Classic maze, with its four ghost starts turned into the single ghost marker. */
    private char[][] classic() {
        String[] lines = Mazes.ALL[0].rows.clone();
        for (int r = 0; r < rows; r++) {
            lines[r] = lines[r].replace('r', 'G').replaceAll("[pbo]", "O");
        }
        return toGrid(lines);
    }

    private char[][] blank() {
        char[][] g = new char[rows][cols];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                g[r][c] = (r == 0 || r == rows - 1 || c == 0 || c == cols - 1) ? 'X' : ' ';
            }
        }
        return g;
    }

    void loadClassic() {
        grid = classic();
        bad.clear();
        say("Loaded the Classic maze. Change anything you like.", false);
    }

    /** Clearing needs a second press within 3 seconds, so a stray key can't wipe the maze. */
    void clear(long frame, int framesIn3s) {
        if (clearArmedAt >= 0 && frame - clearArmedAt <= framesIn3s) {
            grid = blank();
            bad.clear();
            clearArmedAt = -1;
            say("Cleared. Draw walls, then place the Muncher and the ghosts.", false);
        } else {
            clearArmedAt = frame;
            say("Press X again to clear the whole maze.", true);
        }
    }

    void selectTool(int index) {
        if (index >= 0 && index < TOOLS.length) tool = index;
    }

    void toggleMirror() {
        mirror = !mirror;
    }

    void hover(int r, int c) {
        hoverRow = r;
        hoverCol = c;
    }

    /** Applies the current tool at a tile. @param first true on the click itself, false while dragging */
    void paint(int r, int c, boolean first) {
        if (r <= 0 || r >= rows - 1 || c < 0 || c >= cols) return; // top and bottom rows stay walls
        char t = TOOLS[tool];
        bad.clear();
        if (UNIQUE.indexOf(t) >= 0) {
            if (!first) return;
            if (c == 0 || c == cols - 1) {
                say("Put the Muncher, the ghosts and the fruit inside the maze, not on its edge.", true);
                return;
            }
            for (char[] row : grid) {
                for (int i = 0; i < cols; i++) if (row[i] == t) row[i] = ' ';
            }
            grid[r][c] = t;
            return;
        }
        grid[r][c] = t;
        int m = cols - 1 - c;
        if (mirror && m != c && UNIQUE.indexOf(grid[r][m]) < 0) grid[r][m] = t;
    }

    // ------------------------------------------------------------ checking and saving

    /** @return a list of problems; empty when the maze can be played. Unreachable tiles go into {@code bad}. */
    List<String> validate() {
        List<String> errs = new ArrayList<>();
        int pac = count('P'), ghosts = count('G'), pellets = count(' ') + count('*') + count('F');
        if (pac != 1) errs.add("Place the Muncher with the MUNCH tool (key 5).");
        if (ghosts != 1) errs.add("Place the ghosts with the GHOSTS tool (key 6).");
        if (pellets == 0) errs.add("Add at least one pellet.");
        for (int r = 0; r < rows; r++) {
            if ((grid[r][0] != 'X') != (grid[r][cols - 1] != 'X')) {
                errs.add("Row " + (r + 1) + ": a tunnel needs an opening on both the left and the right edge.");
                break;
            }
        }
        if (!errs.isEmpty()) return errs;

        boolean[][] seen = new boolean[rows][cols];
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (grid[r][c] == 'P') {
                    seen[r][c] = true;
                    queue.add(new int[] {r, c});
                }
            }
        }
        int[][] steps = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        while (!queue.isEmpty()) {
            int[] cur = queue.poll();
            for (int[] s : steps) {
                int r = cur[0] + s[0];
                int c = Math.floorMod(cur[1] + s[1], cols); // side tunnels wrap around
                if (r < 0 || r >= rows || grid[r][c] == 'X' || seen[r][c]) continue;
                seen[r][c] = true;
                queue.add(new int[] {r, c});
            }
        }
        int lost = 0;
        boolean ghostsOk = true;
        bad.clear();
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (seen[r][c]) continue;
                char ch = grid[r][c];
                if (ch == ' ' || ch == '*' || ch == 'F') {
                    lost++;
                    bad.add(r * cols + c);
                } else if (ch == 'G') {
                    ghostsOk = false;
                    bad.add(r * cols + c);
                }
            }
        }
        if (lost > 0) {
            errs.add(lost + (lost == 1 ? " pellet can't" : " pellets can't")
                + " be reached from the Muncher (marked in red). Open a path or remove them.");
        }
        if (!ghostsOk) errs.add("The ghosts are walled off from the Muncher.");
        return errs;
    }

    private int count(char ch) {
        int n = 0;
        for (char[] row : grid) for (char x : row) if (x == ch) n++;
        return n;
    }

    /** Checks and saves the maze. @return true if it was saved */
    boolean save() {
        List<String> errs = validate();
        if (!errs.isEmpty()) {
            say(errs.get(0) + (errs.size() > 1 ? " (and " + (errs.size() - 1) + " more)" : ""), true);
            return false;
        }
        List<String> lines = new ArrayList<>();
        for (char[] row : grid) lines.add(new String(row));
        try {
            Files.write(file, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            say("Couldn't save the maze: " + e.getMessage(), true);
            return false;
        }
        say("Saved. Press C on the menu to play it any time.", false);
        return true;
    }

    private void say(String text, boolean error) {
        message = text;
        messageIsError = error;
    }

    // ------------------------------------------------------------ drawing

    /** Draws the maze being edited in a cols x rows grid of tiles starting at (0, 0). */
    void drawBoard(Graphics2D g) {
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int x = c * tile;
                int y = r * tile;
                switch (grid[r][c]) {
                    case 'X':
                        g.setColor(r == 0 || r == rows - 1 ? LOCKED_WALL : WALL);
                        g.fillRoundRect(x + 2, y + 2, tile - 4, tile - 4, 8, 8);
                        break;
                    case ' ':
                        g.setColor(Color.WHITE);
                        g.fillRect(x + 14, y + 14, 4, 4);
                        break;
                    case '*':
                        g.setColor(Color.WHITE);
                        g.fillOval(x + 7, y + 7, tile - 14, tile - 14);
                        break;
                    case 'P':
                        g.setColor(Color.YELLOW);
                        g.fillArc(x + 2, y + 2, tile - 4, tile - 4, 30, 300);
                        break;
                    case 'G':
                        g.drawImage(ghostImage, x, y, tile, tile, null);
                        break;
                    case 'F':
                        g.drawImage(cherryImage, x, y, tile, tile, null);
                        break;
                    default:
                        break;
                }
                if (bad.contains(r * cols + c)) {
                    g.setColor(new Color(0xFF3B30));
                    g.setStroke(new BasicStroke(3));
                    g.drawRect(x + 3, y + 3, tile - 6, tile - 6);
                    g.setStroke(new BasicStroke(1));
                }
            }
        }
        g.setColor(new Color(255, 255, 255, 18));
        for (int c = 1; c < cols; c++) g.drawLine(c * tile, 0, c * tile, rows * tile);
        for (int r = 1; r < rows; r++) g.drawLine(0, r * tile, cols * tile, r * tile);
        if (mirror) {
            g.setColor(new Color(255, 213, 46, 90));
            g.setStroke(new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[] {6, 6}, 0));
            g.drawLine(cols * tile / 2, 0, cols * tile / 2, rows * tile);
            g.setStroke(new BasicStroke(1));
        }
        if (hoverRow > 0 && hoverRow < rows - 1 && hoverCol >= 0 && hoverCol < cols) {
            g.setColor(Color.YELLOW);
            g.setStroke(new BasicStroke(2));
            g.drawRect(hoverCol * tile + 1, hoverRow * tile + 1, tile - 2, tile - 2);
            g.setStroke(new BasicStroke(1));
        }
    }

    private int chipWidth(int width) {
        return (width - 16) / TOOLS.length;
    }

    /** Which tool chip is at this x in the toolbar, or -1. */
    int toolAt(int x, int width) {
        int i = (x - 8) / chipWidth(width);
        return x >= 8 && i >= 0 && i < TOOLS.length ? i : -1;
    }

    /** Tool chips, drawn in the strip above the maze. */
    void drawToolbar(Graphics2D g, int width, int height) {
        int w = chipWidth(width);
        for (int i = 0; i < TOOLS.length; i++) {
            int x = 8 + i * w;
            boolean on = i == tool;
            g.setColor(on ? new Color(0x181C42) : new Color(0x10132E));
            g.fillRoundRect(x + 2, 3, w - 4, height - 6, 10, 10);
            g.setColor(on ? Color.YELLOW : new Color(0x2C3270));
            g.setStroke(new BasicStroke(2));
            g.drawRoundRect(x + 2, 3, w - 4, height - 6, 10, 10);
            g.setStroke(new BasicStroke(1));
            drawToolIcon(g, TOOLS[i], x + 8, height / 2 - 7, 14);
            g.setFont(new Font("Arial", Font.BOLD, 10));
            g.setColor(on ? Color.YELLOW : Color.WHITE);
            g.drawString((i + 1) + " " + TOOL_NAMES[i], x + 26, height / 2 + 4);
        }
    }

    private void drawToolIcon(Graphics2D g, char t, int x, int y, int s) {
        switch (t) {
            case 'X': g.setColor(WALL); g.fillRoundRect(x, y, s, s, 4, 4); break;
            case ' ': g.setColor(Color.WHITE); g.fillRect(x + s / 2 - 2, y + s / 2 - 2, 4, 4); break;
            case '*': g.setColor(Color.WHITE); g.fillOval(x + 1, y + 1, s - 2, s - 2); break;
            case 'O':
                g.setColor(Color.GRAY);
                g.setStroke(new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[] {3, 2}, 0));
                g.drawRect(x, y, s - 1, s - 1);
                g.setStroke(new BasicStroke(1));
                break;
            case 'P': g.setColor(Color.YELLOW); g.fillArc(x, y, s, s, 30, 300); break;
            case 'G': g.drawImage(ghostImage, x, y, s, s, null); break;
            case 'F': g.drawImage(cherryImage, x, y, s, s, null); break;
            default: break;
        }
    }

    /** Message line and key hints, drawn in the bar below the maze. */
    void drawStatus(Graphics2D g, int y, int width, int height) {
        g.setColor(Color.BLACK);
        g.fillRect(0, y, width, height);
        g.setFont(new Font("Arial", Font.PLAIN, 12));
        g.setColor(messageIsError ? new Color(0xFF6B5E) : new Color(0x9AA0CC));
        g.drawString(message, 8, y + 13);
        g.setColor(Color.LIGHT_GRAY);
        g.drawString("1-7 tool   R mirror " + (mirror ? "ON" : "OFF") + "   S save   P save & play   C classic   X clear   Esc exit",
            8, y + 28);
    }
}

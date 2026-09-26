import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.net.URL;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import javax.swing.*;

public class PacMan extends JPanel implements ActionListener, KeyListener {

    enum Direction {
        UP(0, -1), DOWN(0, 1), LEFT(-1, 0), RIGHT(1, 0), NONE(0, 0);

        final int dx;
        final int dy;

        Direction(int dx, int dy) {
            this.dx = dx;
            this.dy = dy;
        }

        Direction opposite() {
            switch (this) {
                case UP: return DOWN;
                case DOWN: return UP;
                case LEFT: return RIGHT;
                case RIGHT: return LEFT;
                default: return NONE;
            }
        }
    }

    enum State { MENU, SCORES, READY, PLAYING, DYING, LEVEL_CLEAR, NAME_ENTRY, GAME_OVER }

    enum Difficulty {
        EASY("EASY", "5 lives, slow ghosts, 10s power-ups", new Color(0x4CD964), 5, 10000, 2, 30),
        MEDIUM("MEDIUM", "3 lives, classic ghosts, 8s power-ups", new Color(0xFFB000), 3, 8000, 4, 10),
        HARD("HARD", "3 lives, fast smart ghosts, 5s power-ups", new Color(0xFF3B30), 3, 5000, 7, 3);

        final String label;
        final String description;
        final Color color;
        final int lives;
        final int scaredFrames;
        /** ghosts skip one frame in every (ghostSkipBase + level), so a higher base means faster ghosts */
        final int ghostSkipBase;
        /** chance (in percent) that a chasing ghost picks a random turn instead of the smart one */
        final int randomTurnPercent;

        Difficulty(String label, String description, Color color, int lives, int scaredMs,
                   int ghostSkipBase, int randomTurnPercent) {
            this.label = label;
            this.description = description;
            this.color = color;
            this.lives = lives;
            this.scaredFrames = scaredMs / FRAME_MS;
            this.ghostSkipBase = ghostSkipBase;
            this.randomTurnPercent = randomTurnPercent;
        }

        Path scoresFile() {
            return Paths.get(System.getProperty("user.home"), ".pacman_scores_" + name().toLowerCase());
        }

        /** where versions before the top-5 scoreboard kept a single high score */
        Path legacyHighScoreFile() {
            return Paths.get(System.getProperty("user.home"), ".pacman_highscore_" + name().toLowerCase());
        }
    }

    /** Floating score text shown where points were earned. */
    static class Popup {
        final int x, y;
        final String text;
        int timer;

        Popup(int x, int y, String text, int timer) {
            this.x = x;
            this.y = y;
            this.text = text;
            this.timer = timer;
        }
    }

    class Entity {
        int x, y;
        int startX, startY;
        Direction direction = Direction.NONE;
        Image image;

        Entity(Image image, int x, int y) {
            this.image = image;
            this.x = this.startX = x;
            this.y = this.startY = y;
        }

        boolean atTileCenter() {
            return x % tileSize == 0 && y % tileSize == 0;
        }

        int col() {
            return Math.floorMod(Math.round((float) x / tileSize), columnCount);
        }

        int row() {
            return Math.round((float) y / tileSize);
        }

        void step(int speed) {
            x += direction.dx * speed;
            y += direction.dy * speed;
            // tunnel wrap-around; boardWidth is a multiple of tileSize so alignment is preserved
            if (x < 0) x += boardWidth;
            if (x >= boardWidth) x -= boardWidth;
        }

        void reset() {
            x = startX;
            y = startY;
            direction = Direction.NONE;
        }
    }

    class Ghost extends Entity {
        final char kind;
        final Image normalImage;
        final int scatterCol, scatterRow;
        final int baseReleaseDelay;
        int releaseDelay;
        boolean scared = false;
        /** eaten by pac man: only the eyes are left, heading back home */
        boolean eaten = false;
        /** steps from each tile to this ghost's home tile, used by the eyes to find their way back */
        int[][] homeDistance;

        Ghost(char kind, Image image, int x, int y, int scatterCol, int scatterRow, int releaseDelay) {
            super(image, x, y);
            this.kind = kind;
            this.normalImage = image;
            this.scatterCol = scatterCol;
            this.scatterRow = scatterRow;
            this.baseReleaseDelay = releaseDelay;
            this.releaseDelay = releaseDelay;
        }

        @Override
        void reset() {
            super.reset();
            scared = false;
            eaten = false;
            releaseDelay = baseReleaseDelay;
        }
    }

    private Mazes.Maze maze = Mazes.ALL[0];

    private final int rowCount = 21;
    private final int columnCount = 19;
    private final int tileSize = 32;
    private final int boardWidth = columnCount * tileSize;
    private final int boardHeight = rowCount * tileSize;
    private final int hudHeight = tileSize;
    /** strip above the maze where the power-up countdown appears */
    private final int powerBarHeight = tileSize;

    private static final int FRAME_MS = 25;
    private static final int PAC_SPEED = 4; // must divide tileSize
    private static final int GHOST_SPEED = 4; // must divide tileSize
    private static final int SCARED_WARNING_FRAMES = 2000 / FRAME_MS;
    private static final int SCATTER_FRAMES = 7000 / FRAME_MS;
    private static final int CHASE_FRAMES = 20000 / FRAME_MS;
    private static final int DYING_FRAMES = 1500 / FRAME_MS;
    private static final int LEVEL_CLEAR_FRAMES = 2000 / FRAME_MS;
    private static final int FRUIT_FRAMES = 10000 / FRAME_MS;
    private static final int POPUP_FRAMES = 1000 / FRAME_MS;
    private static final int EXTRA_LIFE_SCORE = 10000;
    private static final int EYES_STEPS_PER_FRAME = 2; // eyes travel twice as fast as pac man

    private Image wallImage;
    private Image blueGhostImage, orangeGhostImage, pinkGhostImage, redGhostImage, scaredGhostImage;
    private Image powerFoodImage, cherryImage;

    private boolean[][] walls;
    private boolean[][] food;
    private boolean[][] powerFood;
    private int foodRemaining;
    private int foodEaten;

    private Entity pacman;
    private Direction facing = Direction.RIGHT;
    private final List<Ghost> ghosts = new ArrayList<>();
    private Entity fruit;
    private Fruit fruitKind = Fruit.CHERRY;
    private int fruitTimer = 0;
    private int fruitCol, fruitRow;
    private final List<Popup> popups = new ArrayList<>();
    private final Sound sound = new Sound();

    private final Timer gameLoop;
    private final Random random = new Random();

    private State state = State.MENU;
    private Difficulty difficulty = Difficulty.MEDIUM;
    private boolean paused = false;
    private Direction queuedDirection = Direction.NONE;
    private long frame = 0;
    private int stateTimer = 0;
    private int scaredTimer = 0;
    private int ghostCombo = 0;
    private int modeTimer = 0;
    private boolean scatterMode = true;

    private int score = 0;
    private int highScore = 0;
    private final ScoreBoard[] boards = new ScoreBoard[Difficulty.values().length];
    private String playerName = "";
    private int lastRank = -1;
    private boolean extraLifeAwarded = false;
    private int lives = 3;
    private int level = 1;

    PacMan() {
        setPreferredSize(new Dimension(boardWidth, powerBarHeight + boardHeight + hudHeight));
        setBackground(Color.BLACK);
        addKeyListener(this);
        setFocusable(true);

        wallImage = loadImage("wall.png");
        blueGhostImage = loadImage("blueGhost.png");
        orangeGhostImage = loadImage("orangeGhost.png");
        pinkGhostImage = loadImage("pinkGhost.png");
        redGhostImage = loadImage("redGhost.png");
        scaredGhostImage = loadImage("scaredGhost.png");
        powerFoodImage = loadImage("powerFood.png");
        cherryImage = loadImage("cherry.png");

        for (Difficulty d : Difficulty.values()) {
            boards[d.ordinal()] = new ScoreBoard(d.scoresFile(), d.legacyHighScoreFile());
        }
        highScore = board().best();
        loadMap();

        gameLoop = new Timer(FRAME_MS, this);
        gameLoop.start();
    }

    private Image loadImage(String name) {
        URL url = getClass().getResource(name);
        if (url == null) {
            throw new IllegalStateException("Missing image resource: " + name
                + " (it must sit next to the compiled classes)");
        }
        return new ImageIcon(url).getImage();
    }

    private ScoreBoard board() {
        return boards[difficulty.ordinal()];
    }

    private void loadMap() {
        walls = new boolean[rowCount][columnCount];
        food = new boolean[rowCount][columnCount];
        powerFood = new boolean[rowCount][columnCount];
        foodRemaining = 0;
        foodEaten = 0;
        ghosts.clear();
        maze = Mazes.forLevel(level);
        fruit = null;
        fruitTimer = 0;
        popups.clear();

        for (int r = 0; r < rowCount; r++) {
            for (int c = 0; c < columnCount; c++) {
                char tile = maze.rows[r].charAt(c);
                int x = c * tileSize;
                int y = r * tileSize;
                switch (tile) {
                    case 'X': walls[r][c] = true; break;
                    case ' ': food[r][c] = true; foodRemaining++; break;
                    case 'F': food[r][c] = true; foodRemaining++; fruitCol = c; fruitRow = r; break;
                    case '*': powerFood[r][c] = true; foodRemaining++; break;
                    case 'P': pacman = new Entity(null, x, y); break;
                    case 'r': ghosts.add(new Ghost('r', redGhostImage, x, y, columnCount - 2, -2, 0)); break;
                    case 'p': ghosts.add(new Ghost('p', pinkGhostImage, x, y, 1, -2, 2000 / FRAME_MS)); break;
                    case 'b': ghosts.add(new Ghost('b', blueGhostImage, x, y, columnCount - 1, rowCount, 5000 / FRAME_MS)); break;
                    case 'o': ghosts.add(new Ghost('o', orangeGhostImage, x, y, 0, rowCount, 8000 / FRAME_MS)); break;
                    default: break;
                }
            }
        }
        for (Ghost ghost : ghosts) {
            ghost.homeDistance = distancesFrom(ghost.startX / tileSize, ghost.startY / tileSize);
        }
    }

    /** Breadth-first search over open tiles (including the side tunnels) from the given tile. */
    private int[][] distancesFrom(int startCol, int startRow) {
        int[][] dist = new int[rowCount][columnCount];
        for (int[] row : dist) {
            Arrays.fill(row, Integer.MAX_VALUE);
        }
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        dist[startRow][startCol] = 0;
        queue.add(new int[] {startCol, startRow});
        while (!queue.isEmpty()) {
            int[] cur = queue.poll();
            for (Direction d : new Direction[] {Direction.UP, Direction.LEFT, Direction.DOWN, Direction.RIGHT}) {
                int c = Math.floorMod(cur[0] + d.dx, columnCount);
                int r = cur[1] + d.dy;
                if (isWall(c, r) || dist[r][c] != Integer.MAX_VALUE) continue;
                dist[r][c] = dist[cur[1]][cur[0]] + 1;
                queue.add(new int[] {c, r});
            }
        }
        return dist;
    }

    private boolean isWall(int col, int row) {
        if (row < 0 || row >= rowCount) return true;
        return walls[row][Math.floorMod(col, columnCount)];
    }

    private void resetPositions() {
        pacman.reset();
        facing = Direction.RIGHT;
        for (Ghost ghost : ghosts) {
            ghost.reset();
        }
        queuedDirection = Direction.NONE;
        scaredTimer = 0;
        modeTimer = 0;
        scatterMode = true;
    }

    private void newGame(Difficulty chosen) {
        difficulty = chosen;
        highScore = board().best();
        paused = false;
        lastRank = -1;
        extraLifeAwarded = false;
        score = 0;
        lives = difficulty.lives;
        level = 1;
        loadMap();
        resetPositions();
        state = State.READY;
        sound.start();
    }

    // ---------------------------------------------------------------- update

    @Override
    public void actionPerformed(ActionEvent e) {
        if (!paused) {
            update();
        }
        repaint();
    }

    private void update() {
        frame++;
        popups.removeIf(p -> --p.timer <= 0);
        switch (state) {
            case PLAYING:
                updatePlaying();
                break;
            case DYING:
                if (--stateTimer <= 0) {
                    if (lives <= 0) {
                        state = board().qualifies(score) ? State.NAME_ENTRY : State.GAME_OVER;
                    } else {
                        resetPositions();
                        state = State.READY;
                    }
                }
                break;
            case LEVEL_CLEAR:
                if (--stateTimer <= 0) {
                    level++;
                    loadMap();
                    resetPositions();
                    state = State.READY;
                }
                break;
            default:
                break;
        }
    }

    private void updatePlaying() {
        updateModes();
        movePacman();
        if (checkGhostCollisions()) return;
        moveGhosts();
        if (checkGhostCollisions()) return;
        updateFruit();

        if (foodRemaining == 0) {
            state = State.LEVEL_CLEAR;
            stateTimer = LEVEL_CLEAR_FRAMES;
            sound.levelClear();
        }
    }

    private void updateModes() {
        if (scaredTimer > 0 && --scaredTimer == 0) {
            for (Ghost ghost : ghosts) {
                ghost.scared = false;
            }
        }
        // scatter/chase cycle is frozen while ghosts are frightened
        if (scaredTimer == 0 && ++modeTimer >= (scatterMode ? SCATTER_FRAMES : CHASE_FRAMES)) {
            modeTimer = 0;
            scatterMode = !scatterMode;
        }
    }

    private void movePacman() {
        if (pacman.atTileCenter()) {
            int col = pacman.col();
            int row = pacman.row();
            eatAt(col, row);

            if (queuedDirection != Direction.NONE
                    && !isWall(col + queuedDirection.dx, row + queuedDirection.dy)) {
                pacman.direction = queuedDirection;
            }
            if (isWall(col + pacman.direction.dx, row + pacman.direction.dy)) {
                pacman.direction = Direction.NONE;
            }
        } else if (queuedDirection != Direction.NONE && queuedDirection == pacman.direction.opposite()) {
            pacman.direction = queuedDirection;
        }

        pacman.step(PAC_SPEED);
        if (pacman.direction != Direction.NONE) {
            facing = pacman.direction;
        }
    }

    private void eatAt(int col, int row) {
        if (food[row][col]) {
            food[row][col] = false;
            addScore(10);
            sound.waka();
            onFoodEaten();
        } else if (powerFood[row][col]) {
            powerFood[row][col] = false;
            addScore(50);
            onFoodEaten();
            frightenGhosts();
        }
        if (fruit != null && fruit.col() == col && fruit.row() == row) {
            addScore(fruitKind.points);
            popups.add(new Popup(fruit.x, fruit.y, String.valueOf(fruitKind.points), POPUP_FRAMES));
            fruit = null;
            sound.fruit();
        }
    }

    private void addScore(int points) {
        score += points;
        highScore = Math.max(highScore, score);
        if (!extraLifeAwarded && score >= EXTRA_LIFE_SCORE) {
            extraLifeAwarded = true;
            lives++;
            sound.extraLife();
            popups.add(new Popup(pacman.x, pacman.y - tileSize / 2, "1UP!", POPUP_FRAMES * 2));
        }
    }

    private void onFoodEaten() {
        foodRemaining--;
        foodEaten++;
        if (foodEaten == 70 || foodEaten == 140) {
            fruitKind = Fruit.forLevel(level);
            fruit = new Entity(null, fruitCol * tileSize, fruitRow * tileSize);
            fruitTimer = FRUIT_FRAMES;
        }
    }

    private void updateFruit() {
        if (fruit != null && --fruitTimer <= 0) {
            fruit = null;
        }
    }

    private void frightenGhosts() {
        scaredTimer = difficulty.scaredFrames;
        ghostCombo = 0;
        sound.powerUp();
        popups.add(new Popup(pacman.x, pacman.y - tileSize / 2, "POWER UP!", POPUP_FRAMES * 3 / 2));
        for (Ghost ghost : ghosts) {
            if (ghost.eaten) continue;
            ghost.scared = true;
            ghost.direction = ghost.direction.opposite();
        }
    }

    private void moveGhosts() {
        for (Ghost ghost : ghosts) {
            if (ghost.eaten) {
                moveEyesHome(ghost);
                continue;
            }
            if (ghost.releaseDelay > 0) {
                ghost.releaseDelay--;
                continue;
            }
            // Ghosts always step GHOST_SPEED pixels; slowing them down is done by skipping frames,
            // which keeps them aligned to the tile grid.
            boolean moveThisFrame = ghost.scared
                ? frame % 2 == 0
                : frame % (difficulty.ghostSkipBase + level) != 0;
            if (!moveThisFrame) continue;

            if (ghost.atTileCenter()) {
                ghost.direction = chooseGhostDirection(ghost);
            }
            ghost.step(GHOST_SPEED);
        }
    }

    private void moveEyesHome(Ghost ghost) {
        for (int i = 0; i < EYES_STEPS_PER_FRAME; i++) {
            if (ghost.atTileCenter()) {
                int col = ghost.col();
                int row = ghost.row();
                if (ghost.homeDistance[row][col] == 0) {
                    // home again: come back to life after a short pause
                    ghost.eaten = false;
                    ghost.scared = false;
                    ghost.direction = Direction.NONE;
                    ghost.releaseDelay = 500 / FRAME_MS;
                    return;
                }
                Direction best = ghost.direction;
                int bestDist = Integer.MAX_VALUE;
                for (Direction d : new Direction[] {Direction.UP, Direction.LEFT, Direction.DOWN, Direction.RIGHT}) {
                    int c = Math.floorMod(col + d.dx, columnCount);
                    int r = row + d.dy;
                    if (isWall(c, r)) continue;
                    if (ghost.homeDistance[r][c] < bestDist) {
                        bestDist = ghost.homeDistance[r][c];
                        best = d;
                    }
                }
                ghost.direction = best;
            }
            ghost.step(GHOST_SPEED);
        }
    }

    private Direction chooseGhostDirection(Ghost ghost) {
        int col = ghost.col();
        int row = ghost.row();

        List<Direction> options = new ArrayList<>();
        for (Direction d : new Direction[] {Direction.UP, Direction.LEFT, Direction.DOWN, Direction.RIGHT}) {
            if (d != ghost.direction.opposite() && !isWall(col + d.dx, row + d.dy)) {
                options.add(d);
            }
        }
        if (options.isEmpty()) {
            return ghost.direction.opposite();
        }
        if (ghost.scared || random.nextInt(100) < difficulty.randomTurnPercent) {
            return options.get(random.nextInt(options.size()));
        }

        int[] target = ghostTarget(ghost);
        Direction best = options.get(0);
        long bestDist = Long.MAX_VALUE;
        for (Direction d : options) {
            long dx = col + d.dx - target[0];
            long dy = row + d.dy - target[1];
            long dist = dx * dx + dy * dy;
            if (dist < bestDist) {
                bestDist = dist;
                best = d;
            }
        }
        return best;
    }

    private int[] ghostTarget(Ghost ghost) {
        if (scatterMode) {
            return new int[] {ghost.scatterCol, ghost.scatterRow};
        }
        int pc = pacman.col();
        int pr = pacman.row();
        Direction pd = pacman.direction;
        switch (ghost.kind) {
            case 'p': // pinky ambushes four tiles ahead of pac man
                return new int[] {pc + 4 * pd.dx, pr + 4 * pd.dy};
            case 'b': { // inky flanks using blinky's position
                Ghost red = ghosts.stream().filter(g -> g.kind == 'r').findFirst().orElse(ghost);
                int ac = pc + 2 * pd.dx;
                int ar = pr + 2 * pd.dy;
                return new int[] {2 * ac - red.col(), 2 * ar - red.row()};
            }
            case 'o': { // clyde chases until close, then retreats to his corner
                int dx = ghost.col() - pc;
                int dy = ghost.row() - pr;
                if (dx * dx + dy * dy > 64) {
                    return new int[] {pc, pr};
                }
                return new int[] {ghost.scatterCol, ghost.scatterRow};
            }
            default: // blinky chases directly
                return new int[] {pc, pr};
        }
    }

    /** @return true if pac man died this frame */
    private boolean checkGhostCollisions() {
        for (Ghost ghost : ghosts) {
            if (ghost.eaten) continue;
            int dx = Math.abs(ghost.x - pacman.x);
            dx = Math.min(dx, boardWidth - dx);
            int dy = Math.abs(ghost.y - pacman.y);
            if (dx >= tileSize / 2 || dy >= tileSize / 2) continue;

            if (ghost.scared) {
                ghostCombo++;
                int points = 100 * (1 << ghostCombo); // 200, 400, 800, 1600
                addScore(points);
                popups.add(new Popup(ghost.x, ghost.y, String.valueOf(points), POPUP_FRAMES));
                sound.eatGhost();
                ghost.eaten = true;
                ghost.scared = false;
            } else {
                lives--;
                state = State.DYING;
                stateTimer = DYING_FRAMES;
                sound.death();
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- drawing

    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        if (scaredTimer > 0 && (state == State.PLAYING || paused)) {
            drawScaredTimerBar(g2);
        }
        // everything else is drawn below the power-up strip
        g2.translate(0, powerBarHeight);
        draw(g2);
    }

    private void draw(Graphics2D g) {
        for (int r = 0; r < rowCount; r++) {
            for (int c = 0; c < columnCount; c++) {
                int x = c * tileSize;
                int y = r * tileSize;
                if (walls[r][c]) {
                    if (maze.wallColor == null) {
                        g.drawImage(wallImage, x, y, tileSize, tileSize, null);
                    } else {
                        g.setColor(maze.wallColor);
                        g.fillRoundRect(x + 2, y + 2, tileSize - 4, tileSize - 4, 8, 8);
                    }
                } else if (food[r][c]) {
                    g.setColor(Color.WHITE);
                    g.fillRect(x + 14, y + 14, 4, 4);
                } else if (powerFood[r][c] && (state != State.PLAYING || (frame / 8) % 2 == 0)) {
                    g.drawImage(powerFoodImage, x, y, tileSize, tileSize, null);
                }
            }
        }

        if (fruit != null) {
            fruitKind.draw(g, fruit.x, fruit.y, tileSize, cherryImage);
        }


        boolean gameEnded = state == State.GAME_OVER || state == State.NAME_ENTRY;
        if (state != State.LEVEL_CLEAR && state != State.DYING && !gameEnded) {
            for (Ghost ghost : ghosts) {
                if (ghost.eaten) {
                    drawEyes(g, ghost.x, ghost.y, ghost.direction);
                    if (ghost.x > boardWidth - tileSize) {
                        drawEyes(g, ghost.x - boardWidth, ghost.y, ghost.direction);
                    }
                    continue;
                }
                Image img = ghost.normalImage;
                if (ghost.scared) {
                    boolean warning = scaredTimer < SCARED_WARNING_FRAMES && (frame / 6) % 2 == 0;
                    img = warning ? ghost.normalImage : scaredGhostImage;
                }
                drawEntity(g, ghost, img);
            }
        }

        if (!gameEnded) {
            drawPacman(g, pacman.x, pacman.y, tileSize, facing, pacmanMouthDegrees());
            // second copy while passing through the side tunnel
            if (pacman.x > boardWidth - tileSize) {
                drawPacman(g, pacman.x - boardWidth, pacman.y, tileSize, facing, pacmanMouthDegrees());
            }
        }

        g.setFont(new Font("Arial", Font.BOLD, 14));
        g.setColor(Color.CYAN);
        for (Popup p : popups) {
            int w = g.getFontMetrics().stringWidth(p.text);
            int rise = (POPUP_FRAMES - p.timer) / 2;
            g.drawString(p.text, p.x + (tileSize - w) / 2, p.y + tileSize / 2 - rise);
        }

        if (state != State.MENU && state != State.SCORES) {
            drawHud(g);
        }

        switch (state) {
            case MENU: drawMenu(g); break;
            case SCORES: drawAllScores(g); break;
            case NAME_ENTRY: drawNameEntry(g); break;
            case GAME_OVER: drawGameOver(g); break;
            case READY: drawBanner(g, "READY!", "Arrow keys / WASD to start  -  M: sound " + (sound.isEnabled() ? "on" : "off"), Color.YELLOW); break;
            case LEVEL_CLEAR: drawBanner(g, "LEVEL " + level + " CLEAR!", null, Color.CYAN); break;
            default: break;
        }
        if (paused) {
            drawBanner(g, "PAUSED", "P: resume  -  Q: quit to menu", Color.WHITE);
        }
    }

    private void drawEyes(Graphics2D g, int x, int y, Direction dir) {
        for (int ex : new int[] {x + 7, x + 17}) {
            g.setColor(Color.WHITE);
            g.fillOval(ex, y + 8, 9, 12);
            g.setColor(new Color(0x2121DE));
            g.fillOval(ex + 2 + dir.dx * 2, y + 12 + dir.dy * 3, 5, 5);
        }
    }

    /** Labelled bar in the strip above the maze counting down how long the ghosts stay frightened. */
    private void drawScaredTimerBar(Graphics2D g) {
        int trackX = 8;
        int trackW = boardWidth - 16;
        int y = 4;
        int h = powerBarHeight - 8;
        boolean ending = scaredTimer < SCARED_WARNING_FRAMES;
        boolean flash = ending && (frame / 5) % 2 == 0;
        Color fill = !ending ? new Color(0x3355FF) : flash ? new Color(0xFF3B30) : new Color(0xFFD52E);

        g.setColor(new Color(0, 0, 0, 225));
        g.fillRoundRect(trackX, y, trackW, h, h, h);
        g.setColor(fill);
        g.fillRoundRect(trackX + 3, y + 3, (trackW - 6) * scaredTimer / difficulty.scaredFrames, h - 6, h - 6, h - 6);
        g.setStroke(new BasicStroke(2));
        g.drawRoundRect(trackX, y, trackW, h, h, h);
        g.setStroke(new BasicStroke(1));

        int seconds = (int) Math.ceil(scaredTimer * FRAME_MS / 1000.0);
        String label = "POWER  " + seconds + "s";
        g.setFont(new Font("Arial", Font.BOLD, 15));
        int textY = y + h / 2 + 5;
        g.setColor(Color.BLACK);
        g.drawString(label, trackX + 13, textY + 1);
        g.setColor(Color.WHITE);
        g.drawString(label, trackX + 12, textY);
    }

    /** Half-angle of pac man's mouth: chomps while moving, opens up and vanishes when he dies. */
    private int pacmanMouthDegrees() {
        if (state == State.DYING) {
            int elapsed = DYING_FRAMES - stateTimer;
            return Math.min(180, 30 + elapsed * 150 / (DYING_FRAMES * 2 / 3));
        }
        if (state != State.PLAYING || pacman.direction == Direction.NONE) {
            return 30;
        }
        int phase = Math.abs((int) (frame % 8) - 4); // 4,3,2,1,0,1,2,3 - one chomp per tile
        return 4 + phase * 11;
    }

    private void drawPacman(Graphics2D g, int x, int y, int size, Direction dir, int mouth) {
        if (mouth >= 180) return;
        int angle;
        switch (dir) {
            case UP: angle = 90; break;
            case LEFT: angle = 180; break;
            case DOWN: angle = 270; break;
            default: angle = 0; break;
        }
        int inset = size / 16;
        g.setColor(Color.YELLOW);
        g.fillArc(x + inset, y + inset, size - 2 * inset, size - 2 * inset, angle + mouth, 360 - 2 * mouth);
    }

    private void drawEntity(Graphics2D g, Entity e, Image img) {
        g.drawImage(img, e.x, e.y, tileSize, tileSize, null);
        // draw a second copy while passing through the side tunnel
        if (e.x > boardWidth - tileSize) {
            g.drawImage(img, e.x - boardWidth, e.y, tileSize, tileSize, null);
        }
    }

    private void drawHud(Graphics2D g) {
        int y = boardHeight;
        g.setColor(Color.BLACK);
        g.fillRect(0, y, boardWidth, hudHeight);

        g.setFont(new Font("Arial", Font.BOLD, 18));
        g.setColor(Color.WHITE);
        g.drawString("SCORE " + score, 8, y + 22);

        String hi = "HI " + highScore;
        int hiWidth = g.getFontMetrics().stringWidth(hi);
        g.drawString(hi, (boardWidth - hiWidth) / 2 - 30, y + 22);

        g.drawString("LV " + level, boardWidth / 2 + 50, y + 22);

        if (state != State.MENU) {
            g.setFont(new Font("Arial", Font.BOLD, 14));
            g.setColor(difficulty.color);
            g.drawString(difficulty.label, boardWidth / 2 + 96, y + 21);
        }

        int iconSize = tileSize * 2 / 3;
        if (lives <= 5) {
            for (int i = 0; i < lives; i++) {
                int x = boardWidth - (i + 1) * (iconSize + 4) - 4;
                drawPacman(g, x, y + (hudHeight - iconSize) / 2, iconSize, Direction.RIGHT, 30);
            }
        } else {
            // too many to fit: one icon and a count
            g.setFont(new Font("Arial", Font.BOLD, 18));
            g.setColor(Color.WHITE);
            String count = "x" + lives;
            int textW = g.getFontMetrics().stringWidth(count);
            g.drawString(count, boardWidth - textW - 8, y + 22);
            drawPacman(g, boardWidth - textW - 12 - iconSize, y + (hudHeight - iconSize) / 2, iconSize, Direction.RIGHT, 30);
        }
    }

    private void drawMenu(Graphics2D g) {
        g.setColor(new Color(0, 0, 0, 215));
        g.fillRect(0, 0, boardWidth, boardHeight);

        g.setFont(new Font("Arial", Font.BOLD, 56));
        FontMetrics fm = g.getFontMetrics();
        g.setColor(Color.YELLOW);
        String title = "PAC-MAN";
        g.drawString(title, (boardWidth - fm.stringWidth(title)) / 2, 130);

        g.setFont(new Font("Arial", Font.PLAIN, 18));
        fm = g.getFontMetrics();
        g.setColor(Color.WHITE);
        String prompt = "Select difficulty";
        g.drawString(prompt, (boardWidth - fm.stringWidth(prompt)) / 2, 190);

        Difficulty[] options = Difficulty.values();
        for (int i = 0; i < options.length; i++) {
            Difficulty d = options[i];
            int top = 230 + i * 100;
            boolean selected = d == difficulty;

            if (selected) {
                g.setColor(new Color(d.color.getRed(), d.color.getGreen(), d.color.getBlue(), 50));
                g.fillRoundRect(80, top, boardWidth - 160, 80, 16, 16);
                g.setColor(d.color);
                g.setStroke(new BasicStroke(3));
                g.drawRoundRect(80, top, boardWidth - 160, 80, 16, 16);
                g.setStroke(new BasicStroke(1));
                drawPacman(g, 96, top + 24, tileSize, Direction.RIGHT, 20 + (int) Math.abs(frame % 16 - 8) * 3);
            }

            g.setFont(new Font("Arial", Font.BOLD, 26));
            g.setColor(selected ? d.color : Color.GRAY);
            g.drawString((i + 1) + ". " + d.label, 144, top + 34);

            g.setFont(new Font("Arial", Font.PLAIN, 14));
            g.setColor(selected ? Color.WHITE : Color.GRAY);
            g.drawString(d.description, 144, top + 58);

            String best = "HI " + boards[d.ordinal()].best();
            fm = g.getFontMetrics();
            g.drawString(best, boardWidth - 96 - fm.stringWidth(best), top + 34);
        }

        g.setFont(new Font("Arial", Font.PLAIN, 15));
        fm = g.getFontMetrics();
        g.setColor(Color.LIGHT_GRAY);
        String help = "Up/Down or 1/2/3 to choose  -  Enter to start  -  M: sound " + (sound.isEnabled() ? "on" : "off");
        g.drawString(help, (boardWidth - fm.stringWidth(help)) / 2, 570);
        String help2 = "H: top 5 high scores";
        g.drawString(help2, (boardWidth - fm.stringWidth(help2)) / 2, 596);
    }

    private void drawCentered(Graphics2D g, String text, int y) {
        g.drawString(text, (boardWidth - g.getFontMetrics().stringWidth(text)) / 2, y);
    }

    /** Draws one difficulty's top 5 with the given row highlighted. @return the y below the table */
    private int drawScoreTable(Graphics2D g, Difficulty d, int top, int highlight) {
        g.setFont(new Font("Arial", Font.BOLD, 20));
        g.setColor(d.color);
        drawCentered(g, d.label, top);

        List<ScoreBoard.Entry> entries = boards[d.ordinal()].entries();
        g.setFont(new Font("Monospaced", Font.BOLD, 18));
        int left = boardWidth / 2 - 130;
        int right = boardWidth / 2 + 130;
        for (int i = 0; i < ScoreBoard.SIZE; i++) {
            int y = top + 28 + i * 24;
            boolean hl = i == highlight;
            if (hl) {
                g.setColor(new Color(255, 255, 0, 60));
                g.fillRect(left - 10, y - 18, right - left + 20, 24);
            }
            g.setColor(hl ? Color.YELLOW : Color.WHITE);
            String rank = (i + 1) + ".";
            if (i < entries.size()) {
                ScoreBoard.Entry e = entries.get(i);
                g.drawString(rank + " " + e.name, left, y);
                String sc = String.valueOf(e.score);
                g.drawString(sc, right - g.getFontMetrics().stringWidth(sc), y);
            } else {
                g.setColor(Color.DARK_GRAY);
                g.drawString(rank + " ----------", left, y);
            }
        }
        return top + 28 + ScoreBoard.SIZE * 24;
    }

    private void dimBoard(Graphics2D g) {
        g.setColor(new Color(0, 0, 0, 215));
        g.fillRect(0, 0, boardWidth, boardHeight + hudHeight);
    }

    private void drawAllScores(Graphics2D g) {
        dimBoard(g);
        g.setFont(new Font("Arial", Font.BOLD, 36));
        g.setColor(Color.YELLOW);
        drawCentered(g, "TOP 5 HIGH SCORES", 70);
        int y = 120;
        for (Difficulty d : Difficulty.values()) {
            y = drawScoreTable(g, d, y, -1) + 40;
        }
        g.setFont(new Font("Arial", Font.PLAIN, 15));
        g.setColor(Color.LIGHT_GRAY);
        drawCentered(g, "Press any key to go back", boardHeight + 10);
    }

    private void drawNameEntry(Graphics2D g) {
        dimBoard(g);
        g.setFont(new Font("Arial", Font.BOLD, 40));
        g.setColor(Color.YELLOW);
        drawCentered(g, "NEW HIGH SCORE!", 170);

        g.setFont(new Font("Arial", Font.BOLD, 26));
        g.setColor(Color.WHITE);
        drawCentered(g, score + "  (" + difficulty.label + ")", 220);

        g.setFont(new Font("Arial", Font.PLAIN, 18));
        drawCentered(g, "Type your name:", 300);

        int boxW = 320;
        int boxX = (boardWidth - boxW) / 2;
        g.setColor(new Color(40, 40, 60));
        g.fillRoundRect(boxX, 320, boxW, 56, 12, 12);
        g.setColor(Color.YELLOW);
        g.setStroke(new BasicStroke(2));
        g.drawRoundRect(boxX, 320, boxW, 56, 12, 12);
        g.setStroke(new BasicStroke(1));

        g.setFont(new Font("Monospaced", Font.BOLD, 30));
        String shown = playerName + ((frame / 12) % 2 == 0 ? "_" : " ");
        drawCentered(g, shown, 358);

        g.setFont(new Font("Arial", Font.PLAIN, 15));
        g.setColor(Color.LIGHT_GRAY);
        drawCentered(g, "Letters / numbers, Backspace to erase, Enter to save", 420);
    }

    private void drawGameOver(Graphics2D g) {
        dimBoard(g);
        g.setFont(new Font("Arial", Font.BOLD, 48));
        g.setColor(Color.RED);
        drawCentered(g, "GAME OVER", 130);

        g.setFont(new Font("Arial", Font.BOLD, 24));
        g.setColor(Color.WHITE);
        drawCentered(g, "SCORE " + score + "   -   LEVEL " + level, 180);

        g.setFont(new Font("Arial", Font.PLAIN, 16));
        g.setColor(Color.LIGHT_GRAY);
        drawCentered(g, "Top 5", 240);
        drawScoreTable(g, difficulty, 275, lastRank);

        g.setFont(new Font("Arial", Font.PLAIN, 16));
        g.setColor(Color.LIGHT_GRAY);
        drawCentered(g, "Press Enter to go back to the menu", 500);
    }

    private void drawBanner(Graphics2D g, String title, String subtitle, Color color) {
        int centerY = 13 * tileSize; // the open row just below the ghost area
        g.setColor(new Color(0, 0, 0, 190));
        g.fillRect(0, centerY - 44, boardWidth, subtitle == null ? 60 : 84);

        g.setFont(new Font("Arial", Font.BOLD, 32));
        FontMetrics fm = g.getFontMetrics();
        g.setColor(color);
        g.drawString(title, (boardWidth - fm.stringWidth(title)) / 2, centerY);

        if (subtitle != null) {
            g.setFont(new Font("Arial", Font.PLAIN, 16));
            fm = g.getFontMetrics();
            g.setColor(Color.WHITE);
            g.drawString(subtitle, (boardWidth - fm.stringWidth(subtitle)) / 2, centerY + 28);
        }
    }

    // ---------------------------------------------------------------- input

    @Override
    public void keyTyped(KeyEvent e) {}

    @Override
    public void keyReleased(KeyEvent e) {}

    @Override
    public void keyPressed(KeyEvent e) {
        int key = e.getKeyCode();

        if (state == State.NAME_ENTRY) {
            handleNameKey(e);
            return;
        }
        if (state == State.SCORES) {
            state = State.MENU;
            return;
        }
        if (key == KeyEvent.VK_M) {
            sound.toggle();
            return;
        }
        if (state == State.MENU) {
            handleMenuKey(key);
            return;
        }
        if (state == State.GAME_OVER) {
            if (key == KeyEvent.VK_ENTER || key == KeyEvent.VK_SPACE) {
                state = State.MENU;
            }
            return;
        }
        if (paused && key == KeyEvent.VK_Q) {
            paused = false;
            state = State.MENU;
            return;
        }
        if (state == State.READY && key == KeyEvent.VK_ESCAPE) {
            state = State.MENU;
            return;
        }
        if (key == KeyEvent.VK_P || key == KeyEvent.VK_ESCAPE) {
            if (state == State.PLAYING) paused = !paused;
            return;
        }

        Direction d = directionForKey(key);
        if (d == Direction.NONE) return;
        queuedDirection = d;
        if (state == State.READY) {
            state = State.PLAYING;
        }
    }

    private void handleMenuKey(int key) {
        Difficulty[] options = Difficulty.values();
        switch (key) {
            case KeyEvent.VK_UP: case KeyEvent.VK_W:
                difficulty = options[Math.floorMod(difficulty.ordinal() - 1, options.length)];
                break;
            case KeyEvent.VK_DOWN: case KeyEvent.VK_S:
                difficulty = options[(difficulty.ordinal() + 1) % options.length];
                break;
            case KeyEvent.VK_1: case KeyEvent.VK_NUMPAD1: newGame(Difficulty.EASY); break;
            case KeyEvent.VK_2: case KeyEvent.VK_NUMPAD2: newGame(Difficulty.MEDIUM); break;
            case KeyEvent.VK_3: case KeyEvent.VK_NUMPAD3: newGame(Difficulty.HARD); break;
            case KeyEvent.VK_ENTER: case KeyEvent.VK_SPACE: newGame(difficulty); break;
            case KeyEvent.VK_H: state = State.SCORES; break;
            default: break;
        }
    }

    private void handleNameKey(KeyEvent e) {
        int key = e.getKeyCode();
        char ch = Character.toUpperCase(e.getKeyChar());
        if (key == KeyEvent.VK_ENTER) {
            String name = playerName.trim();
            if (name.isEmpty()) name = "PLAYER";
            playerName = name; // remembered as the default for the next high score
            lastRank = board().add(name, score);
            state = State.GAME_OVER;
        } else if (key == KeyEvent.VK_BACK_SPACE) {
            if (!playerName.isEmpty()) {
                playerName = playerName.substring(0, playerName.length() - 1);
            }
        } else if (playerName.length() < ScoreBoard.MAX_NAME_LENGTH
                && ((ch >= 'A' && ch <= 'Z') || (ch >= '0' && ch <= '9') || ch == ' ')) {
            playerName += ch;
        }
    }

    private Direction directionForKey(int key) {
        switch (key) {
            case KeyEvent.VK_UP: case KeyEvent.VK_W: return Direction.UP;
            case KeyEvent.VK_DOWN: case KeyEvent.VK_S: return Direction.DOWN;
            case KeyEvent.VK_LEFT: case KeyEvent.VK_A: return Direction.LEFT;
            case KeyEvent.VK_RIGHT: case KeyEvent.VK_D: return Direction.RIGHT;
            default: return Direction.NONE;
        }
    }
}

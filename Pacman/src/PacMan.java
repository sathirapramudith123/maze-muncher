import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.net.URL;
import java.util.ArrayList;
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

    enum State { READY, PLAYING, DYING, LEVEL_CLEAR, GAME_OVER }

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
            releaseDelay = baseReleaseDelay;
        }
    }

    // X = wall, O = empty (no food), P = pac man, * = power food, ' ' = food
    // b = blue ghost, o = orange ghost, p = pink ghost, r = red ghost
    private final String[] tileMap = {
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
        "OOOX X       X XOOO",
        "XXXX X XXXXX X XXXX",
        "X        X        X",
        "X XX XXX X XXX XX X",
        "X  X     P     X  X",
        "XX X X XXXXX X X XX",
        "X    X   X   X    X",
        "X XXXXXX X XXXXXX X",
        "X*               *X",
        "XXXXXXXXXXXXXXXXXXX"
    };

    private final int rowCount = 21;
    private final int columnCount = 19;
    private final int tileSize = 32;
    private final int boardWidth = columnCount * tileSize;
    private final int boardHeight = rowCount * tileSize;
    private final int hudHeight = tileSize;

    private static final int FRAME_MS = 25;
    private static final int PAC_SPEED = 4; // must divide tileSize
    private static final int GHOST_SPEED = 4; // must divide tileSize
    private static final int SCARED_FRAMES = 8000 / FRAME_MS;
    private static final int SCARED_WARNING_FRAMES = 2000 / FRAME_MS;
    private static final int SCATTER_FRAMES = 7000 / FRAME_MS;
    private static final int CHASE_FRAMES = 20000 / FRAME_MS;
    private static final int DYING_FRAMES = 1500 / FRAME_MS;
    private static final int LEVEL_CLEAR_FRAMES = 2000 / FRAME_MS;
    private static final int CHERRY_FRAMES = 10000 / FRAME_MS;
    private static final int POPUP_FRAMES = 1000 / FRAME_MS;
    private static final Path HIGH_SCORE_FILE = Paths.get(System.getProperty("user.home"), ".pacman_highscore");

    private Image wallImage;
    private Image blueGhostImage, orangeGhostImage, pinkGhostImage, redGhostImage, scaredGhostImage;
    private Image pacmanUpImage, pacmanDownImage, pacmanLeftImage, pacmanRightImage;
    private Image powerFoodImage, cherryImage, cherry2Image;

    private boolean[][] walls;
    private boolean[][] food;
    private boolean[][] powerFood;
    private int foodRemaining;
    private int foodEaten;

    private Entity pacman;
    private final List<Ghost> ghosts = new ArrayList<>();
    private Entity cherry;
    private int cherryTimer = 0;
    private final List<Popup> popups = new ArrayList<>();
    private final Sound sound = new Sound();

    private final Timer gameLoop;
    private final Random random = new Random();

    private State state = State.READY;
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
    private int savedHighScore = 0;
    private int lives = 3;
    private int level = 1;

    PacMan() {
        setPreferredSize(new Dimension(boardWidth, boardHeight + hudHeight));
        setBackground(Color.BLACK);
        addKeyListener(this);
        setFocusable(true);

        wallImage = loadImage("wall.png");
        blueGhostImage = loadImage("blueGhost.png");
        orangeGhostImage = loadImage("orangeGhost.png");
        pinkGhostImage = loadImage("pinkGhost.png");
        redGhostImage = loadImage("redGhost.png");
        scaredGhostImage = loadImage("scaredGhost.png");
        pacmanUpImage = loadImage("pacmanUp.png");
        pacmanDownImage = loadImage("pacmanDown.png");
        pacmanLeftImage = loadImage("pacmanLeft.png");
        pacmanRightImage = loadImage("pacmanRight.png");
        powerFoodImage = loadImage("powerFood.png");
        cherryImage = loadImage("cherry.png");
        cherry2Image = loadImage("cherry2.png");

        highScore = savedHighScore = loadHighScore();
        loadMap();
        sound.start();

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

    private int loadHighScore() {
        try {
            return Integer.parseInt(new String(Files.readAllBytes(HIGH_SCORE_FILE), StandardCharsets.UTF_8).trim());
        } catch (IOException | NumberFormatException e) {
            return 0;
        }
    }

    private void saveHighScore() {
        if (highScore <= savedHighScore) return;
        try {
            Files.write(HIGH_SCORE_FILE, String.valueOf(highScore).getBytes(StandardCharsets.UTF_8));
            savedHighScore = highScore;
        } catch (IOException e) {
            // not fatal: the high score just won't survive a restart
        }
    }

    private void loadMap() {
        walls = new boolean[rowCount][columnCount];
        food = new boolean[rowCount][columnCount];
        powerFood = new boolean[rowCount][columnCount];
        foodRemaining = 0;
        foodEaten = 0;
        ghosts.clear();
        cherry = null;
        cherryTimer = 0;
        popups.clear();

        for (int r = 0; r < rowCount; r++) {
            for (int c = 0; c < columnCount; c++) {
                char tile = tileMap[r].charAt(c);
                int x = c * tileSize;
                int y = r * tileSize;
                switch (tile) {
                    case 'X': walls[r][c] = true; break;
                    case ' ': food[r][c] = true; foodRemaining++; break;
                    case '*': powerFood[r][c] = true; foodRemaining++; break;
                    case 'P': pacman = new Entity(pacmanRightImage, x, y); break;
                    case 'r': ghosts.add(new Ghost('r', redGhostImage, x, y, columnCount - 2, -2, 0)); break;
                    case 'p': ghosts.add(new Ghost('p', pinkGhostImage, x, y, 1, -2, 2000 / FRAME_MS)); break;
                    case 'b': ghosts.add(new Ghost('b', blueGhostImage, x, y, columnCount - 1, rowCount, 5000 / FRAME_MS)); break;
                    case 'o': ghosts.add(new Ghost('o', orangeGhostImage, x, y, 0, rowCount, 8000 / FRAME_MS)); break;
                    default: break;
                }
            }
        }
    }

    private boolean isWall(int col, int row) {
        if (row < 0 || row >= rowCount) return true;
        return walls[row][Math.floorMod(col, columnCount)];
    }

    private void resetPositions() {
        pacman.reset();
        pacman.image = pacmanRightImage;
        for (Ghost ghost : ghosts) {
            ghost.reset();
        }
        queuedDirection = Direction.NONE;
        scaredTimer = 0;
        modeTimer = 0;
        scatterMode = true;
    }

    private void newGame() {
        score = 0;
        lives = 3;
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
                        state = State.GAME_OVER;
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
        updateCherry();

        if (foodRemaining == 0) {
            state = State.LEVEL_CLEAR;
            stateTimer = LEVEL_CLEAR_FRAMES;
            saveHighScore();
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
        updatePacmanImage();
    }

    private void updatePacmanImage() {
        switch (pacman.direction) {
            case UP: pacman.image = pacmanUpImage; break;
            case DOWN: pacman.image = pacmanDownImage; break;
            case LEFT: pacman.image = pacmanLeftImage; break;
            case RIGHT: pacman.image = pacmanRightImage; break;
            default: break;
        }
    }

    private void eatAt(int col, int row) {
        if (food[row][col]) {
            food[row][col] = false;
            score += 10;
            sound.waka();
            onFoodEaten();
        } else if (powerFood[row][col]) {
            powerFood[row][col] = false;
            score += 50;
            onFoodEaten();
            frightenGhosts();
        }
        if (cherry != null && cherry.col() == col && cherry.row() == row) {
            int points = 100 * level;
            score += points;
            popups.add(new Popup(cherry.x, cherry.y, String.valueOf(points), POPUP_FRAMES));
            cherry = null;
            sound.fruit();
        }
        highScore = Math.max(highScore, score);
    }

    private void onFoodEaten() {
        foodRemaining--;
        foodEaten++;
        if (foodEaten == 70 || foodEaten == 140) {
            cherry = new Entity(level % 2 == 1 ? cherryImage : cherry2Image, 9 * tileSize, 11 * tileSize);
            cherryTimer = CHERRY_FRAMES;
        }
    }

    private void updateCherry() {
        if (cherry != null && --cherryTimer <= 0) {
            cherry = null;
        }
    }

    private void frightenGhosts() {
        scaredTimer = SCARED_FRAMES;
        ghostCombo = 0;
        sound.powerUp();
        for (Ghost ghost : ghosts) {
            ghost.scared = true;
            ghost.direction = ghost.direction.opposite();
        }
    }

    private void moveGhosts() {
        for (Ghost ghost : ghosts) {
            if (ghost.releaseDelay > 0) {
                ghost.releaseDelay--;
                continue;
            }
            // Ghosts always step GHOST_SPEED pixels; slowing them down is done by skipping frames,
            // which keeps them aligned to the tile grid.
            boolean moveThisFrame = ghost.scared
                ? frame % 2 == 0
                : frame % (4 + level) != 0;
            if (!moveThisFrame) continue;

            if (ghost.atTileCenter()) {
                ghost.direction = chooseGhostDirection(ghost);
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
        if (ghost.scared || random.nextInt(10) == 0) {
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
            int dx = Math.abs(ghost.x - pacman.x);
            dx = Math.min(dx, boardWidth - dx);
            int dy = Math.abs(ghost.y - pacman.y);
            if (dx >= tileSize / 2 || dy >= tileSize / 2) continue;

            if (ghost.scared) {
                ghostCombo++;
                int points = 100 * (1 << ghostCombo); // 200, 400, 800, 1600
                score += points;
                highScore = Math.max(highScore, score);
                popups.add(new Popup(ghost.x, ghost.y, String.valueOf(points), POPUP_FRAMES));
                sound.eatGhost();
                ghost.reset();
                ghost.releaseDelay = 1000 / FRAME_MS;
            } else {
                lives--;
                state = State.DYING;
                stateTimer = DYING_FRAMES;
                saveHighScore();
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
        draw(g2);
    }

    private void draw(Graphics2D g) {
        for (int r = 0; r < rowCount; r++) {
            for (int c = 0; c < columnCount; c++) {
                int x = c * tileSize;
                int y = r * tileSize;
                if (walls[r][c]) {
                    g.drawImage(wallImage, x, y, tileSize, tileSize, null);
                } else if (food[r][c]) {
                    g.setColor(Color.WHITE);
                    g.fillRect(x + 14, y + 14, 4, 4);
                } else if (powerFood[r][c] && (state != State.PLAYING || (frame / 8) % 2 == 0)) {
                    g.drawImage(powerFoodImage, x, y, tileSize, tileSize, null);
                }
            }
        }

        if (cherry != null) {
            drawEntity(g, cherry, cherry.image);
        }

        boolean levelClearFlash = state == State.LEVEL_CLEAR;
        if (!levelClearFlash && state != State.GAME_OVER) {
            for (Ghost ghost : ghosts) {
                Image img = ghost.normalImage;
                if (ghost.scared) {
                    boolean warning = scaredTimer < SCARED_WARNING_FRAMES && (frame / 6) % 2 == 0;
                    img = warning ? ghost.normalImage : scaredGhostImage;
                }
                drawEntity(g, ghost, img);
            }
        }

        if (state != State.DYING || (frame / 4) % 2 == 0) {
            drawEntity(g, pacman, pacman.image);
        }

        g.setFont(new Font("Arial", Font.BOLD, 14));
        g.setColor(Color.CYAN);
        for (Popup p : popups) {
            int w = g.getFontMetrics().stringWidth(p.text);
            int rise = (POPUP_FRAMES - p.timer) / 2;
            g.drawString(p.text, p.x + (tileSize - w) / 2, p.y + tileSize / 2 - rise);
        }

        drawHud(g);

        switch (state) {
            case READY: drawBanner(g, "READY!", "Arrow keys / WASD to start  -  M: sound " + (sound.isEnabled() ? "on" : "off"), Color.YELLOW); break;
            case LEVEL_CLEAR: drawBanner(g, "LEVEL " + level + " CLEAR!", null, Color.CYAN); break;
            case GAME_OVER: drawBanner(g, "GAME OVER", "Press Enter to play again", Color.RED); break;
            default: break;
        }
        if (paused) {
            drawBanner(g, "PAUSED", "Press P to resume", Color.WHITE);
        }
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

        int iconSize = tileSize * 2 / 3;
        for (int i = 0; i < lives; i++) {
            int x = boardWidth - (i + 1) * (iconSize + 4) - 4;
            g.drawImage(pacmanRightImage, x, y + (hudHeight - iconSize) / 2, iconSize, iconSize, null);
        }
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

        if (key == KeyEvent.VK_M) {
            sound.toggle();
            return;
        }
        if (state == State.GAME_OVER) {
            if (key == KeyEvent.VK_ENTER || key == KeyEvent.VK_SPACE) {
                newGame();
            }
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

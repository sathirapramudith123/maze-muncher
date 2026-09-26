import java.awt.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Twelve badges to unlock, saved to a text file, with an "unlocked" banner shown one at a time. */
final class Achievements {

    static final class Badge {
        final String id;
        final String name;
        final String description;

        Badge(String id, String name, String description) {
            this.id = id;
            this.name = name;
            this.description = description;
        }
    }

    static final Badge[] ALL = {
        new Badge("first_ghost", "Ghost Hunter", "Eat a frightened ghost"),
        new Badge("clean_sweep", "Clean Sweep", "Eat all four ghosts with one power pellet"),
        new Badge("close_call", "Close Call", "Eat a ghost in the last 2 seconds of a power-up"),
        new Badge("fruit", "Fruit Lover", "Eat a bonus fruit"),
        new Badge("level_clear", "Maze Runner", "Clear a level"),
        new Badge("flawless", "Untouchable", "Clear a level without losing a life"),
        new Badge("brave", "Brave Heart", "Clear a level on Hard"),
        new Badge("explorer", "Explorer", "Reach level 4 and see all four mazes"),
        new Badge("marathon", "Marathon", "Reach level 6"),
        new Badge("one_up", "1UP", "Score 10,000 points and earn an extra life"),
        new Badge("high_roller", "High Roller", "Score 30,000 points in one game"),
        new Badge("architect", "Architect", "Play a maze you built in the editor"),
    };

    private final Path file;
    private final int bannerFrames;
    private final Set<String> unlocked = new LinkedHashSet<>();
    private final ArrayDeque<Badge> queue = new ArrayDeque<>();
    private Badge showing;
    private int showTimer;

    Achievements(Path file, int bannerFrames) {
        this.file = file;
        this.bannerFrames = bannerFrames;
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (find(line.trim()) != null) unlocked.add(line.trim());
            }
        } catch (IOException e) {
            // nothing unlocked yet
        }
    }

    private static Badge find(String id) {
        for (Badge b : ALL) {
            if (b.id.equals(id)) return b;
        }
        return null;
    }

    boolean has(String id) {
        return unlocked.contains(id);
    }

    int count() {
        return unlocked.size();
    }

    /** Unlocks a badge (once) and queues its banner. */
    void unlock(String id) {
        Badge badge = find(id);
        if (badge == null || !unlocked.add(id)) return;
        queue.add(badge);
        try {
            Files.write(file, new ArrayList<>(unlocked), StandardCharsets.UTF_8);
        } catch (IOException e) {
            // not fatal: the badge just won't be remembered after a restart
        }
    }

    /** Advances the banner by one frame. @return the badge whose banner just appeared, or null */
    Badge tick() {
        if (showing != null && --showTimer <= 0) {
            showing = null;
        }
        if (showing == null && !queue.isEmpty()) {
            showing = queue.poll();
            showTimer = bannerFrames;
            return showing;
        }
        return null;
    }

    /** Draws the "achievement unlocked" banner centred near the top of an area this wide. */
    void drawBanner(Graphics2D g, int width) {
        if (showing == null) return;
        g.setFont(new Font("Arial", Font.BOLD, 16));
        int textW = Math.max(g.getFontMetrics().stringWidth(showing.name), 170);
        int w = textW + 70;
        int h = 52;
        int x = (width - w) / 2;
        int y = 12;
        g.setColor(new Color(16, 19, 46, 245));
        g.fillRoundRect(x, y, w, h, 14, 14);
        g.setColor(new Color(0xFFD52E));
        g.setStroke(new BasicStroke(2));
        g.drawRoundRect(x, y, w, h, 14, 14);
        g.setStroke(new BasicStroke(1));
        drawTrophy(g, x + 14, y + 13, 26, new Color(0xFFD52E));
        g.setFont(new Font("Arial", Font.BOLD, 10));
        g.drawString("ACHIEVEMENT UNLOCKED", x + 52, y + 21);
        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.BOLD, 16));
        g.drawString(showing.name, x + 52, y + 41);
    }

    /** A small trophy cup. */
    static void drawTrophy(Graphics2D g, int x, int y, int size, Color color) {
        Graphics2D t = (Graphics2D) g.create();
        t.translate(x, y);
        t.scale(size / 24.0, size / 24.0);
        t.setColor(color);
        t.setStroke(new BasicStroke(2.2f));
        t.drawArc(1, 4, 7, 7, 90, 180);   // left handle
        t.drawArc(16, 4, 7, 7, 270, 180); // right handle
        t.fillRoundRect(5, 2, 14, 4, 2, 2);
        t.fillArc(5, -4, 14, 18, 180, 180); // cup
        t.fillRect(11, 13, 2, 5);           // stem
        t.fillRoundRect(7, 18, 10, 4, 2, 2); // base
        t.dispose();
    }

    /** Splits text into lines no wider than maxWidth for the current font. */
    static List<String> wrap(Graphics2D g, String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String candidate = line.length() == 0 ? word : line + " " + word;
            if (g.getFontMetrics().stringWidth(candidate) > maxWidth && line.length() > 0) {
                lines.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(candidate);
            }
        }
        if (line.length() > 0) lines.add(line.toString());
        return lines;
    }
}

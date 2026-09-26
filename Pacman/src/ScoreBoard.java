import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** The top 5 scores (with player names) for one difficulty, saved to a text file. */
final class ScoreBoard {
    static final int SIZE = 5;
    static final int MAX_NAME_LENGTH = 10;

    static final class Entry {
        final String name;
        final int score;

        Entry(String name, int score) {
            this.name = name;
            this.score = score;
        }
    }

    private final Path file;
    private final List<Entry> entries = new ArrayList<>();

    /** @param legacyFile the single-number high score file older versions wrote, imported once */
    ScoreBoard(Path file, Path legacyFile) {
        this.file = file;
        if (Files.exists(file)) {
            load();
        } else {
            importLegacy(legacyFile);
        }
    }

    List<Entry> entries() {
        return Collections.unmodifiableList(entries);
    }

    int best() {
        return entries.isEmpty() ? 0 : entries.get(0).score;
    }

    boolean qualifies(int score) {
        return score > 0 && (entries.size() < SIZE || score > entries.get(SIZE - 1).score);
    }

    /** Adds the score and saves the board. @return its rank (0 = first place), or -1 if it didn't make the board */
    int add(String name, int score) {
        if (!qualifies(score)) return -1;
        int rank = 0;
        while (rank < entries.size() && entries.get(rank).score >= score) {
            rank++;
        }
        entries.add(rank, new Entry(name, score));
        while (entries.size() > SIZE) {
            entries.remove(entries.size() - 1);
        }
        save();
        return rank;
    }

    private void load() {
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String[] parts = line.split("\t", 2);
                if (parts.length != 2) continue;
                try {
                    entries.add(new Entry(parts[1], Integer.parseInt(parts[0].trim())));
                } catch (NumberFormatException e) {
                    // skip a corrupted line
                }
            }
        } catch (IOException e) {
            // unreadable file: start with an empty board
        }
        entries.sort((a, b) -> Integer.compare(b.score, a.score));
        while (entries.size() > SIZE) {
            entries.remove(entries.size() - 1);
        }
    }

    private void importLegacy(Path legacyFile) {
        try {
            int score = Integer.parseInt(new String(Files.readAllBytes(legacyFile), StandardCharsets.UTF_8).trim());
            if (score > 0) {
                entries.add(new Entry("---", score));
                save();
            }
        } catch (IOException | NumberFormatException e) {
            // no old high score to bring over
        }
    }

    private void save() {
        List<String> lines = new ArrayList<>();
        for (Entry e : entries) {
            lines.add(e.score + "\t" + e.name);
        }
        try {
            Files.write(file, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            // not fatal: the scores just won't survive a restart
        }
    }
}

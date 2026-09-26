import java.awt.*;
import java.awt.geom.AffineTransform;

/** Bonus fruit: a different one each level, worth more the further you get. */
enum Fruit {
    CHERRY("Cherry", 100),
    STRAWBERRY("Strawberry", 300),
    ORANGE("Orange", 500),
    APPLE("Apple", 700),
    MELON("Melon", 1000),
    GRAPES("Grapes", 2000);

    final String label;
    final int points;

    Fruit(String label, int points) {
        this.label = label;
        this.points = points;
    }

    /** Level 1 = cherry, level 2 = strawberry, ... level 6 and beyond = grapes. */
    static Fruit forLevel(int level) {
        Fruit[] all = values();
        return all[Math.min(level, all.length) - 1];
    }

    /** Draws the fruit in a size x size box. The cherry uses the project's cherry.png sprite. */
    void draw(Graphics2D g, int x, int y, int size, Image cherryImage) {
        if (this == CHERRY) {
            g.drawImage(cherryImage, x, y, size, size, null);
            return;
        }
        Graphics2D f = (Graphics2D) g.create();
        f.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        f.translate(x, y);
        f.scale(size / 32.0, size / 32.0); // shapes below are drawn on a 32x32 grid
        switch (this) {
            case STRAWBERRY: drawStrawberry(f); break;
            case ORANGE: drawOrange(f); break;
            case APPLE: drawApple(f); break;
            case MELON: drawMelon(f); break;
            case GRAPES: drawGrapes(f); break;
            default: break;
        }
        f.dispose();
    }

    private static void drawStrawberry(Graphics2D g) {
        g.setColor(new Color(0xE8202A));
        g.fillOval(6, 8, 20, 16);
        g.fillPolygon(new int[] {7, 25, 16}, new int[] {17, 17, 30}, 3);
        g.setColor(new Color(0xFFE9A8));
        int[][] seeds = {{11, 13}, {16, 12}, {21, 13}, {13, 18}, {19, 18}, {16, 23}, {10, 17}, {22, 17}};
        for (int[] s : seeds) {
            g.fillRect(s[0], s[1], 2, 2);
        }
        g.setColor(new Color(0x2FA84F));
        g.fillPolygon(new int[] {8, 13, 16, 19, 24, 16}, new int[] {7, 6, 3, 6, 7, 11}, 6);
    }

    private static void drawOrange(Graphics2D g) {
        g.setColor(new Color(0xFF9F1A));
        g.fillOval(5, 8, 22, 21);
        g.setColor(new Color(0xE07A00));
        g.fillOval(9, 13, 2, 2);
        g.fillOval(20, 15, 2, 2);
        g.fillOval(14, 22, 2, 2);
        g.setColor(new Color(0x7A4A12));
        g.fillRect(15, 4, 2, 6);
        g.setColor(new Color(0x2FA84F));
        g.fillOval(17, 3, 9, 5);
    }

    private static void drawApple(Graphics2D g) {
        g.setColor(new Color(0xD7141F));
        g.fillOval(4, 9, 14, 20);
        g.fillOval(14, 9, 14, 20);
        g.fillOval(7, 12, 18, 18);
        g.setColor(new Color(255, 255, 255, 140));
        g.fillOval(8, 13, 4, 6);
        g.setColor(new Color(0x7A4A12));
        g.fillRect(15, 3, 2, 8);
        g.setColor(new Color(0x2FA84F));
        AffineTransform old = g.getTransform();
        g.rotate(-0.5, 20, 6);
        g.fillOval(17, 3, 9, 5);
        g.setTransform(old);
    }

    private static void drawMelon(Graphics2D g) {
        g.setColor(new Color(0x3DBE4A));
        g.fillOval(4, 7, 24, 23);
        g.setColor(new Color(0x1E7A2A));
        g.setStroke(new BasicStroke(1.6f));
        g.drawArc(8, 7, 16, 23, 90, 180);
        g.drawArc(8, 7, 16, 23, 270, 180);
        g.drawLine(16, 7, 16, 30);
        g.setColor(new Color(0x7A4A12));
        g.fillRect(15, 3, 2, 5);
    }

    private static void drawGrapes(Graphics2D g) {
        g.setColor(new Color(0x2FA84F));
        g.fillRect(15, 2, 2, 6);
        g.fillOval(17, 3, 8, 4);
        int[][] rows = {{5, 11, 17, 23}, {8, 14, 20}, {11, 17}, {14}};
        for (int r = 0; r < rows.length; r++) {
            for (int cx : rows[r]) {
                int cy = 8 + r * 5;
                g.setColor(new Color(0x7B2FBF));
                g.fillOval(cx - 1, cy, 7, 7);
                g.setColor(new Color(0xB57BFF));
                g.fillOval(cx, cy + 1, 2, 2);
            }
        }
    }
}

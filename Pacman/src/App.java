import javax.swing.JFrame;
import javax.swing.SwingUtilities;

public class App {
    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Pac Man");
            frame.setResizable(false);
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

            PacMan pacmanGame = new PacMan();
            frame.add(pacmanGame);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
            pacmanGame.requestFocusInWindow();
        });
    }
}

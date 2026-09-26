import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;

/** Retro sound effects synthesized at runtime, so no audio files are needed. */
public class Sound {
    private static final float SAMPLE_RATE = 22050f;

    private final ExecutorService player = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "pacman-sound");
        t.setDaemon(true);
        return t;
    });
    private volatile boolean enabled = true;
    private volatile boolean available = true;
    private boolean wakaHigh = false;
    private final AtomicInteger pending = new AtomicInteger();

    public boolean isEnabled() {
        return enabled;
    }

    public void toggle() {
        enabled = !enabled;
    }

    public void waka() {
        if (pending.get() > 0) return; // don't let pellet sounds pile up behind each other
        wakaHigh = !wakaHigh;
        play(wakaHigh ? sweep(450, 250, 60) : sweep(250, 450, 60));
    }

    public void powerUp() {
        play(concat(sweep(300, 900, 120), sweep(300, 900, 120)));
    }

    public void eatGhost() {
        play(sweep(200, 1400, 180));
    }

    public void fruit() {
        play(concat(tone(880, 70), tone(1320, 110)));
    }

    public void death() {
        play(concat(sweep(800, 150, 700), tone(120, 120)));
    }

    public void levelClear() {
        play(concat(tone(523, 110), tone(659, 110), tone(784, 110), tone(1047, 250)));
    }

    public void start() {
        play(concat(tone(494, 120), tone(988, 120), tone(740, 120), tone(622, 120),
                    tone(988, 90), tone(740, 180), tone(622, 240)));
    }

    private void play(byte[] samples) {
        if (!enabled || !available) return;
        pending.incrementAndGet();
        player.submit(() -> {
            AudioFormat format = new AudioFormat(SAMPLE_RATE, 8, 1, true, false);
            try (SourceDataLine line = AudioSystem.getSourceDataLine(format)) {
                line.open(format);
                line.start();
                line.write(samples, 0, samples.length);
                line.drain();
            } catch (Exception | LinkageError e) {
                available = false; // no audio device: keep playing silently
            } finally {
                pending.decrementAndGet();
            }
        });
    }

    private static byte[] tone(double freq, int ms) {
        return sweep(freq, freq, ms);
    }

    /** Square wave gliding from startFreq to endFreq, with a short fade-out to avoid clicks. */
    private static byte[] sweep(double startFreq, double endFreq, int ms) {
        int n = (int) (SAMPLE_RATE * ms / 1000);
        byte[] out = new byte[n];
        double phase = 0;
        for (int i = 0; i < n; i++) {
            double t = (double) i / n;
            phase += (startFreq + (endFreq - startFreq) * t) / SAMPLE_RATE;
            double fade = Math.min(1.0, (n - i) / (SAMPLE_RATE * 0.01));
            out[i] = (byte) ((phase % 1.0 < 0.5 ? 1 : -1) * 28 * fade);
        }
        return out;
    }

    private static byte[] concat(byte[]... parts) {
        int len = 0;
        for (byte[] p : parts) len += p.length;
        byte[] out = new byte[len];
        int pos = 0;
        for (byte[] p : parts) {
            System.arraycopy(p, 0, out, pos, p.length);
            pos += p.length;
        }
        return out;
    }
}

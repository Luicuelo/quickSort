package principal;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * {@link Sound} implementation based on the Java Sound API.
 * <p>
 * All tones are synthesized and played on a single daemon worker thread, so
 * playback never blocks the UI and never spawns unbounded threads. When the
 * worker falls behind, new tones are dropped instead of accumulating latency.
 * </p>
 */
public class SoundPlayer implements Sound {

    /** Preferred audio formats, tried in order until one is supported. */
    private static final AudioFormat[] FORMATS = {
            new AudioFormat(44100f, 16, 1, true, false),
            new AudioFormat(22050f, 16, 1, true, false),
            new AudioFormat(8000f, 8, 1, true, false)
    };
    private static final int TONE_DURATION_MS = 75;
    private static final int MIN_FREQUENCY_HZ = 200;
    private static final int MAX_FREQUENCY_HZ = 800;
    /** Sample amplitude for 8-bit audio, kept well below 127 so the feedback stays quiet. */
    private static final int TONE_VOLUME = 60;
    /** Sample amplitude for 16-bit audio, scaled to match the 8-bit loudness. */
    private static final int TONE_VOLUME_16_BIT = 6000;
    /** Maximum tones waiting for the worker; extra requests are dropped. */
    private static final int MAX_PENDING_TONES = 4;

    private final ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "sound-player");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicInteger pendingTones = new AtomicInteger();

    @Override
    public void playTone(double pitch) {
        if (pendingTones.get() >= MAX_PENDING_TONES) {
            return;
        }
        double clamped = Math.max(0, Math.min(1, pitch));
        int frequency = MIN_FREQUENCY_HZ + (int) ((MAX_FREQUENCY_HZ - MIN_FREQUENCY_HZ) * clamped);
        pendingTones.incrementAndGet();
        worker.execute(() -> {
            try {
                generateTone(frequency, TONE_DURATION_MS);
            } finally {
                pendingTones.decrementAndGet();
            }
        });
    }

    @Override
    public void shutdown() {
        worker.shutdownNow();
    }

    /**
     * Generates and plays a sine-wave tone with a fade-in/fade-out envelope
     * to avoid clicking sounds. Runs on the worker thread.
     *
     * @param frequency  tone frequency in Hz
     * @param durationMs tone duration in milliseconds
     */
    private void generateTone(int frequency, int durationMs) {
        for (AudioFormat format : FORMATS) {
            try {
                DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
                if (!AudioSystem.isLineSupported(info)) {
                    continue;
                }

                int sampleCount = Math.max(1, (int) Math.ceil(durationMs * format.getSampleRate() / 1000.0));
                byte[] buffer = new byte[sampleCount * format.getFrameSize()];
                int fadeLength = Math.min(sampleCount / 7, 20);

                for (int i = 0; i < sampleCount; i++) {
                    double angle = i / (format.getSampleRate() / frequency) * 2.0 * Math.PI;

                    double envelope;
                    if (i < fadeLength) {
                        envelope = (double) i / fadeLength;              // fade in
                    } else if (i > sampleCount - fadeLength) {
                        envelope = (double) (sampleCount - i) / fadeLength; // fade out
                    } else {
                        envelope = 1.0;
                    }

                    if (format.getSampleSizeInBits() == 8) {
                        buffer[i] = (byte) (Math.sin(angle) * TONE_VOLUME * envelope);
                    } else {
                        short sample = (short) (Math.sin(angle) * TONE_VOLUME_16_BIT * envelope);
                        int index = i * 2;
                        buffer[index] = (byte) (sample & 0xFF);
                        buffer[index + 1] = (byte) ((sample >> 8) & 0xFF);
                    }
                }

                SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info);
                line.open(format);
                line.start();
                line.write(buffer, 0, buffer.length);
                line.drain();
                line.close();
                return;
            } catch (LineUnavailableException e) {
                // Try the next format if the current one is unavailable
            }
        }
        System.err.println("Audio not available: no supported audio output format was found for this system.");
    }
}
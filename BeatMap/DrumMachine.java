import java.util.Random;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

final class DrumMachine implements AutoCloseable {
    private static final int SAMPLE_RATE = 44100;
    private static final int BLOCK_FRAMES = 256;
    // Each published array is immutable while the audio thread is using it.
    private volatile float[] pattern;
    private volatile boolean playing;
    private volatile boolean closed;
    private volatile int step = -1;
    private volatile String error = "";
    private Thread worker;

    void setPattern(float[] values) {
        pattern = values.clone();
    }

    synchronized void toggle() {
        if (closed) return;
        playing = !playing;
        if (playing && worker == null) {
            worker = new Thread(this::render, "drum-audio");
            worker.setDaemon(true);
            worker.start();
        }
    }

    boolean isPlaying() { return playing; }
    int currentStep() { return step; }
    String error() { return error; }

    private void render() {
        AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
        try (SourceDataLine line = AudioSystem.getSourceDataLine(format)) {
            line.open(format, BLOCK_FRAMES * 2 * 4);
            line.start();
            error = "";
            renderBlocks(line);
        } catch (LineUnavailableException | IllegalArgumentException exception) {
            error = "Audio unavailable. Check the Mac sound output and try Play again.";
            System.err.println(exception);
        } finally {
            synchronized (this) {
                playing = false;
                step = -1;
                worker = null;
            }
        }
    }

    private void renderBlocks(SourceDataLine line) {
        float[][] sounds = makeSounds();
        int[] cursor = new int[sounds.length];
        float[] gains = new float[sounds.length];
        byte[] block = new byte[BLOCK_FRAMES * 2];
        double framesToNextStep = 0;
        int nextStep = 0;
        while (!closed) {
            float[] settings = pattern;
            if (!playing || settings == null) {
                step = -1;
                nextStep = 0;
                framesToNextStep = 0;
                java.util.Arrays.fill(gains, 0);
                java.util.Arrays.fill(block, (byte) 0);
            } else {
                for (int frame = 0; frame < BLOCK_FRAMES; frame++) {
                    if (framesToNextStep <= 0) {
                        for (int voice = 0; voice < sounds.length; voice++) {
                            float gain = settings[CornerPatterns.hit(voice, nextStep)];
                            if (gain <= 0) continue;
                            gains[voice] = gain;
                            cursor[voice] = 0;
                        }
                        step = nextStep;
                        nextStep = (nextStep + 1) % CornerPatterns.STEPS;
                        framesToNextStep += SAMPLE_RATE * 60.0 / (settings[CornerPatterns.TEMPO] * 4);
                    }
                    framesToNextStep--;
                    float sample = 0;
                    for (int voice = 0; voice < sounds.length; voice++) {
                        if (gains[voice] > 0 && cursor[voice] < sounds[voice].length) {
                            sample += gains[voice] * sounds[voice][cursor[voice]++];
                        }
                    }
                    int pcm = (int) (Math.max(-1, Math.min(1, sample)) * 32767);
                    block[frame * 2] = (byte) pcm;
                    block[frame * 2 + 1] = (byte) (pcm >> 8);
                }
            }
            line.write(block, 0, block.length);
        }
    }

    private static float[][] makeSounds() {
        float[] kick = new float[(int) (SAMPLE_RATE * .18)];
        float[] snare = new float[(int) (SAMPLE_RATE * .14)];
        float[] hat = new float[(int) (SAMPLE_RATE * .067)];
        Random random = new Random(9);
        double phase = 0;
        for (int i = 0; i < kick.length; i++) {
            double time = (double) i / SAMPLE_RATE;
            phase += 2 * Math.PI * (47 + 104 * Math.exp(-time * 37)) / SAMPLE_RATE;
            kick[i] = (float) (.71 * Math.sin(phase) * envelope(time, .055, kick.length - i));
        }
        double previousNoise = 0;
        for (int i = 0; i < snare.length; i++) {
            double time = (double) i / SAMPLE_RATE;
            double noise = 2 * random.nextDouble() - 1;
            double tone = Math.sin(2 * Math.PI * 179 * time) * Math.exp(-time * 32);
            snare[i] = (float) ((.31 * (noise - .45 * previousNoise) + .20 * tone) * envelope(time, .042, snare.length - i));
            previousNoise = noise;
        }
        previousNoise = 0;
        for (int i = 0; i < hat.length; i++) {
            double time = (double) i / SAMPLE_RATE;
            double noise = 2 * random.nextDouble() - 1;
            hat[i] = (float) (.25 * (noise - previousNoise) * envelope(time, .019, hat.length - i));
            previousNoise = noise;
        }
        return new float[][] { kick, snare, hat };
    }

    private static double envelope(double time, double decay, int remainingFrames) {
        return Math.min(1, time * 900) * Math.exp(-time / decay) * Math.min(1, remainingFrames / 180.0);
    }

    public synchronized void close() {
        closed = true;
        playing = false;
    }
}

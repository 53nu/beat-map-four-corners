import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

final class WekiLink implements AutoCloseable {
    private static final String INPUTS = "/beatmap/inputs";
    private static final String PREDICTION = "/beatmap/prediction";
    private static final String TRAINED = "/wekinator/status/done-training";
    private static final String CONTROL = "/wekinator/control/";
    private static final String OUTPUT_TAGS = "," + "f".repeat(CornerPatterns.VALUES);
    private final DatagramChannel channel;
    private final InetSocketAddress destination;
    private final ByteBuffer receiveBuffer = ByteBuffer.allocate(512);
    private final ByteBuffer sendBuffer = ByteBuffer.allocate(2048);

    WekiLink() throws IOException {
        InetAddress localhost = InetAddress.getByName("127.0.0.1");
        destination = new InetSocketAddress(localhost, 6460);
        channel = DatagramChannel.open();
        try {
            channel.bind(new InetSocketAddress(localhost, 12014));
            channel.configureBlocking(false);
        } catch (IOException error) {
            channel.close();
            throw error;
        }
    }

    void sendPosition(float x, float y) throws IOException {
        send(INPUTS, x, y, x * y);
    }

    float[] readPrediction() throws IOException {
        float[] latest = null;
        for (int i = 0; i < 32 && receive(); i++) {
            float[] values = readPredictionPacket();
            if (values != null) latest = values;
        }
        return latest;
    }

    // During training the worker owns the socket. Live position updates would become examples.
    void train(float[][] patterns) throws IOException, InterruptedException {
        verifyConnection();
        try {
            sendTrainingBundle(patterns);
            awaitTraining();
            Thread.sleep(70);
            command("startRunning");
            for (int corner = 0; corner < 4; corner++) {
                verifyCorner(corner, patterns[corner]);
            }
        } catch (IOException | InterruptedException error) {
            if (channel.isOpen()) {
                try {
                    command("stopRecording");
                } catch (IOException cleanupError) {
                    error.addSuppressed(cleanupError);
                }
            }
            throw error;
        }
    }

    private void sendTrainingBundle(float[][] patterns) throws IOException {
        // One ordered datagram keeps an input paired with its exact target values.
        sendBuffer.clear();
        writeOscString(sendBuffer, "#bundle");
        sendBuffer.putLong(1); // OSC immediate timetag.
        appendMessage(CONTROL + "stopRecording");
        appendMessage(CONTROL + "stopRunning");
        appendMessage(CONTROL + "deleteAllExamples");
        appendMessage(CONTROL + "startRecording");
        for (int corner = 0; corner < 4; corner++) {
            float x = corner % 2;
            float y = corner / 2;
            appendMessage(CONTROL + "outputs", patterns[corner]);
            appendMessage(INPUTS, x, y, x * y);
        }
        appendMessage(CONTROL + "stopRecording");
        appendMessage(CONTROL + "train");
        sendPacket();
    }

    private void appendMessage(String address, float... values) {
        int sizePosition = sendBuffer.position();
        sendBuffer.putInt(0);
        writeMessage(address, values);
        sendBuffer.putInt(sizePosition, sendBuffer.position() - sizePosition - Integer.BYTES);
    }

    private void verifyConnection() throws IOException, InterruptedException {
        readPrediction();
        command("startRunning");
        long deadline = deadline(2);
        while (System.nanoTime() < deadline) {
            sendPosition(.5f, .5f);
            Thread.sleep(45);
            if (readPrediction() != null) {
                // Let Wekinator complete the run-state transition before recording.
                Thread.sleep(100);
                return;
            }
        }
        throw new IOException("Wekinator is not responding. Open CornerModel, then try again.");
    }

    private void awaitTraining() throws IOException, InterruptedException {
        long deadline = deadline(8);
        while (System.nanoTime() < deadline) {
            if (receive()) {
                if (TRAINED.equals(readOscString(receiveBuffer))) return;
            } else {
                Thread.sleep(15);
            }
        }
        throw new IOException("Wekinator did not finish training. Check its window and try again.");
    }

    private void verifyCorner(int corner, float[] expected) throws IOException, InterruptedException {
        long deadline = deadline(2);
        while (System.nanoTime() < deadline) {
            sendPosition(corner % 2, corner / 2);
            Thread.sleep(40);
            float[] prediction = readPrediction();
            if (prediction != null && matches(prediction, expected)) return;
        }
        throw new IOException("Wekinator did not reproduce corner " + (char) ('A' + corner) + ". Try training again.");
    }

    private static boolean matches(float[] actual, float[] expected) {
        for (int i = 0; i < actual.length; i++) {
            if (Math.abs(actual[i] - expected[i]) > (i == CornerPatterns.TEMPO ? .12f : .012f)) return false;
        }
        return true;
    }

    private static long deadline(int seconds) {
        return System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
    }

    private void command(String action) throws IOException {
        send(CONTROL + action);
    }

    private void send(String address, float... values) throws IOException {
        sendBuffer.clear();
        writeMessage(address, values);
        sendPacket();
    }

    private void writeMessage(String address, float[] values) {
        writeOscString(sendBuffer, address);
        writeOscString(sendBuffer, "," + "f".repeat(values.length));
        for (float value : values) sendBuffer.putFloat(value);
    }

    private void sendPacket() throws IOException {
        sendBuffer.flip();
        if (channel.send(sendBuffer, destination) == 0) throw new IOException("Could not send an OSC message.");
    }

    private boolean receive() throws IOException {
        receiveBuffer.clear();
        if (channel.receive(receiveBuffer) == null) return false;
        receiveBuffer.flip();
        return true;
    }

    private float[] readPredictionPacket() {
        if (!PREDICTION.equals(readOscString(receiveBuffer)) || !OUTPUT_TAGS.equals(readOscString(receiveBuffer))
            || receiveBuffer.remaining() != CornerPatterns.VALUES * Float.BYTES) return null;
        float[] values = new float[CornerPatterns.VALUES];
        for (int i = 0; i < values.length; i++) {
            float value = receiveBuffer.getFloat();
            if (!Float.isFinite(value)) return null;
            values[i] = i == CornerPatterns.TEMPO ? Math.max(70, Math.min(150, value)) : Math.max(0, Math.min(1, value));
        }
        return values;
    }

    private static void writeOscString(ByteBuffer buffer, String value) {
        buffer.put(value.getBytes(StandardCharsets.US_ASCII)).put((byte) 0);
        while (buffer.position() % 4 != 0) buffer.put((byte) 0);
    }

    private static String readOscString(ByteBuffer buffer) {
        int start = buffer.position();
        while (buffer.hasRemaining()) {
            if (buffer.get() != 0) continue;
            int end = buffer.position() - 1;
            int aligned = (buffer.position() + 3) & ~3;
            if (aligned > buffer.limit()) return "";
            buffer.position(aligned);
            return new String(buffer.array(), start, end - start, StandardCharsets.US_ASCII);
        }
        return "";
    }

    public void close() {
        try {
            channel.close();
        } catch (IOException error) {
            System.err.println(error);
        }
    }
}

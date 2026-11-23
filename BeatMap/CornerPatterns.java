import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

final class CornerPatterns {
    static final int STEPS = 16;
    static final int VOICES = 3;
    static final int TEMPO = VOICES * STEPS;
    static final int VALUES = TEMPO + 1;

    static int hit(int voice, int step) {
        return voice * STEPS + step;
    }

    static float[][] read(Path file) throws IOException {
        List<String> rows = Files.readAllLines(file, StandardCharsets.UTF_8);
        if (rows.size() != 4) throw new IOException("Expected four corner patterns.");
        float[][] patterns = new float[4][VALUES];
        for (int corner = 0; corner < 4; corner++) {
            String[] cells = rows.get(corner).split(",", -1);
            if (cells.length != VALUES) throw new IOException("Expected 48 hits and a tempo in each pattern.");
            for (int i = 0; i < VALUES; i++) {
                float value;
                try {
                    value = Float.parseFloat(cells[i]);
                } catch (NumberFormatException error) {
                    throw new IOException("Invalid number in corner patterns.", error);
                }
                if (!Float.isFinite(value) || (i == TEMPO ? value < 70 || value > 150 : value < 0 || value > 1)) {
                    throw new IOException("A corner pattern value is out of range.");
                }
                patterns[corner][i] = value;
            }
        }
        return patterns;
    }

    static void write(Path file, float[][] patterns) throws IOException {
        List<String> rows = new ArrayList<>();
        for (float[] pattern : patterns) {
            StringBuilder row = new StringBuilder();
            for (float value : pattern) {
                if (row.length() > 0) row.append(',');
                row.append(value);
            }
            rows.add(row.toString());
        }
        Path temporary = Files.createTempFile(file.getParent(), "corners-", ".tmp");
        try {
            Files.write(temporary, rows, StandardCharsets.UTF_8);
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}

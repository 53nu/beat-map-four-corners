import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

final int PAD_X = 40, PAD_Y = 140, PAD_W = 542, PAD_H = 233;
final int GRID_X = 149, GRID_Y = 473, CELL = 42, STEP_GAP = 7;
final int INK = #F3F1E9, MUTED = #ACB0BB, PANEL = #1E222C, STROKE = #343B48;
final int ACID = #E8F679;
final String[] VOICES = { "KICK", "SNARE", "HI-HAT" };
final int[] COLORS = { #FF947E, #D4B9FF, ACID };
final String[] CORNERS = { "A · Top left", "B · Top right", "C · Bottom left", "D · Bottom right" };

DrumMachine drums = new DrumMachine();
WekiLink weki;
PFont ui, bold, mono;
Path patternFile;
float[][] corners;
float[] live;
float padX = .5, padY = .5;
int selected = -1;
int lastSend = -1000, lastOutput = -10000;
boolean restoreOnConnect = true;
boolean dirty;
boolean playAfterTraining;
String notice = "";
String saveError = "";
FutureTask<Void> training;

void setup() {
  size(920, 668);
  surface.setTitle("Beat Map · Four Corners");
  pixelDensity(displayDensity());
  frameRate(60);
  ui = createFont("SansSerif", 14, true);
  bold = new PFont(new java.awt.Font("SansSerif", java.awt.Font.BOLD, 46), true);
  mono = createFont("Monospaced", 13, true);
  patternFile = Path.of(dataPath("corners.csv"));
  try {
    corners = CornerPatterns.read(patternFile);
    drums.setPattern(corners[0]);
  } catch (IOException error) {
    notice = "Could not read corners.csv. Check the project’s data folder.";
    println(error);
  }
  try {
    weki = new WekiLink();
  } catch (IOException error) {
    notice = "Could not open port 12014. Close any other Four Corners window.";
    println(error);
  }
}

void draw() {
  updateWekinator();
  background(#13161D);
  noStroke();
  fill(INK);
  textFont(bold, 42);
  text("Beat Map", 38, 68);
  fill(MUTED);
  textFont(ui, 15);
  text("Edit four grooves. Move between them to play.", 41, 96);
  drawConnection();
  drawPad();
  drawCorners();
  drawSequencer();
  drawTransport();
}

void updateWekinator() {
  if (weki == null || corners == null) return;
  if (training != null) {
    if (training.isDone()) finishTraining();
    return;
  }
  if (selected < 0 && within(mouseX, mouseY, PAD_X, PAD_Y, PAD_W, PAD_H)) {
    padX = constrain(norm(mouseX, PAD_X, PAD_X + PAD_W), 0, 1);
    padY = constrain(norm(mouseY, PAD_Y, PAD_Y + PAD_H), 0, 1);
  }
  try {
    int now = millis();
    if (now - lastSend >= 32) {
      weki.sendPosition(padX, padY);
      lastSend = now;
    }
    float[] prediction = weki.readPrediction();
    if (prediction == null) return;
    lastOutput = now;
    if (restoreOnConnect) {
      restoreOnConnect = false;
      beginTraining(false);
      return;
    }
    live = prediction;
    if (selected < 0) drums.setPattern(live);
  } catch (IOException error) {
    notice = error.getMessage();
  }
}

void beginTraining(boolean startPlayback) {
  if (training != null || weki == null || corners == null) return;
  restoreOnConnect = false;
  playAfterTraining = startPlayback;
  float[][] snapshot = new float[corners.length][];
  for (int corner = 0; corner < corners.length; corner++) snapshot[corner] = corners[corner].clone();
  notice = "Sending the four corner patterns to Wekinator…";
  training = new FutureTask<Void>(() -> {
    weki.train(snapshot);
    return null;
  });
  Thread worker = new Thread(training, "beat-map-training");
  worker.setDaemon(true);
  worker.start();
}

void finishTraining() {
  try {
    training.get();
    dirty = false;
    selected = -1;
    if (playAfterTraining && !drums.isPlaying()) drums.toggle();
    lastOutput = millis();
    notice = "";
  } catch (ExecutionException error) {
    notice = error.getCause().getMessage();
  } catch (InterruptedException error) {
    Thread.currentThread().interrupt();
    notice = "Training stopped. Press Train & play to retry.";
  } finally {
    training = null;
  }
}

boolean connected() {
  return millis() - lastOutput < 1100;
}

void drawConnection() {
  String label = "WAITING FOR WEKINATOR";
  if (connected()) label = "WEKINATOR LIVE";
  if (training != null) label = "TRAINING 4 CORNERS";
  fill(connected() || training != null ? #28301E : #282D36);
  rect(682, 45, 198, 31, 16);
  fill(connected() || training != null ? ACID : MUTED);
  circle(699, 60, 7);
  textFont(mono, 12);
  text(label, 711, 64);
}

void drawPad() {
  noStroke();
  fill(PANEL);
  rect(PAD_X, PAD_Y, PAD_W, PAD_H, 13);
  stroke(STROKE);
  strokeWeight(1);
  for (int i = 1; i < 8; i++) {
    float x = PAD_X + PAD_W * i / 8.0;
    line(x, PAD_Y + 1, x, PAD_Y + PAD_H - 1);
  }
  for (int i = 1; i < 4; i++) {
    float y = PAD_Y + PAD_H * i / 4.0;
    line(PAD_X + 1, y, PAD_X + PAD_W - 1, y);
  }
  float x = PAD_X + (selected < 0 ? padX : selected % 2) * PAD_W;
  float y = PAD_Y + (selected < 0 ? padY : selected / 2) * PAD_H;
  stroke(#606958);
  line(x, PAD_Y, x, PAD_Y + PAD_H);
  line(PAD_X, y, PAD_X + PAD_W, y);
  noStroke();
  textFont(mono, 14);
  for (int corner = 0; corner < 4; corner++) {
    int left = PAD_X + (corner % 2 == 0 ? 16 : PAD_W - 42);
    int top = PAD_Y + (corner < 2 ? 16 : PAD_H - 42);
    fill(corner == selected ? ACID : #12151D);
    rect(left, top, 26, 26, 6);
    fill(corner == selected ? #181B24 : INK);
    text(char('A' + corner), left + 9, top + 18);
  }
  noFill();
  stroke(ACID);
  strokeWeight(2);
  circle(x, y, 22);
  noStroke();
  fill(ACID);
  circle(x, y, 6);
}

void drawCorners() {
  fill(INK);
  textFont(bold, 16);
  text("Edit a corner", 614, 156);
  int cardWidth = 126;
  for (int corner = 0; corner < 4; corner++) {
    int left = 614 + corner % 2 * 140;
    int top = 170 + corner / 2 * 68;
    boolean active = selected == corner;
    if (active) stroke(ACID); else stroke(STROKE);
    fill(active ? #292F22 : PANEL);
    rect(left, top, cardWidth, 58, 8);
    noStroke();
    fill(active ? ACID : INK);
    textFont(bold, 13);
    text(CORNERS[corner], left + 10, top + 22);
    fill(MUTED);
    textFont(mono, 12);
    if (corners != null) text(round(corners[corner][CornerPatterns.TEMPO]) + " BPM", left + 10, top + 41);
  }
  button(614, 314, 163, 33, training == null ? "Train & play" : "Training…", training == null && (dirty || selected >= 0));
  if (dirty) {
    textFont(ui, 13);
    fill(MUTED);
    text("Changes to train", 615, 367);
  }
}

void drawSequencer() {
  float[] values = shownPattern();
  int playingStep = drums.currentStep();
  fill(INK);
  textFont(bold, 18);
  text(selected < 0 ? "Live groove" : "Corner " + char('A' + selected), 40, 421);
  if (values != null) {
    textFont(mono, 14);
    text(round(values[CornerPatterns.TEMPO]) + " BPM", 750, 421);
  }
  if (selected >= 0) {
    button(714, 399, 27, 28, "−", false);
    button(853, 399, 27, 28, "+", false);
    fill(MUTED);
    textFont(ui, 13);
    text("Click a step to cycle full, soft, and off.", 154, 420);
  }
  textFont(mono, 12);
  fill(MUTED);
  text("4/4 · 16 STEPS", 40, 455);
  for (int beat = 0; beat < 4; beat++) {
    fill(INK);
    text(beat + 1, stepLeft(beat * 4) + 10, 455);
  }
  for (int voice = 0; voice < CornerPatterns.VOICES; voice++) {
    int top = GRID_Y + voice * 40;
    fill(COLORS[voice]);
    text(VOICES[voice], 40, top + 19);
    for (int step = 0; step < CornerPatterns.STEPS; step++) {
      float velocity = values == null ? 0 : values[CornerPatterns.hit(voice, step)];
      stroke(playingStep == step ? INK : STROKE);
      strokeWeight(1);
      fill(lerpColor(PANEL, COLORS[voice], velocity));
      rect(stepLeft(step), top, CELL - 5, 30, 5);
    }
  }
  noStroke();
}

void drawTransport() {
  button(40, 618, 105, 34, drums.isPlaying() ? "Stop" : "Play", true);
  String hint = "Space to start or stop playback.";
  if (selected < 0 && live == null) hint = "Open CornerModel in Wekinator and press Run.";
  if (selected >= 0) hint = "Play to hear this corner. Train & play applies all four corners to the pad.";
  if (!notice.isEmpty()) hint = notice;
  if (!saveError.isEmpty()) hint = saveError;
  if (!drums.error().isEmpty()) hint = drums.error();
  fill(MUTED);
  textFont(ui, 13);
  text(hint, 160, 639);
}

void button(int left, int top, int w, int h, String label, boolean highlighted) {
  noStroke();
  fill(highlighted ? ACID : #303642);
  rect(left, top, w, h, 7);
  textFont(bold, 13);
  fill(highlighted ? #151922 : INK);
  text(label, left + (w - textWidth(label)) / 2, top + h / 2 + 5);
}

float[] shownPattern() {
  if (corners == null) return null;
  if (selected >= 0) return corners[selected];
  return live == null ? corners[0] : live;
}

int stepLeft(int step) {
  return GRID_X + CELL * step + STEP_GAP * (step / 4);
}

boolean within(int x, int y, int left, int top, int w, int h) {
  return x >= left && x <= left + w && y >= top && y <= top + h;
}

void mousePressed() {
  if (within(mouseX, mouseY, 40, 618, 105, 34)) {
    if (corners != null) drums.toggle();
    return;
  }
  if (training != null || corners == null) return;
  if (within(mouseX, mouseY, 614, 314, 163, 33)) {
    beginTraining(true);
    return;
  }
  for (int corner = 0; corner < 4; corner++) {
    if (within(mouseX, mouseY, 614 + corner % 2 * 140, 170 + corner / 2 * 68, 126, 58)) {
      selected = corner;
      restoreOnConnect = false;
      drums.setPattern(corners[corner]);
      return;
    }
  }
  if (selected < 0) return;
  if (within(mouseX, mouseY, 714, 399, 27, 28) || within(mouseX, mouseY, 853, 399, 27, 28)) {
    float[] pattern = corners[selected];
    pattern[CornerPatterns.TEMPO] = constrain(pattern[CornerPatterns.TEMPO] + (mouseX < 750 ? -2 : 2), 70, 150);
    saveEdit();
    return;
  }
  for (int voice = 0; voice < CornerPatterns.VOICES; voice++) {
    for (int step = 0; step < CornerPatterns.STEPS; step++) {
      if (!within(mouseX, mouseY, stepLeft(step), GRID_Y + voice * 40, CELL - 5, 30)) continue;
      int hit = CornerPatterns.hit(voice, step);
      float current = corners[selected][hit];
      corners[selected][hit] = current < .01 ? 1 : (current > .5 ? .45 : 0);
      saveEdit();
      return;
    }
  }
}

void saveEdit() {
  dirty = true;
  drums.setPattern(corners[selected]);
  try {
    CornerPatterns.write(patternFile, corners);
    saveError = "";
  } catch (IOException error) {
    saveError = "Could not save corners.csv. Your edits are still available in this window.";
    println(error);
  }
}

void keyPressed() {
  if (key == ' ' && corners != null) drums.toggle();
}

void dispose() {
  drums.close();
  if (training != null) training.cancel(true);
  if (weki != null) weki.close();
  super.dispose();
}

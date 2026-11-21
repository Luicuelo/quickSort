package principal;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * JavaFX entry point of the sorting visualizer.
 * <p>
 * Responsibilities are limited to the user interface: building the window and
 * controls, parsing launch parameters, wiring the components together and
 * driving the animation loop. Sorting logic lives in {@link SortAlgorithm}
 * implementations; queue, playback modes and audio live in {@link Engine};
 * drawing lives in {@link Renderer}.
 * </p>
 */
public class QuickSort extends Application {

    /** Available bar sizes: smaller bars mean more elements and longer, calmer runs. */
    private enum BarSize {
        LARGE(16), MEDIUM(10), SMALL(6), TINY(4), MICRO(3);

        final int pixels;

        BarSize(int pixels) {
            this.pixels = pixels;
        }

        @Override
        public String toString() {
            String name = name();
            return name.charAt(0) + name.substring(1).toLowerCase() + " (" + pixels + " px)";
        }
    }

    private static final int DEFAULT_WIDTH = 800;
    private static final int DEFAULT_HEIGHT = 600;
    private static final int BUTTON_HEIGHT = 25;
    private static final Color BACKGROUND_COLOR = Color.LIGHTGRAY;
    private static final Color BAR_COLOR = new Color(0.3, 0.6, 0.9, 1);

    private int canvasWidth = DEFAULT_WIDTH;
    private int canvasHeight = DEFAULT_HEIGHT;
    private boolean soundEnabled = true;
    private BarSize barSize = BarSize.LARGE;

    private Canvas canvas;
    private Renderer renderer;
    private Engine engine;
    private Sound sound;
    private AnimationTimer animationTimer;
    private SortAlgorithm currentAlgorithm;
    /** The unsorted array of the current run, kept to replay it with another algorithm. */
    private int[] initialValues;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        Parameters params = getParameters();
        parseSize(params);
        parseSoundOption(params);

        canvas = new Canvas(canvasWidth, canvasHeight);
        sound = soundEnabled ? new SoundPlayer() : Sound.silent();
        rebuildRenderer();

        BorderPane root = new BorderPane();
        root.setCenter(canvas);
        root.setBottom(buildControls());

        restart(true);
        startAnimationLoop();

        primaryStage.setTitle("QuickSort");
        primaryStage.setScene(new Scene(root, canvasWidth, canvasHeight + BUTTON_HEIGHT));
        primaryStage.setResizable(false);
        primaryStage.show();
    }

    /** Builds the bottom control bar: action buttons and algorithm selector. */
    private HBox buildControls() {
        Button restartButton = createButton("Restart", () -> restart(true));
        // Lambdas, not bound method references: the engine is recreated on bar
        // size changes, so the buttons must read the current field each click
        Button iterateButton = createButton("Iterate", () -> engine.iterate());
        Button stopButton = createButton("Stop", () -> engine.stopIteration());
        Button stepButton = createButton("Step", () -> engine.step());

        List<SortAlgorithm> algorithms = SortAlgorithm.all();
        currentAlgorithm = algorithms.get(0);
        ComboBox<SortAlgorithm> algorithmSelector = new ComboBox<>();
        algorithmSelector.getItems().addAll(algorithms);
        algorithmSelector.setValue(currentAlgorithm);
        algorithmSelector.setPrefHeight(BUTTON_HEIGHT);
        algorithmSelector.setOnAction(e -> {
            currentAlgorithm = algorithmSelector.getValue();
            restart(false);
        });

        ComboBox<BarSize> sizeSelector = new ComboBox<>();
        sizeSelector.getItems().addAll(BarSize.values());
        sizeSelector.setValue(barSize);
        sizeSelector.setPrefHeight(BUTTON_HEIGHT);
        sizeSelector.setOnAction(e -> {
            barSize = sizeSelector.getValue();
            rebuildRenderer();
            restart(true);
        });

        HBox controls = new HBox(10, restartButton, iterateButton, stopButton, stepButton,
                algorithmSelector, sizeSelector);
        controls.setPadding(new Insets(0, 10, 0, 0));
        controls.setAlignment(Pos.CENTER_LEFT);
        return controls;
    }

    /** Recreates the drawing surface and the engine for the current bar size. */
    private void rebuildRenderer() {
        renderer = new Renderer(canvas.getGraphicsContext2D(), canvasWidth, canvasHeight,
                barSize.pixels, BAR_COLOR, BACKGROUND_COLOR);
        engine = new Engine(renderer, sound);
    }

    private Button createButton(String text, Runnable action) {
        Button button = new Button(text);
        button.setOnAction(e -> action.run());
        button.setPrefHeight(BUTTON_HEIGHT);
        return button;
    }

    /**
     * Prepares a new run: with a fresh random array, or replaying the same
     * initial array with the currently selected algorithm.
     * <p>
     * The algorithm runs to completion synchronously on its own copy of the
     * array, reporting operations to the engine, which queues them for
     * playback. {@code initialValues} stays unsorted for future replays.
     * </p>
     */
    private void restart(boolean generateNewArray) {
        if (generateNewArray || initialValues == null) {
            initialValues = generateRandomArray(renderer.barCount(), renderer.maxBarValue());
        }
        engine.prepare(initialValues, currentAlgorithm.tintsComparisons());
        currentAlgorithm.sort(initialValues.clone(), engine);
    }

    private static int[] generateRandomArray(int size, int maxValue) {
        int[] values = new int[size];
        for (int i = 0; i < size; i++) {
            values[i] = ThreadLocalRandom.current().nextInt(maxValue);
        }
        return values;
    }

    /** Drives the engine with one tick per frame. */
    private void startAnimationLoop() {
        animationTimer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                try {
                    engine.tick();
                } catch (RuntimeException e) {
                    System.err.println("Animation error: " + e.getMessage());
                    stop();
                }
            }
        };
        animationTimer.start();
    }

    /** Parses the named arguments {@code --width} and {@code --height}. */
    private void parseSize(Parameters params) {
        try {
            canvasWidth = parsePositive(params, "width", DEFAULT_WIDTH);
            canvasHeight = parsePositive(params, "height", DEFAULT_HEIGHT);
        } catch (NumberFormatException e) {
            System.err.println("Invalid size parameters, using defaults");
            canvasWidth = DEFAULT_WIDTH;
            canvasHeight = DEFAULT_HEIGHT;
        }
    }

    private static int parsePositive(Parameters params, String name, int defaultValue) {
        String raw = params.getNamed().get(name);
        if (raw == null) {
            return defaultValue;
        }
        int value = Integer.parseInt(raw);
        if (value <= 0) {
            throw new NumberFormatException(name + " must be positive");
        }
        return value;
    }

    /** Parses the named argument {@code --sound=true|false}. */
    private void parseSoundOption(Parameters params) {
        soundEnabled = Boolean.parseBoolean(params.getNamed().getOrDefault("sound", "true"));
    }

    @Override
    public void stop() {
        if (animationTimer != null) {
            animationTimer.stop();
        }
        if (sound != null) {
            sound.shutdown();
        }
    }
}
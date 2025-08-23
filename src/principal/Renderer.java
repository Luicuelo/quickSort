package principal;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.*;
import javafx.scene.paint.Color;

import javax.sound.sampled.*;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Queue;

/**
 * Renderer class for visualizing QuickSort algorithm operations.
 * <p>
 * This class handles the graphical representation of sorting operations
 * including:
 * - Drawing array elements as colored bars
 * - Animating swap operations with blinking effects
 * - Highlighting pivot elements during partitioning
 * - Managing animation queues and timing
 * - Providing step-by-step and continuous visualization modes
 * </p>
 */
class Renderer {
    /**
     * Action class represents a single animation operation in the QuickSort
     * visualization.
     * Each action has an instruction type, parameters, and manages its own
     * animation stages.
     */
    protected static class Action {

        /** First parameter (typically an array index) */
        private final int param1;
        /** Second parameter (typically another array index for swaps) */
        private final int param2;
        /** Current animation stage counter */
        private int stage;
        private int totalStages;

        /** Constant for swap operation */
        public static enum Operations {
            SWAP,
            PIVOT,
            COMPARE
        }

        /** The type of operation */
        private final Operations instruction;

        /**
         * Creates a new action for the animation queue.
         * 
         * @param instruction the type of operation (SWAP or PIVOT)
         * @param param1      first parameter (array index)
         * @param param2      second parameter (array index for swaps, unused for
         *                    pivots)
         */
        public Action(Operations instruction, int param1, int param2) {
            this.instruction = instruction;
            this.param1 = param1;
            this.param2 = param2;
            this.totalStages=getInstructionStageNumber();
            this.stage = totalStages;
        }

        /**
         * Gets the number of animation stages for this instruction type.
         * 
         * @return number of stages (8 for SWAP, 9 for PIVOT)
         */
        private int getInstructionStageNumber() {
            return switch (instruction) {
                case SWAP ->10;
                case PIVOT -> 2;
                case COMPARE -> 2;
                default -> 0;
            };
        }

        /** @return the instruction type */
        public Operations getInstruction() {
            return instruction;
        }

        /** @return the first parameter */
        public int getParam1() {
            return param1;
        }

        /** @return the second parameter */
        public int getParam2() {
            return param2;
        }

        /**
         * Advances the animation by one stage.
         * 
         * @return true if the animation is complete, false if more stages remain
         */
        public boolean endStage() {
            stage--;
            return stage <= 0;
        }

        /** @return the current stage number */
        public int getStage() {
            return stage;
        }

    /** @return totalStages number */
        public int getTotalStages(){
            return totalStages;
        }

    }

    /** Array of values to be rendered as bars */
    protected int[] renderArrayOfValues;
    /** Queue of animation actions to be processed */
    protected static final Queue<Action> actionQueue = new LinkedList<>();
    /** Size of each cell in pixels */
    protected static final int PIXEL_SIZE = 16;

    /** Default color for drawing bars */
    private final Color defaultColor;
    /** Inverted color for highlighting pivots */
    private final Color invertedDefaultColor;
    /** Background color for clearing areas */
    private final Color backGroundColor;
    /** JavaFX graphics context for drawing */
    private final GraphicsContext gc;
    /** Canvas width in pixels */
    private final int width;
    /** Canvas height in pixels */
    private final int height;
    /** Number of cells horizontally */
    private final int cellWidth;
    /** Number of cells vertically */
    private final int cellHeight;
    /** Writable image for efficient pixel manipulation */
    private final WritableImage estadosImage;
    /** Pixel writer for direct pixel access */
    private final PixelWriter pixelWriter;

    /** Flag to control whether sound is enabled during visualization */
    private final boolean soundEnabled;

    /** Flag indicating if continuous iteration is active */
    private boolean runningIteration = false;
    /** Flag indicating if single step mode is active */
    private boolean runningStep = false;
    /** Reusable buffer for pixel data to avoid allocations */
    private final byte[] blockBuffer = new byte[PIXEL_SIZE * PIXEL_SIZE * 4];

    /**
     * Creates a new Renderer for QuickSort visualization.
     * 
     * @param gc              the graphics context to draw on (must not be null)
     * @param w               canvas width in pixels (must be positive)
     * @param h               canvas height in pixels (must be positive)
     * @param barsColor       color for drawing the array bars
     * @param backGroundColor color for the background
     * @param soundEnabled    whether sound is enabled for audio feedback
     * @throws IllegalArgumentException if gc is null or dimensions are invalid
     */
    public Renderer(GraphicsContext gc, int w, int h, Color barsColor, Color backGroundColor, boolean soundEnabled) {
        if (gc == null) {
            throw new IllegalArgumentException("GraphicsContext cannot be null");
        }
        if (w <= 0 || h <= 0) {
            throw new IllegalArgumentException("Width and height must be positive");
        }

        this.defaultColor = barsColor;
        this.invertedDefaultColor = invertColor(barsColor);
        this.backGroundColor = backGroundColor;
        this.gc = gc;
        this.width = w;
        this.height = h;
        this.cellWidth = width / PIXEL_SIZE;
        this.cellHeight = height / PIXEL_SIZE;
        this.soundEnabled = soundEnabled;

        // Create writable image and pixel writer for efficient drawing
        estadosImage = new WritableImage(width, height);
        pixelWriter = estadosImage.getPixelWriter();

    }

    protected void copyRenderArrayOfValues(int[] ArrayOfValues) {
        for (int i = 0; i < ArrayOfValues.length; i++) {
            ArrayOfValues[i] = renderArrayOfValues[i];
        }
    }

    /**
     * Clears the entire canvas and resets the image.
     * Sets all pixels to transparent and fills the background.
     */
    public void clearImage() {
        pixelWriter.setPixels(0, 0, width, height, PixelFormat.getByteBgraInstance(), new byte[width * height * 4], 0,
                width * 4);
        gc.fillRect(0, 0, width, height);
        gc.drawImage(estadosImage, 0, 0);
        System.out.println("Image Drawn");
    }

    /**
     * Updates the visual representation by processing one animation frame.
     * Only processes if iteration or step mode is active.
     */
    public void updateRegion() {
        if (!runningIteration && !runningStep)
            return;
        iteration();
        runningStep = false;
        gc.drawImage(estadosImage, 0, 0);
    }

    /**
     * Processes one iteration of the animation queue.
     * Executes the current action and removes it when complete.
     */
    public void iteration() {
        if (actionQueue.isEmpty()) {
            return;
        }
        Action action = actionQueue.peek();
        boolean finished = false;
        switch (action.getInstruction()) {
            case SWAP:
                finished = swap(action);
                break;
            case PIVOT:
                finished = pivot(action);
                break;
            case COMPARE:
                finished = compare(action);
                break;
            default:
                break;
        }
        if (finished) {
            actionQueue.poll();
        }
    }

    /**
     * Animates a swap operation between two array elements.
     * The animation includes blinking effects and the actual value swap.
     * 
     * @param action the swap action containing the indices to swap
     * @return true if the animation is complete, false otherwise
     */
    private boolean swap(Action action) {
        // Calculate color based on current stage
        Color color = calculateStageColor(action.getTotalStages(), action.getStage(), defaultColor);
        
        // Perform the actual swap at the midpoint of animation
        if (action.getStage() == action.getTotalStages() / 2) {
            int temp = renderArrayOfValues[action.getParam1()];
            renderArrayOfValues[action.getParam1()] = renderArrayOfValues[action.getParam2()];
            renderArrayOfValues[action.getParam2()] = temp;
            // Play sound based on distance between elements
            int distance = Math.abs(action.getParam2() - action.getParam1());
            playSwapSound(distance);
            // Draw both elements that will be swapped
            clearRectangle(action.getParam1(), 0, 1, cellHeight - 1);
            clearRectangle(action.getParam2(), 0, 1, cellHeight - 1);
        }


        rectangleToScreen(action.getParam1(), (height / PIXEL_SIZE) -
                renderArrayOfValues[action.getParam1()] - 1, 1,
                renderArrayOfValues[action.getParam1()], true, true, color);


        rectangleToScreen(action.getParam2(), (height / PIXEL_SIZE) -
                renderArrayOfValues[action.getParam2()] - 1, 1,
                renderArrayOfValues[action.getParam2()], true, true, color);

        // System.out.println("Swapping " + action.getParam1() + " with " +
        // action.getParam2() + " stage:" + stage);
        return action.endStage();
    }


    private boolean compare(Action action) {

        // delete last triangles

        for (int a = 0; a < cellWidth; a++) {
            if (a != action.getParam1() && a != action.getParam2())
                triangleToScreen(a, cellHeight - 1, Color.LIGHTBLUE);
        }

        if (action.getStage() > 1) {
            triangleToScreen(action.getParam1(), cellHeight - 1, Color.DARKGREEN);
            triangleToScreen(action.getParam2(), cellHeight - 1, Color.BLUE);
        }
        return action.endStage();
    }

    /**
     * Animates the pivot highlighting during QuickSort partitioning.
     * Alternates between normal and inverted colors to create a flashing effect.
     * 
     * @param action the pivot action containing the pivot index
     * @return true if the animation is complete, false otherwise
     */
    private boolean pivot(Action action) {
        Color color;
        if (action.getStage() % 2 != 0) {
            color = defaultColor;
        } else
            color = invertedDefaultColor;

        rectangleToScreen(action.param1, (height / Renderer.PIXEL_SIZE) - renderArrayOfValues[action.param1] - 1, 1,
                renderArrayOfValues[action.param1], true, true, color);

        // System.out.println("Pivoting around " + action.getParam1() + " stage:" +
        // stage);
        return action.endStage();
    }

    /**
     * Clears a rectangular area by filling it with the background color.
     * 
     * @param x       horizontal position in cells
     * @param y       vertical position in cells
     * @param rwidth  width in cells
     * @param rheight height in cells
     */
    protected void clearRectangle(int x, int y, int rwidth, int rheight) {

        WritablePixelFormat<ByteBuffer> pixelFormat = PixelFormat.getByteBgraInstance();
        for (int cy = Math.max(0, y); cy < Math.min(cellHeight, y + rheight); cy++) {
            for (int cx = Math.max(0, x); cx < Math.min(cellWidth, x + rwidth); cx++) {
                int screenX = cx * PIXEL_SIZE;
                int screenY = cy * PIXEL_SIZE;

                for (int py = 0; py < PIXEL_SIZE; py++) {
                    for (int px = 0; px < PIXEL_SIZE; px++) {
                        int pos = (py * PIXEL_SIZE + px) * 4;

                        blockBuffer[pos] = (byte) (backGroundColor.getBlue() * 255); // Blue - inner color
                        blockBuffer[pos + 1] = (byte) (backGroundColor.getGreen() * 255); // Green
                        blockBuffer[pos + 2] = (byte) (backGroundColor.getRed() * 255); // Red
                        blockBuffer[pos + 3] = (byte) 255;
                    }
                }

                if (screenX + PIXEL_SIZE <= width && screenY + PIXEL_SIZE <= height) {
                    pixelWriter.setPixels(screenX, screenY, PIXEL_SIZE, PIXEL_SIZE, pixelFormat, blockBuffer, 0,
                            PIXEL_SIZE * 4);
                }
            }
        }
    }

    /**
     * Draws a small triangle marker within a PIXEL_SIZE cell.
     * Used to indicate elements being compared during sorting visualization.
     * The triangle is drawn as a pyramid shape pointing upward.
     * 
     * @param x     horizontal position in cells
     * @param y     vertical position in cells
     * @param color color of the triangle marker
     */
    protected void triangleToScreen(int x, int y, Color color) {
        if (x < 0 || x >= cellWidth || y < 0 || y >= cellHeight)
            return;

        int screenX = x * PIXEL_SIZE + PIXEL_SIZE / 2;
        int screenY = y * PIXEL_SIZE;

        int width = 0;
        for (int row = 1; row < PIXEL_SIZE / 2; row++) {
            width++;

            for (int col = -width; col <= width; col++) {

                pixelWriter.setColor(screenX + col, screenY + row, color);

            }
        }
    }

    /**
     * Draws a rectangle to the screen using the default color.
     * 
     * @param x        horizontal position in cells
     * @param y        vertical position in cells
     * @param rwidth   width in cells
     * @param rheight  height in cells
     * @param hasFrame whether to draw a frame around the rectangle
     * @param hasSpace whether to leave spacing around the rectangle
     */
    protected void rectangleToScreen(int x, int y, int rwidth, int rheight, boolean hasFrame, boolean hasSpace) {
        rectangleToScreen(x, y, rwidth, rheight, hasFrame, hasSpace, defaultColor);
    }

    /**
     * Draws a rectangle to the screen with specified color and styling options.
     * 
     * @param x        horizontal position in cells
     * @param y        vertical position in cells
     * @param rwidth   width in cells
     * @param rheight  height in cells
     * @param hasFrame whether to draw a frame around the rectangle
     * @param hasSpace whether to leave spacing around the rectangle
     * @param color    the color to use for drawing
     */
    protected void rectangleToScreen(int x, int y, int rwidth, int rheight, boolean hasFrame, boolean hasSpace,
            Color color) {
        WritablePixelFormat<ByteBuffer> pixelFormat = PixelFormat.getByteBgraInstance();

        for (int cy = Math.max(0, y); cy < Math.min(cellHeight, y + rheight); cy++) {
            for (int cx = Math.max(0, x); cx < Math.min(cellWidth, x + rwidth); cx++) {
                int screenX = cx * PIXEL_SIZE;
                int screenY = cy * PIXEL_SIZE;

                boolean leftBorder = (cx == x);
                boolean rightBorder = (cx == x + rwidth - 1);
                boolean topBorder = (cy == y);
                boolean bottomBorder = (cy == y + rheight - 1);

                // Clear the entire block first
                // The Alpha is 0 is not going to draw
                Arrays.fill(blockBuffer, (byte) 0);

                int startPx = (hasSpace && leftBorder) ? 1 : 0;
                // int endPx = (hasSpace && rightBorder) ? PIXEL_SIZE - 1 : PIXEL_SIZE;
                int endPx = PIXEL_SIZE;
                int startPy = 0;
                int endPy = (hasSpace && bottomBorder) ? PIXEL_SIZE - 1 : PIXEL_SIZE;

                for (int py = startPy; py < endPy; py++) {
                    for (int px = startPx; px < endPx; px++) {
                        int pos = (py * PIXEL_SIZE + px) * 4;

                        if (!(hasFrame &&
                                ((leftBorder && px == startPx) ||
                                        (rightBorder && px == endPx - 1) ||
                                        (topBorder && py == startPy) ||
                                        (bottomBorder && py == endPy - 1)))) {
                            blockBuffer[pos] = (byte) (color.getBlue() * 255); // Blue - inner color
                            blockBuffer[pos + 1] = (byte) (color.getGreen() * 255); // Green
                            blockBuffer[pos + 2] = (byte) (color.getRed() * 255); // Red
                        }
                        blockBuffer[pos + 3] = (byte) 255;
                    }
                }

                if (screenX + PIXEL_SIZE <= width && screenY + PIXEL_SIZE <= height) {
                    pixelWriter.setPixels(screenX, screenY, PIXEL_SIZE, PIXEL_SIZE,
                            pixelFormat, blockBuffer, 0, PIXEL_SIZE * 4);
                }
            }
        }
    }

    /**
     * Restarts the visualization by stopping any ongoing animation.
     * Resets the animation state to allow for a fresh start.
     */
    public void restart() {

        actionQueue.clear();
        runningIteration = false;
    }

    /**
     * Activates continuous iteration mode.
     * When active, the visualization will continuously process animation actions.
     */
    public void iterate() {
        runningIteration = true;
    }

    /**
     * Stops the continuous iteration mode.
     * When stopped, the visualization will pause and wait for manual control.
     */
    public void stopIteration() {
        runningIteration = false;
    }

    /**
     * Performs a single animation step.
     * Useful for debugging or educational purposes to observe each operation
     * individually.
     */
    public void step() {
        runningStep = true;
    }

    /**
     * Creates an inverted version of the given color.
     * 
     * @param color the original color
     * @return a new color with inverted RGB values
     */
    private Color invertColor(Color color) {
        return new Color(1 - color.getRed(), 1 - color.getGreen(), 1 - color.getBlue(), color.getOpacity());
    }

    /**
     * Creates a lighter version of the given color by blending with white.
     * 
     * @param color the original color
     * @return a new color that is 50% lighter
     */
    private Color lightColor(Color color) {
        double red = color.getRed() + (1 - color.getRed()) / 2;
        double green = color.getGreen() + (1 - color.getGreen()) / 2;
        double blue = color.getBlue() + (1 - color.getBlue()) / 2;

        return new Color(red, green, blue, 1);
    }

    /**
     * Creates a darker version of the given color by blending with white.
     * 
     * @param color the original color
     * @return a new color that is 50% lighter
     */
    private Color darkColor(Color color) {
        double red = color.getRed() / 2;
        double green = color.getGreen() / 2;
        double blue = color.getBlue() / 2;

        return new Color(red, green, blue, 1);
    }

    /**
     * Calculates the color for a specific stage of animation based on total stages.
     * Creates a pulse effect that darkens and then returns to normal.
     * 
     * @param totalStages the total number of stages in the animation
     * @param currentStage the current stage of the animation
     * @param baseColor the base color to modify
     * @return the calculated color for this stage
     */
    private Color calculateStageColor(int totalStages, int currentStage, Color baseColor) {
        // At the beginning or end of animation, return the default color
        if (currentStage == totalStages || currentStage == 1) {
            return baseColor;
        }
        
        // Calculate the progress of the animation (0 to 1)
        double progress = (double)(totalStages - currentStage) / totalStages;
        
        // Create a smooth transition that goes from 0 to 1 and back to 0
        // This creates a pulse effect that darkens and then returns to normal
        double pulseProgress;
        if (progress <= 0.5) {
            // First half: go from 0 to 1 (darken)
            pulseProgress = progress * 2;
        } else {
            // Second half: go from 1 to 0 (lighten)
            pulseProgress = (1 - progress) * 2;
        }
        
        // Calculate color adjustment - darken bars during animation
        double darkenFactor = 0.6 * pulseProgress; // Maximum 70% darkening
        return new Color(
            Math.max(0, baseColor.getRed() * (1 - darkenFactor)),
            Math.max(0, baseColor.getGreen() * (1 - darkenFactor)),
            Math.max(0, baseColor.getBlue() * (1 - darkenFactor)),
            baseColor.getOpacity()
        );
    }

    /**
     * Animates a comparison operation between two array elements.
     * Displays triangular markers below the elements being compared during sorting
     * algorithms.
     * Triangles persist until explicitly cleared by other operations.
     *
     * @param action the compare action containing the indices being compared
     * @return true if the animation is complete, false otherwise
     */

    /**
     * Plays a sound based on the distance between swapped elements.
     * Lower frequency (bass) for nearby elements, higher frequency for distant
     * elements.
     * The sound is only played if the sound feature is enabled in the main
     * application.
     *
     * @param distance the distance between the swapped elements
     */
    private void playSwapSound(int distance) {
        if (!soundEnabled) {
            return;
        }
        // Calculate frequency based on distance
        // Bass sound (low frequency) for nearby elements, high pitch for distant
        // elements
        // Frequency range from 200Hz (nearby) to 800Hz (distant)
        int maxDistance = renderArrayOfValues != null ? renderArrayOfValues.length : 50;
        int frequency = 200 + (600 * Math.min(distance, maxDistance) / Math.max(maxDistance, 1));

        // Generate and play the sound in a separate thread to avoid blocking the UI
        new Thread(() -> generateTone(frequency, 75)).start();
    }

    /**
     * Generates and plays a tone with the specified frequency and duration.
     * Implements smooth start and end to avoid clicking sounds.
     *
     * @param frequency the frequency of the tone in Hz
     * @param duration  the duration of the tone in milliseconds
     */
    private void generateTone(int frequency, int duration) {
        try {
            // Audio format: 8kHz sample rate, 8-bit samples, mono, signed, little-endian
            AudioFormat format = new AudioFormat(8000f, 8, 1, true, false);
            int bufferSize = (int) (duration * 8);
            byte[] buffer = new byte[bufferSize];

            // Generate sine wave samples with smooth start and end to avoid clicks
            for (int i = 0; i < buffer.length; i++) {
                double angle = i / (8000f / frequency) * 2.0 * Math.PI;

                // Apply an envelope to reduce volume and avoid clicks at start/end
                double envelope;
                int fadeLength = Math.min(bufferSize / 7, 20); // 20 samples or 1/7th of buffer for fade

                if (i < fadeLength) {
                    // Fade in
                    envelope = (double) i / fadeLength;
                } else if (i > buffer.length - fadeLength) {
                    // Fade out
                    envelope = (double) (buffer.length - i) / fadeLength;
                } else {
                    // Full volume in the middle
                    envelope = 1.0;
                }

                // Reduce overall volume to make it less loud
                buffer[i] = (byte) (Math.sin(angle) * 60 * envelope); // Reduced from 127 to 60
            }

            // Play the sound
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
            SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info);
            line.open(format);
            line.start();
            line.write(buffer, 0, buffer.length);
            line.drain();
            line.close();
        } catch (LineUnavailableException e) {
            // If audio is not available, silently continue without sound
            System.err.println("Audio not available: " + e.getMessage());
        }
    }

}
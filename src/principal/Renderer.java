package principal;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.image.WritablePixelFormat;
import javafx.scene.paint.Color;

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * JavaFX drawing surface for the sorting visualization.
 * <p>
 * Drawing only: no action queue, no playback state and no audio (those belong
 * to the {@link Engine}). Bars are composed into an off-screen image through
 * direct pixel writes for efficiency, and {@link #present()} blits that image
 * onto the canvas. All geometry (cell size, margins, baseline) is encapsulated
 * here; callers only deal with bar indices and values.
 * </p>
 */
class Renderer implements SortView {

    /** Minimum allowed cell size in pixels. */
    public static final int MIN_PIXEL_SIZE = 3;
    /** Horizontal margin, in cells, left empty on each side of the array. */
    private static final int SIDE_MARGIN = 2;
    /** Vertical margin, in cells, kept above the tallest bar. */
    private static final int TOP_MARGIN = 10;

    private static final Color BASELINE_COLOR = Color.LIGHTBLUE;
    /** Persistent highlight color of the pivot bar. */
    private static final Color PIVOT_COLOR = Color.ORANGE;
    /** Marker color of the first compared element. */
    private static final Color FIRST_COMPARED_COLOR = Color.DARKGREEN;
    private static final Color SECOND_COMPARED_COLOR = Color.BLUE;

    private final GraphicsContext gc;
    private final int width;
    private final int height;
    /** Size of each cell in pixels. */
    private final int pixelSize;
    private final int cellWidth;
    private final int cellHeight;
    private final Color barColor;
    private final Color backgroundColor;
    /** Off-screen image where bars are composed before being presented. */
    private final WritableImage backBuffer;
    private final PixelWriter pixelWriter;
    /** Reusable buffer for one cell of pixel data, to avoid allocations. */
    private final byte[] blockBuffer;

    /**
     * Creates a new drawing surface.
     *
     * @param gc              the graphics context to draw on (must not be null)
     * @param width           canvas width in pixels (must be positive)
     * @param height          canvas height in pixels (must be positive)
     * @param pixelSize       size of each cell in pixels (at least {@link #MIN_PIXEL_SIZE})
     * @param barColor        color for drawing the array bars
     * @param backgroundColor color for the background
     * @throws IllegalArgumentException if gc is null or dimensions are invalid
     */
    public Renderer(GraphicsContext gc, int width, int height, int pixelSize, Color barColor, Color backgroundColor) {
        if (gc == null) {
            throw new IllegalArgumentException("GraphicsContext cannot be null");
        }
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Width and height must be positive");
        }
        if (pixelSize < MIN_PIXEL_SIZE) {
            throw new IllegalArgumentException("Cell size must be at least " + MIN_PIXEL_SIZE + " pixels");
        }

        this.gc = gc;
        this.width = width;
        this.height = height;
        this.pixelSize = pixelSize;
        this.cellWidth = width / pixelSize;
        this.cellHeight = height / pixelSize;
        this.barColor = barColor;
        this.backgroundColor = backgroundColor;

        this.backBuffer = new WritableImage(width, height);
        this.pixelWriter = backBuffer.getPixelWriter();
        this.blockBuffer = new byte[pixelSize * pixelSize * 4];
    }

    /** @return how many bars fit on screen, excluding the side margins */
    public int barCount() {
        return cellWidth - SIDE_MARGIN;
    }

    /** @return the maximum bar value that fits below the top margin */
    public int maxBarValue() {
        return cellHeight - TOP_MARGIN;
    }

    @Override
    public void clear() {
        pixelWriter.setPixels(0, 0, width, height, PixelFormat.getByteBgraInstance(),
                new byte[width * height * 4], 0, width * 4);
        gc.setFill(backgroundColor);
        gc.fillRect(0, 0, width, height);
        present();
    }

    @Override
    public void drawBars(int[] values) {
        for (int i = 0; i < values.length; i++) {
            drawBar(i, values[i], 0);
        }
        drawBaseline();
    }

    @Override
    public void drawBar(int index, int value, double darkness) {
        clearColumn(index);
        drawBarShape(index, value, darken(barColor, darkness));
    }

    @Override
    public void drawPivotBar(int index, int value, boolean inverted) {
        clearColumn(index);
        drawBarShape(index, value, inverted ? PIVOT_COLOR.darker() : PIVOT_COLOR);
    }

    @Override
    public void drawComparedBar(int index, int value, boolean first) {
        clearColumn(index);
        drawBarShape(index, value, first ? FIRST_COMPARED_COLOR : SECOND_COMPARED_COLOR);
    }

    @Override
    public void showComparison(int i, int j) {
        // Redrawing the baseline erases any previous markers
        drawBaseline();
        drawTriangleMarker(i, cellHeight - 1, FIRST_COMPARED_COLOR);
        drawTriangleMarker(j, cellHeight - 1, SECOND_COMPARED_COLOR);
    }

    @Override
    public void clearComparison() {
        drawBaseline();
    }

    @Override
    public void present() {
        gc.drawImage(backBuffer, 0, 0);
    }

    /** Draws a framed bar of the given value sitting on the baseline. */
    private void drawBarShape(int index, int value, Color color) {
        fillCells(index, cellHeight - value - 1, 1, value, color);
    }

    /** Clears a bar column, preserving the baseline row. */
    private void clearColumn(int index) {
        clearCells(index, 0, 1, cellHeight - 1);
    }

    /** Draws the horizontal baseline on which all bars sit. */
    private void drawBaseline() {
        fillCells(0, cellHeight - 1, cellWidth, 1, BASELINE_COLOR);
    }

    /**
     * Fills a region of cells with a framed, spaced rectangle of the given
     * color. Coordinates and sizes are in cells, not pixels.
     */
    private void fillCells(int x, int y, int regionWidth, int regionHeight, Color color) {
        WritablePixelFormat<ByteBuffer> pixelFormat = PixelFormat.getByteBgraInstance();

        for (int cy = Math.max(0, y); cy < Math.min(cellHeight, y + regionHeight); cy++) {
            for (int cx = Math.max(0, x); cx < Math.min(cellWidth, x + regionWidth); cx++) {
                int screenX = cx * pixelSize;
                int screenY = cy * pixelSize;

                boolean leftBorder = (cx == x);
                boolean rightBorder = (cx == x + regionWidth - 1);
                boolean topBorder = (cy == y);
                boolean bottomBorder = (cy == y + regionHeight - 1);

                // Transparent buffer: pixels left at alpha 0 are not painted
                Arrays.fill(blockBuffer, (byte) 0);

                // Leave a one-pixel gap on the left and bottom for spacing
                int startPx = leftBorder ? 1 : 0;
                int endPx = pixelSize;
                int startPy = 0;
                int endPy = bottomBorder ? pixelSize - 1 : pixelSize;

                for (int py = startPy; py < endPy; py++) {
                    for (int px = startPx; px < endPx; px++) {
                        int pos = (py * pixelSize + px) * 4;

                        boolean isFrame = (leftBorder && px == startPx)
                                || (rightBorder && px == endPx - 1)
                                || (topBorder && py == startPy)
                                || (bottomBorder && py == endPy - 1);
                        if (!isFrame) {
                            blockBuffer[pos] = (byte) (color.getBlue() * 255);
                            blockBuffer[pos + 1] = (byte) (color.getGreen() * 255);
                            blockBuffer[pos + 2] = (byte) (color.getRed() * 255);
                        }
                        blockBuffer[pos + 3] = (byte) 255;
                    }
                }

                if (screenX + pixelSize <= width && screenY + pixelSize <= height) {
                    pixelWriter.setPixels(screenX, screenY, pixelSize, pixelSize,
                            pixelFormat, blockBuffer, 0, pixelSize * 4);
                }
            }
        }
    }

    /** Fills a region of cells with the background color. */
    private void clearCells(int x, int y, int regionWidth, int regionHeight) {
        WritablePixelFormat<ByteBuffer> pixelFormat = PixelFormat.getByteBgraInstance();

        Arrays.fill(blockBuffer, (byte) 255);
        for (int pos = 0; pos < blockBuffer.length; pos += 4) {
            blockBuffer[pos] = (byte) (backgroundColor.getBlue() * 255);
            blockBuffer[pos + 1] = (byte) (backgroundColor.getGreen() * 255);
            blockBuffer[pos + 2] = (byte) (backgroundColor.getRed() * 255);
            blockBuffer[pos + 3] = (byte) 255;
        }

        for (int cy = Math.max(0, y); cy < Math.min(cellHeight, y + regionHeight); cy++) {
            for (int cx = Math.max(0, x); cx < Math.min(cellWidth, x + regionWidth); cx++) {
                int screenX = cx * pixelSize;
                int screenY = cy * pixelSize;
                if (screenX + pixelSize <= width && screenY + pixelSize <= height) {
                    pixelWriter.setPixels(screenX, screenY, pixelSize, pixelSize,
                            pixelFormat, blockBuffer, 0, pixelSize * 4);
                }
            }
        }
    }

    /**
     * Draws a small upward-pointing triangle marker inside a cell, used to
     * indicate elements being compared.
     */
    private void drawTriangleMarker(int x, int y, Color color) {
        if (x < 0 || x >= cellWidth || y < 0 || y >= cellHeight) {
            return;
        }

        int screenX = x * pixelSize + pixelSize / 2;
        int screenY = y * pixelSize;

        int halfWidth = 0;
        int rows = Math.max(1, pixelSize / 2 - 1);
        for (int row = 1; row <= rows; row++) {
            halfWidth++;
            for (int col = -halfWidth; col <= halfWidth; col++) {
                pixelWriter.setColor(screenX + col, screenY + row, color);
            }
        }
    }

    /** @return the color darkened by the given factor in [0, 1] */
    private static Color darken(Color color, double factor) {
        if (factor <= 0) {
            return color;
        }
        double scale = 1 - factor;
        return new Color(color.getRed() * scale, color.getGreen() * scale, color.getBlue() * scale,
                color.getOpacity());
    }
}
package principal;

/**
 * Drawing contract used by the {@link Engine}.
 * <p>
 * Implementations translate semantic drawing requests (bars, pivots,
 * comparison markers) into pixels. The engine never touches JavaFX types,
 * so it stays independent of the UI toolkit.
 * </p>
 */
public interface SortView {

    /** Clears the whole drawing surface. */
    void clear();

    /** Draws the complete array as bars, including the baseline. */
    void drawBars(int[] values);

    /**
     * Redraws a single bar, clearing its column first.
     *
     * @param index    bar position
     * @param value    bar height in cells
     * @param darkness darkening factor in {@code [0, 1]}, used for the swap
     *                 pulse effect; {@code 0} means the normal bar color
     */
    void drawBar(int index, int value, double darkness);

    /**
     * Redraws a single bar with the pivot highlight, which persists until the
     * bar is redrawn normally.
     *
     * @param index    bar position
     * @param value    bar height in cells
     * @param inverted darker variant of the highlight, used for the selection
     *                 flash
     */
    void drawPivotBar(int index, int value, boolean inverted);

    /**
     * Redraws a single bar tinted as one of the two elements being compared.
     *
     * @param index bar position
     * @param value bar height in cells
     * @param first true for the first compared element, false for the second
     */
    void drawComparedBar(int index, int value, boolean first);

    /** Shows comparison markers under the two given bars. */
    void showComparison(int i, int j);

    /** Removes any comparison markers. */
    void clearComparison();

    /** Paints the composed frame onto the screen. */
    void present();
}
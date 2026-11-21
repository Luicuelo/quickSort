package principal;

/**
 * Observer interface through which sorting algorithms report their operations.
 * <p>
 * Algorithms depend only on this interface, never on the UI or the animation
 * engine. This keeps them pure, reusable and unit-testable without JavaFX.
 * </p>
 */
public interface SortListener {

    /**
     * Reports that the elements at indices {@code i} and {@code j} were swapped.
     */
    void onSwap(int i, int j);

    /**
     * Reports a comparison between the elements at indices {@code i} and {@code j}.
     */
    void onCompare(int i, int j);

    /**
     * Reports that the element at {@code index} is acting as pivot.
     */
    void onPivot(int index);

    /**
     * Reports the subarray {@code [low, high]} the algorithm is about to work
     * on. Elements outside this range are inactive (already placed or not yet
     * reached) and may be shown dimmed.
     */
    void onRange(int low, int high);

    /**
     * Reports that the range {@code [start, end]} was rewritten without swaps
     * (for example, the merge step of MergeSort).
     *
     * @param start    first index of the rewritten range
     * @param end      last index of the rewritten range (inclusive)
     * @param snapshot the full array state right after the rewrite; the
     *                 receiver must copy it if it needs to keep it, because the
     *                 algorithm keeps mutating its array afterwards
     */
    void onRangeRefresh(int start, int end, int[] snapshot);
}
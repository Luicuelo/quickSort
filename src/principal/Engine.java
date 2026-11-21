package principal;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

/**
 * Animation engine: owns the queue of sorting actions, the playback modes
 * (iterate / step / stop) and the audio feedback.
 * <p>
 * The engine is plain Java: it talks to the drawing surface through
 * {@link SortView} and to the audio through {@link Sound}, so it has no
 * dependency on JavaFX or on the Java Sound API. It also acts as the
 * {@link SortListener} of the running algorithm, translating reported
 * operations into queued {@link Action}s.
 * </p>
 * <p>
 * Key invariant: the displayed array ({@code displayValues}) is only mutated
 * while playing back queued actions, never while enqueuing them. This keeps
 * the visualization consistent with the algorithm's step order, even for
 * algorithms like MergeSort that rewrite ranges without swaps.
 * </p>
 */
public class Engine implements SortListener {

    /** Animation stages per action type: playback policy, not algorithm data. */
    private static final int SWAP_STAGES = 10;
    private static final int PIVOT_STAGES = 4;
    /** Comparisons hold their markers for several frames so they can be read. */
    private static final int COMPARE_STAGES = 6;
    /** Short beat so a newly selected subarray is visible before it is worked on. */
    private static final int RANGE_STAGES = 2;
    /** Darkening applied to bars outside the active subarray. */
    private static final double OUT_OF_RANGE_DARKNESS = 0.55;
    /** Maximum darkening applied during the swap pulse effect. */
    private static final double MAX_DARKEN_FACTOR = 0.6;

    /**
     * A single sorting operation to be animated. Pure, immutable data: each
     * variant carries exactly the parameters it needs, nothing more.
     */
    public sealed interface Action {

        /** Exchange of the elements at indices {@code i} and {@code j}. */
        record Swap(int i, int j) implements Action {
        }

        /** Comparison between the elements at indices {@code i} and {@code j}. */
        record Compare(int i, int j) implements Action {
        }

        /** Highlight of the pivot element at {@code index}. */
        record Pivot(int index) implements Action {
        }

        /** Activation of the subarray {@code [low, high]} the algorithm works on. */
        record Range(int low, int high) implements Action {
        }

        /**
         * Progressive redraw of the range {@code [start, end]} after a rewrite
         * without swaps. The snapshot is captured at enqueue time and never
         * exposes its internal array.
         */
        record RefreshRange(int start, int end, int[] snapshot) implements Action {

            public RefreshRange {
                // Defensive copy: the algorithm keeps mutating its array afterwards
                snapshot = snapshot.clone();
            }

            /** @return the snapshot value at {@code index} without exposing the array */
            public int valueAt(int index) {
                return snapshot[index];
            }

            @Override
            public int[] snapshot() {
                return snapshot.clone();
            }
        }
    }

    /** Queue entry: an action plus its remaining animation stages. */
    private static final class Playback {
        final Action action;
        final int totalStages;
        int stagesLeft;

        Playback(Action action, int totalStages) {
            this.action = action;
            this.totalStages = totalStages;
            this.stagesLeft = totalStages;
        }
    }

    private final SortView view;
    private final Sound sound;
    private final Deque<Playback> queue = new ArrayDeque<>();

    /** The array currently shown on screen; mutated only during playback. */
    private int[] displayValues;
    /** Bar currently marked as pivot; stays highlighted until the next pivot. */
    private int pivotIndex = -1;
    /** Subarray currently being worked on; bars outside it are shown dimmed. */
    private int activeLow;
    private int activeHigh;
    /** Bars currently tinted by the last comparison; kept steady across
     * consecutive comparisons so shared bars never flicker. */
    private int comparedFirst = -1;
    private int comparedSecond = -1;
    /** Run policy: when false, comparisons show baseline markers only. */
    private boolean tintComparisons = true;
    private boolean running = false;
    private boolean stepPending = false;

    public Engine(SortView view, Sound sound) {
        this.view = Objects.requireNonNull(view, "view cannot be null");
        this.sound = Objects.requireNonNull(sound, "sound cannot be null");
    }

    /**
     * Discards any pending animation and prepares a fresh run for the given
     * array. The engine keeps its own copy; the caller's array is not retained.
     *
     * @param tintComparisons whether comparisons tint the compared bars or
     *                        only show baseline markers
     */
    public void prepare(int[] values, boolean tintComparisons) {
        queue.clear();
        running = false;
        stepPending = false;
        pivotIndex = -1;
        comparedFirst = -1;
        comparedSecond = -1;
        this.tintComparisons = tintComparisons;
        displayValues = values.clone();
        activeLow = 0;
        activeHigh = values.length - 1;
        view.clear();
        view.drawBars(displayValues);
        view.present();
    }

    // ---- SortListener: algorithms report here; only enqueuing happens ----

    @Override
    public void onSwap(int i, int j) {
        enqueue(new Action.Swap(i, j), SWAP_STAGES);
    }

    @Override
    public void onCompare(int i, int j) {
        enqueue(new Action.Compare(i, j), COMPARE_STAGES);
    }

    @Override
    public void onPivot(int index) {
        enqueue(new Action.Pivot(index), PIVOT_STAGES);
    }

    @Override
    public void onRange(int low, int high) {
        enqueue(new Action.Range(low, high), RANGE_STAGES);
    }

    @Override
    public void onRangeRefresh(int start, int end, int[] snapshot) {
        enqueue(new Action.RefreshRange(start, end, snapshot), Math.max(1, end - start + 1));
    }

    private void enqueue(Action action, int totalStages) {
        queue.addLast(new Playback(action, totalStages));
    }

    // ---- Playback modes ----

    /** Activates continuous playback until {@link #stopIteration()}. */
    public void iterate() {
        running = true;
    }

    /** Pauses continuous playback; the queue is preserved. */
    public void stopIteration() {
        running = false;
    }

    /** Requests a single animation stage, useful for step-by-step study. */
    public void step() {
        stepPending = true;
    }

    /**
     * Advances the animation by one stage if a playback mode is active.
     * Called once per frame by the application's animation loop.
     */
    public void tick() {
        if (!running && !stepPending) {
            return;
        }
        stepPending = false;
        Playback playback = queue.peekFirst();
        if (playback == null || displayValues == null) {
            return;
        }
        if (playStage(playback)) {
            queue.pollFirst();
            if (queue.isEmpty()) {
                finishRun();
            }
        }
        view.present();
    }

    /** Restores the normal appearance once the whole run has been played back. */
    private void finishRun() {
        pivotIndex = -1;
        activeLow = 0;
        activeHigh = displayValues.length - 1;
        for (int i = 0; i < displayValues.length; i++) {
            redrawBar(i);
        }
        clearComparisonTints();
    }

    /** Restores the bars tinted by the last comparison and clears the markers. */
    private void clearComparisonTints() {
        if (comparedFirst >= 0) {
            redrawBar(comparedFirst);
            comparedFirst = -1;
        }
        if (comparedSecond >= 0) {
            redrawBar(comparedSecond);
            comparedSecond = -1;
        }
        view.clearComparison();
    }

    /**
     * Redraws a bar with its current semantic appearance: pivot highlight,
     * dimmed if outside the active subarray, normal otherwise.
     */
    private void redrawBar(int index) {
        if (index == pivotIndex) {
            view.drawPivotBar(index, displayValues[index], false);
        } else {
            boolean outside = index < activeLow || index > activeHigh;
            view.drawBar(index, displayValues[index], outside ? OUT_OF_RANGE_DARKNESS : 0);
        }
    }

    /**
     * Plays one stage of the given action.
     *
     * @return true when the action has no stages left
     */
    private boolean playStage(Playback playback) {
        Action action = playback.action;
        if (action instanceof Action.Swap swap) {
            playSwap(swap, playback);
        } else if (action instanceof Action.Compare compare) {
            playCompare(compare, playback);
        } else if (action instanceof Action.Pivot pivot) {
            playPivot(pivot, playback);
        } else if (action instanceof Action.Range range) {
            playRange(range, playback);
        } else if (action instanceof Action.RefreshRange refresh) {
            playRefreshRange(refresh, playback);
        }
        playback.stagesLeft--;
        return playback.stagesLeft <= 0;
    }

    /** Animates a swap with a darkening pulse; the visible exchange happens
     * at the midpoint of the animation. */
    private void playSwap(Action.Swap swap, Playback playback) {
        int stage = playback.stagesLeft;
        if (stage == playback.totalStages) {
            clearComparisonTints();
        }
        if (stage == playback.totalStages / 2) {
            int temp = displayValues[swap.i()];
            displayValues[swap.i()] = displayValues[swap.j()];
            displayValues[swap.j()] = temp;
            // The pivot marker follows its bar when the pivot itself is swapped
            if (pivotIndex == swap.i()) {
                pivotIndex = swap.j();
            } else if (pivotIndex == swap.j()) {
                pivotIndex = swap.i();
            }
            sound.playTone(normalizedPitch(Math.abs(swap.j() - swap.i())));
        }
        double darkness = pulseDarkness(playback.totalStages, stage);
        view.drawBar(swap.i(), displayValues[swap.i()], darkness);
        view.drawBar(swap.j(), displayValues[swap.j()], darkness);
        // Restore the semantic appearance (pivot highlight / range) at the end
        if (stage == 1) {
            redrawBar(swap.i());
            redrawBar(swap.j());
        }
    }

    /** Shows the comparison markers and, when the run policy asks for it,
     * tints the compared bars, keeping shared bars steady across consecutive
     * comparisons to avoid flicker; the pivot keeps its orange highlight. */
    private void playCompare(Action.Compare compare, Playback playback) {
        if (playback.stagesLeft != playback.totalStages) {
            return;
        }
        int first = compare.i();
        int second = compare.j();
        view.showComparison(first, second);
        if (!tintComparisons) {
            return; // markers only: bars keep their normal appearance
        }
        // Restore only the bars that are no longer part of the comparison
        if (comparedFirst >= 0 && comparedFirst != first && comparedFirst != second) {
            redrawBar(comparedFirst);
        }
        if (comparedSecond >= 0 && comparedSecond != first && comparedSecond != second) {
            redrawBar(comparedSecond);
        }
        comparedFirst = first;
        comparedSecond = second;
        if (first != pivotIndex) {
            view.drawComparedBar(first, displayValues[first], true);
        }
        if (second != pivotIndex) {
            view.drawComparedBar(second, displayValues[second], false);
        }
    }

    /** Marks the new pivot with a brief flash, then a persistent highlight;
     * the previous pivot bar is restored to its normal appearance. */
    private void playPivot(Action.Pivot pivot, Playback playback) {
        if (playback.stagesLeft == playback.totalStages) {
            clearComparisonTints();
        }
        if (playback.stagesLeft == playback.totalStages && pivotIndex >= 0 && pivotIndex != pivot.index()) {
            int previous = pivotIndex;
            pivotIndex = -1;
            redrawBar(previous);
        }
        pivotIndex = pivot.index();
        boolean flash = playback.stagesLeft % 2 == 0;
        view.drawPivotBar(pivot.index(), displayValues[pivot.index()], flash);
    }

    /** Highlights the subarray the algorithm is about to work on: bars inside
     * keep their normal color, bars outside are dimmed. */
    private void playRange(Action.Range range, Playback playback) {
        if (playback.stagesLeft != playback.totalStages) {
            return;
        }
        activeLow = range.low();
        activeHigh = range.high();
        for (int i = 0; i < displayValues.length; i++) {
            redrawBar(i);
        }
        clearComparisonTints();
    }

    /** Redraws one bar per stage from the snapshot taken after the rewrite. */
    private void playRefreshRange(Action.RefreshRange refresh, Playback playback) {
        if (playback.stagesLeft == playback.totalStages) {
            clearComparisonTints();
        }
        int index = refresh.start() + (playback.totalStages - playback.stagesLeft);
        if (index <= refresh.end()) {
            displayValues[index] = refresh.valueAt(index);
            view.drawBar(index, displayValues[index], 0);
            if (index == pivotIndex) {
                // The rewrite may have replaced the pivot value at this position
                pivotIndex = -1;
            }
            if (refresh.end() - refresh.start() > 2) {
                sound.playTone(normalizedPitch(index - refresh.start()));
            }
        }
    }

    /** Maps a distance to a pitch in [0, 1]: nearby elements sound bass,
     * distant elements sound high. */
    private double normalizedPitch(int distance) {
        return distance / (double) Math.max(1, displayValues.length);
    }

    /**
     * Darkening factor for the swap pulse: 0 at the first and last stage,
     * rising smoothly to {@link #MAX_DARKEN_FACTOR} at the midpoint.
     */
    private static double pulseDarkness(int totalStages, int stage) {
        if (stage == totalStages || stage == 1) {
            return 0;
        }
        double progress = (double) (totalStages - stage) / totalStages;
        double pulse = progress <= 0.5 ? progress * 2 : (1 - progress) * 2;
        return MAX_DARKEN_FACTOR * pulse;
    }
}
package principal;

import java.util.List;

/**
 * Strategy interface implemented by every sorting algorithm.
 * <p>
 * Implementations are pure: they only manipulate the received array and report
 * their operations through a {@link SortListener}. They have no dependency on
 * JavaFX, on the animation engine or on any static state, so they can be unit
 * tested in isolation. Adding a new algorithm only requires a new class here
 * and registering it in {@link #all()}.
 * </p>
 */
public interface SortAlgorithm {

    /**
     * Sorts {@code values} in place in ascending order, reporting every
     * operation to {@code listener} as it happens.
     *
     * @param values   the array to sort
     * @param listener receiver of the reported operations
     */
    void sort(int[] values, SortListener listener);

    /**
     * Whether comparisons should tint the compared bars. Algorithms whose
     * comparisons are fast and local (adjacent swaps, merging heads) read
     * better with baseline markers only; the default is to tint.
     */
    default boolean tintsComparisons() {
        return true;
    }

    /**
     * Swaps two elements and reports the operation to the listener.
     * Shared helper so every implementation reports swaps consistently.
     */
    static void swap(int[] values, int i, int j, SortListener listener) {
        int temp = values[i];
        values[i] = values[j];
        values[j] = temp;
        listener.onSwap(i, j);
    }

    /**
     * @return one instance of every available algorithm, in display order
     */
    static List<SortAlgorithm> all() {
        return List.of(
                new QuickSortAlgorithm(),
                new BubbleSortAlgorithm(),
                new SelectionSortAlgorithm(),
                new InsertionSortAlgorithm(),
                new MergeSortAlgorithm());
    }
}

/**
 * QuickSort with the last element as pivot and a two-pointer (Hoare-style)
 * partitioning scheme.
 * <p>
 * Time complexity: O(n log n) average, O(n²) worst case.
 * Space complexity: O(log n) for the recursion stack.
 * </p>
 */
class QuickSortAlgorithm implements SortAlgorithm {

    @Override
    public void sort(int[] values, SortListener listener) {
        quickSort(values, 0, values.length - 1, listener);
    }

    private static void quickSort(int[] values, int low, int high, SortListener listener) {
        // Base case: a subarray of 0 or 1 elements is already sorted
        if (high <= low) {
            return;
        }

        listener.onRange(low, high);
        int pivotIndex = high;
        int pivotValue = values[pivotIndex];
        listener.onPivot(pivotIndex);

        int i = low;        // left pointer
        int j = high - 1;   // right pointer (excluding the pivot)

        // Partition: elements <= pivot to the left, elements > pivot to the right
        while (i <= j) {
            // Advance the left pointer while elements belong on the left side
            while (i <= j) {
                listener.onCompare(i, pivotIndex);
                if (values[i] > pivotValue) {
                    break;
                }
                i++;
            }
            // Move the right pointer back while elements belong on the right side
            while (i <= j) {
                listener.onCompare(j, pivotIndex);
                if (values[j] <= pivotValue) {
                    break;
                }
                j--;
            }
            // Both pointers stopped on misplaced elements: exchange them
            if (i < j) {
                SortAlgorithm.swap(values, i, j, listener);
                i++;
                j--;
            }
        }

        // Place the pivot between the two partitions (only if necessary)
        if (i != pivotIndex && values[i] > pivotValue) {
            SortAlgorithm.swap(values, i, pivotIndex, listener);
        }

        quickSort(values, low, i - 1, listener);
        quickSort(values, i + 1, high, listener);
    }

    @Override
    public String toString() {
        return "QuickSort";
    }
}

/**
 * BubbleSort with early exit: repeatedly compares adjacent elements and swaps
 * them if they are in the wrong order; stops as soon as a full pass makes no
 * swaps.
 * <p>
 * Time complexity: O(n²), O(n) on an already sorted array.
 * Space complexity: O(1).
 * </p>
 */
class BubbleSortAlgorithm implements SortAlgorithm {

    @Override
    public void sort(int[] values, SortListener listener) {
        // After each pass, the element at position i is in its final place
        for (int i = values.length - 1; i > 0; i--) {
            boolean swapped = false;
            listener.onRange(0, i);
            for (int j = 0; j < i; j++) {
                listener.onCompare(j, j + 1);
                if (values[j] > values[j + 1]) {
                    SortAlgorithm.swap(values, j, j + 1, listener);
                    swapped = true;
                }
            }
            // No swaps in a full pass means the array is already sorted
            if (!swapped) {
                break;
            }
        }
    }

    @Override
    public boolean tintsComparisons() {
        return false; // fast adjacent comparisons: markers only, less visual noise
    }

    @Override
    public String toString() {
        return "BubbleSort";
    }
}

/**
 * SelectionSort: repeatedly selects the minimum of the unsorted suffix and
 * swaps it into its final position at the front.
 * <p>
 * Time complexity: O(n²).
 * Space complexity: O(1).
 * </p>
 */
class SelectionSortAlgorithm implements SortAlgorithm {

    @Override
    public void sort(int[] values, SortListener listener) {
        // One by one, move the boundary of the unsorted suffix
        for (int i = 0; i < values.length - 1; i++) {
            int minIndex = i;
            listener.onRange(i, values.length - 1);
            listener.onPivot(i);
            for (int j = i + 1; j < values.length; j++) {
                listener.onCompare(j, minIndex);
                if (values[j] < values[minIndex]) {
                    minIndex = j;
                }
            }
            if (minIndex != i) {
                SortAlgorithm.swap(values, i, minIndex, listener);
            }
        }
    }

    @Override
    public String toString() {
        return "SelectionSort";
    }
}

/**
 * InsertionSort: grows a sorted prefix one element at a time, bubbling each
 * new element down to its place with adjacent swaps.
 * <p>
 * Time complexity: O(n²), O(n) on an already sorted array.
 * Space complexity: O(1).
 * </p>
 */
class InsertionSortAlgorithm implements SortAlgorithm {

    @Override
    public void sort(int[] values, SortListener listener) {
        // The first element is considered sorted; start from the second
        for (int i = 1; i < values.length; i++) {
            listener.onRange(0, i);
            int j = i;
            while (j > 0) {
                listener.onCompare(j, j - 1);
                if (values[j] >= values[j - 1]) {
                    break;
                }
                SortAlgorithm.swap(values, j, j - 1, listener);
                j--;
            }
        }
    }

    @Override
    public boolean tintsComparisons() {
        return false; // fast adjacent comparisons: markers only, less visual noise
    }

    @Override
    public String toString() {
        return "InsertionSort";
    }
}

/**
 * MergeSort: recursively splits the array in halves, sorts them and merges
 * the sorted halves through a temporary buffer.
 * <p>
 * The merge rewrites a range without swaps, so it is reported as a range
 * refresh with a snapshot of the array state after the merge.
 * </p>
 * <p>
 * Time complexity: O(n log n).
 * Space complexity: O(n).
 * </p>
 */
class MergeSortAlgorithm implements SortAlgorithm {

    @Override
    public void sort(int[] values, SortListener listener) {
        mergeSort(values, 0, values.length - 1, listener);
    }

    private static void mergeSort(int[] values, int low, int high, SortListener listener) {
        if (low >= high) {
            return;
        }
        listener.onRange(low, high);

        // Base case: two elements are sorted with a single comparison
        if (high - low == 1) {
            listener.onCompare(low, high);
            if (values[low] > values[high]) {
                SortAlgorithm.swap(values, low, high, listener);
            }
            return;
        }

        int mid = low + (high - low) / 2;
        mergeSort(values, low, mid, listener);
        mergeSort(values, mid + 1, high, listener);
        merge(values, low, mid, high, listener);
    }

    private static void merge(int[] values, int low, int mid, int high, SortListener listener) {
        int[] temp = new int[high - low + 1];
        int i = low;
        int j = mid + 1;
        int k = 0;

        while (i <= mid && j <= high) {
            listener.onCompare(i, j);
            if (values[i] <= values[j]) {
                temp[k++] = values[i++];
            } else {
                temp[k++] = values[j++];
            }
        }
        while (i <= mid) {
            temp[k++] = values[i++];
        }
        while (j <= high) {
            temp[k++] = values[j++];
        }

        System.arraycopy(temp, 0, values, low, temp.length);
        listener.onRangeRefresh(low, high, values);
    }

    @Override
    public boolean tintsComparisons() {
        return false; // merging heads advance quickly: markers only
    }

    @Override
    public String toString() {
        return "MergeSort";
    }
}
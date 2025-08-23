package principal;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import principal.Renderer.Action;

/**
 * QuickSort Visualization Application
 * <p>
 * A JavaFX application that visualizes the QuickSort algorithm in real-time.
 * Features:
 * - Interactive sorting visualization with graphical bars
 * - Step-by-step execution control (restart, iterate, stop, step)
 * - Configurable canvas size via command line arguments
 * - Real-time animation of sorting operations
 * - Audio feedback for swap operations
 * </p>
 */
public class QuickSort extends Application {

	private enum SortAlgorithm {
		QuickSort,
		BubbleSort,
		SelectionSort,
		InsertionSort
	}

	private SortAlgorithm actualSortAlgorithm = SortAlgorithm.QuickSort;
	private ComboBox<SortAlgorithm> algorithmSelector;
	private static final int DEFAULT_WIDTH = 800;
	private static final int DEFAULT_HEIGHT = 600;
	private static final int BUTTON_HEIGHT = 25;

	private int canvasWidth;
	private int canvasHeight;
	private static final Color BACKGROUND_COLOR = Color.LIGHTGRAY;
	private Canvas canvas;
	private Renderer renderer;
	private AnimationTimer animationTimer;
	private boolean finished = false;

	/**
	 * Flag to control whether sound is enabled during sorting visualization.
	 * When true, audio feedback is played during swap operations.
	 * When false, all sound generation is skipped.
	 */
	private boolean soundEnabled = true;

	private double frameCounter = 0;
	private static final int FRAME_SKIP = 1; // Update every FRAME_SKIP frames for performance

	/** Array containing the values to be sorted by the QuickSort algorithm */
	private int[] arrayOfValuesToSort;

	/**
	 * Main method to launch the JavaFX application.
	 * 
	 * @param args command line arguments
	 */
	public static void main(String[] args) {
		launch(args);
	}

	/**
	 * Starts the JavaFX application and initializes the user interface.
	 * Creates the main window with canvas for visualization and control buttons.
	 * 
	 * @param primaryStage the primary stage for this application
	 */
	@Override
	public void start(Stage primaryStage) {
		// Parse command line arguments for size
		Parameters params = getParameters();
		parseSize(params);
		parseSoundOption(params); // Parse sound option

		primaryStage.setTitle("QuickSort");

		// Create and initialize
		initialize();

		restart(true);

		// Setup mouse listeners
		// setupMouseListeners();

		// Create the restart button
		Button restartButton = new Button("Restart");
		restartButton.setOnAction(e -> restart(true));
		restartButton.setPrefHeight(BUTTON_HEIGHT);

		// Create the Iterate button for continuous animation
		Button iterateButton = new Button("Iterate");
		iterateButton.setOnAction(e -> iterate());
		iterateButton.setPrefHeight(BUTTON_HEIGHT);

		// Create the Stop button to pause animation
		Button stopButton = new Button("Stop");
		stopButton.setOnAction(e -> stopIteration());
		stopButton.setPrefHeight(BUTTON_HEIGHT);

		// Create the Step button for single-step execution
		Button stepButton = new Button("Step");
		stepButton.setOnAction(e -> step());
		stepButton.setPrefHeight(BUTTON_HEIGHT);

		// Create the algorithm selector dropdown
		algorithmSelector = new ComboBox<>();
		algorithmSelector.getItems().addAll(SortAlgorithm.values());
		algorithmSelector.setValue(actualSortAlgorithm);
		algorithmSelector.setPrefHeight(BUTTON_HEIGHT);
		// Add a listener to update the actualSortAlgorithm field when selection changes
		algorithmSelector.setOnAction(e -> {
			actualSortAlgorithm = algorithmSelector.getValue();
			restart(false);
		});

		// Layout
		BorderPane root = new BorderPane();
		root.setCenter(canvas);

		HBox buttons = new HBox();
		root.setBottom(buttons);
		buttons.setPadding(new Insets(0, 10, 0, 0));
		buttons.setSpacing(10);
		buttons.getChildren().addAll(restartButton, iterateButton, stopButton, stepButton, algorithmSelector);

		// Create scene and show
		Scene scene = new Scene(root, canvasWidth, canvasHeight + BUTTON_HEIGHT);
		primaryStage.setScene(scene);
		primaryStage.setResizable(false);
		primaryStage.show();
	}

	/**
	 * Generates a random array of integers for sorting visualization.
	 * 
	 * @param numberOfElements the size of the array to generate
	 * @param maxValue         the maximum value for array elements (exclusive)
	 */
	private void generateRandomArray(int numberOfElements, int maxValue) {
		arrayOfValuesToSort = new int[numberOfElements];
		renderer.renderArrayOfValues = new int[numberOfElements];
		for (int i = 0; i < numberOfElements; i++) {
			arrayOfValuesToSort[i] = (int) (Math.random() * maxValue);
			renderer.renderArrayOfValues[i] = arrayOfValuesToSort[i];
		}
	}

	/**
	 * Parses command line arguments for canvas size.
	 * 
	 * @param params the parameters to parse
	 */
	private void parseSize(Parameters params) {
		try {
			if (params.getNamed().containsKey("width")) {
				canvasWidth = Integer.parseInt(params.getNamed().get("width"));
			} else {
				canvasWidth = DEFAULT_WIDTH;
			}

			if (params.getNamed().containsKey("height")) {
				canvasHeight = Integer.parseInt(params.getNamed().get("height"));
			} else {
				canvasHeight = DEFAULT_HEIGHT;
			}
		} catch (NumberFormatException e) {
			System.err.println("Invalid size parameters, using defaults");
			canvasWidth = DEFAULT_WIDTH;
			canvasHeight = DEFAULT_HEIGHT;
		}
	}

	/**
	 * Parses command line argument for sound option.
	 * 
	 * @param params the parameters to parse
	 */
	private void parseSoundOption(Parameters params) {
		if (params.getNamed().containsKey("sound")) {
			soundEnabled = Boolean.parseBoolean(params.getNamed().get("sound"));
		}
	}

	/**
	 * Initializes the canvas and starts the animation loop.
	 * Sets up the graphics context, creates the renderer, and begins the animation
	 * timer
	 * for real-time visualization updates.
	 */
	private void initialize() {
		canvas = new Canvas(canvasWidth, canvasHeight);

		GraphicsContext gc = canvas.getGraphicsContext2D();

		gc.clearRect(0, 0, canvasWidth, canvasHeight);
		gc.setFill(BACKGROUND_COLOR);
		gc.fillRect(0, 0, canvasWidth, canvasHeight);

		finished = false;

		renderer = new Renderer(gc, canvasWidth, canvasHeight, new Color(.3, .6, .9, 1), BACKGROUND_COLOR,
				soundEnabled);
		animationTimer = new AnimationTimer() {
			@Override
			public void handle(long now) {
				if (!finished && renderer != null) {
					frameCounter++;
					if (frameCounter >= Double.MAX_VALUE)
						frameCounter = 0;
					if (frameCounter % FRAME_SKIP == 0) {
						try {
							renderer.updateRegion();
						} catch (Exception e) {
							System.err.println("Error updating : " + e.getMessage());
							stopAnimation();
						}
					}
				}
			}
		};

		animationTimer.start();
	}

	/**
	 * Restarts the sorting simulation with a new randomly generated array.
	 * Clears the current visualization, generates new data, and starts the selected
	 * sorting algorithm.
	 */
	private void restart(boolean generateNewArray) {

		if (renderer == null)
			return;
		System.out.println("Restarting with " + actualSortAlgorithm + "...");
		renderer.clearImage();
		// Generate new array if requested, else get the array of the rendered
		if (generateNewArray)
			generateRandomArray(canvasWidth / Renderer.PIXEL_SIZE - 2,
					canvasHeight / Renderer.PIXEL_SIZE - 10);
		else
			renderer.copyRenderArrayOfValues(arrayOfValuesToSort);

		for (int a = 0; a < arrayOfValuesToSort.length; a++) {
			renderer.rectangleToScreen(a, (canvasHeight / Renderer.PIXEL_SIZE) - arrayOfValuesToSort[a] - 1, 1,
					arrayOfValuesToSort[a], true, true);
		}

		renderer.rectangleToScreen(0, (canvasHeight / Renderer.PIXEL_SIZE) - 1, (canvasWidth / Renderer.PIXEL_SIZE), 1,
				true, true, Color.LIGHTBLUE);

		if (renderer != null) {
			renderer.restart();
		}

		// Use the selected algorithm
		switch (actualSortAlgorithm) {
			case BubbleSort:
				bubbleSort(0, arrayOfValuesToSort.length - 1);
				break;
			case SelectionSort:
				selectionSort(0, arrayOfValuesToSort.length - 1);
				break;
			case InsertionSort:
				insertionSort(0, arrayOfValuesToSort.length - 1);
				break;
			default:
				quickSort(0, arrayOfValuesToSort.length - 1);
				break;
		}
	}

	/**
	 * Swaps two elements in the array and adds the swap action to the renderer
	 * queue.
	 * Also plays a sound based on the distance between the swapped elements.
	 * Low frequency (bass) for nearby elements, high frequency for distant
	 * elements.
	 * 
	 * @param ini index of the first element to swap
	 * @param fin index of the second element to swap
	 */
	private void swap(int ini, int fin) {
		// Add the swap action to the renderer queue
		Renderer.actionQueue.add(new Renderer.Action(Action.Operations.SWAP, ini, fin));

		// Perform the actual array swap
		int temp = arrayOfValuesToSort[ini];
		arrayOfValuesToSort[ini] = arrayOfValuesToSort[fin];
		arrayOfValuesToSort[fin] = temp;
	}

	/**
	 * Implements the Selection Sort algorithm.
	 * 
	 * Selection sort works by repeatedly finding the minimum element from the
	 * unsorted part
	 * and putting it at the beginning. The algorithm maintains two subarrays in a
	 * given array:
	 * 1) The subarray which is already sorted.
	 * 2) Remaining subarray which is unsorted.
	 * 
	 * In every iteration of selection sort, the minimum element from the unsorted
	 * subarray
	 * is picked and moved to the sorted subarray.
	 * 
	 * Time Complexity: O(n²)
	 * Space Complexity: O(1)
	 * 
	 * @param ini starting index of the subarray to sort
	 * @param fin ending index of the subarray to sort
	 */
	private void selectionSort(int ini, int fin) {
		// One by one move boundary of unsorted subarray
		for (int i = ini; i < fin; i++) {
			// Find the minimum element in unsorted array
			int minIndex = i;
			Renderer.actionQueue.add(new Renderer.Action(Action.Operations.PIVOT, i, 0));
			for (int j = i + 1; j <= fin; j++) {
				Renderer.actionQueue.add(new Renderer.Action(Action.Operations.COMPARE, j, minIndex));
				if (arrayOfValuesToSort[j] < arrayOfValuesToSort[minIndex]) {
					minIndex = j;
				}
			}

			// Swap the found minimum element with the first element
			if (minIndex != i) {
				swap(i, minIndex);
			}
		}
	}

	/**
	 * Implements the Insertion Sort algorithm.
	 * 
	 * Insertion sort builds the final sorted array one item at a time. It is much
	 * less efficient
	 * on large lists than more advanced algorithms such as quicksort, heapsort, or
	 * merge sort.
	 * 
	 * Insertion sort iterates, consuming one input element each repetition, and
	 * growing a sorted
	 * output list. At each iteration, insertion sort removes one element from the
	 * input data,
	 * finds the location it belongs within the sorted list, and inserts it there.
	 * It repeats until no input elements remain.
	 * 
	 * Time Complexity: O(n²)
	 * Space Complexity: O(1)
	 * 
	 * @param ini starting index of the subarray to sort
	 * @param fin ending index of the subarray to sort
	 */
	private void insertionSort(int ini, int fin) {
		// Start from the second element (index ini+1) as the first element is
		// considered sorted
		for (int i = ini + 1; i <= fin; i++) {
			int j = i;

			// Mark the current element being processed
			Renderer.actionQueue.add(new Renderer.Action(Action.Operations.PIVOT, i, 0));

			// Move elements of arrayOfValuesToSort[ini..i-1], that are greater than key,
			// to one position ahead of their current position using swaps
			while (j > ini && arrayOfValuesToSort[j] < arrayOfValuesToSort[j - 1]) {
				Renderer.actionQueue.add(new Renderer.Action(Action.Operations.COMPARE, j, j - 1));
				// Swap adjacent elements
				swap(j, j - 1);
				j--;
			}
		}
	}

	/**
	 * Implements the Bubble Sort algorithm.
	 * 
	 * Bubble sort is a simple sorting algorithm that repeatedly steps through the
	 * list,
	 * compares adjacent elements and swaps them if they are in the wrong order.
	 * The pass through the list is repeated until the list is sorted.
	 * 
	 * The algorithm, which is a comparison sort, is named for the way smaller or
	 * larger elements
	 * "bubble" to the top of the list.
	 * 
	 * An optimized version is implemented that stops early if no swaps are made in
	 * a pass,
	 * indicating the list is already sorted.
	 * 
	 * Time Complexity: O(n²)
	 * Space Complexity: O(1)
	 * 
	 * @param ini starting index of the subarray to sort
	 * @param fin ending index of the subarray to sort
	 */

	private void bubbleSort(int ini, int fin) {
		boolean swapped;
		// Outer loop
		// for each loop element at i position becames ordered
		for (int i = fin; i > ini; i--) {
			swapped = false;
			// Compare adjacent elements
			Renderer.actionQueue.add(new Renderer.Action(Action.Operations.PIVOT, i - 1, 0));
			for (int j = ini; j < i ; j++) {
				Renderer.actionQueue.add(new Renderer.Action(Action.Operations.COMPARE, j, j + 1));
				if (arrayOfValuesToSort[j] > arrayOfValuesToSort[j + 1]) {
					swap(j, j + 1);
					swapped = true;
				}
			}
			// If no swapping occurred, array is sorted
			if (!swapped)
				break; 
		}
	}

	/**
	 * Implements the QuickSort algorithm recursively.
	 * Uses the last element as pivot and partitions the array around it.
	 * 
	 * @param ini starting index of the subarray to sort
	 * @param fin ending index of the subarray to sort
	 */
	private void quickSort(int ini, int fin) {
		// Base case: if subarray has 1 or 0 elements, it's already sorted
		if (fin <= ini)
			return;

		// Choose the last element as pivot
		int pivotValue = arrayOfValuesToSort[fin];
		Renderer.actionQueue.add(new Renderer.Action(Action.Operations.PIVOT, fin, 0));

		// Initialize pointers for partitioning
		int i = ini; // Left pointer
		int j = fin - 1; // Right pointer (excluding pivot)

		// Partition: move elements smaller than pivot to left, larger to right
		while (i <= j) {
			// Find element from left that should be on right side

			while (i <= j && arrayOfValuesToSort[i] <= pivotValue) {
				Renderer.actionQueue.add(new Renderer.Action(Action.Operations.COMPARE, i, fin));
				i++;
			}
			// Find element from right that should be on left side
			Renderer.actionQueue.add(new Renderer.Action(Action.Operations.COMPARE, i, fin));
			while (i <= j && arrayOfValuesToSort[j] > pivotValue) {
				Renderer.actionQueue.add(new Renderer.Action(Action.Operations.COMPARE, j, fin));
				j--;
			}

			// Swap elements if both pointers found misplaced elements
			if (i < j) {
				swap(i, j);
				i++;
				j--;
			}
		}

		// Place pivot in its correct position (only if necessary)
		if (arrayOfValuesToSort[i] > pivotValue && i != fin) {
			swap(i, fin);
		}

		// Recursively sort the two partitions
		quickSort(ini, i - 1); // Sort left partition
		quickSort(i + 1, fin); // Sort right partition
	}

	/**
	 * Starts continuous iteration mode for the sorting visualization.
	 * The animation will run automatically until stopped.
	 */
	private void iterate() {
		System.out.println("Iterating ...");
		if (renderer != null) {
			renderer.iterate();
		}
	}

	/**
	 * Stops the continuous iteration mode of the sorting visualization.
	 */
	private void stopIteration() {
		System.out.println("stop ...");
		if (renderer != null) {
			renderer.stopIteration();
		}
	}

	/**
	 * Performs a single step in the sorting visualization.
	 * Useful for debugging or educational purposes to see each operation.
	 */
	private void step() {
		System.out.println("step ...");
		if (renderer != null) {
			renderer.step();
		}
	}

	/**
	 * Stops the animation timer and cleans up resources.
	 */
	private void stopAnimation() {
		finished = true;
		if (animationTimer != null) {
			animationTimer.stop();
		}
	}

	/**
	 * Cleanup method called when the application is closing.
	 * Stops the animation timer and releases resources.
	 */
	@Override
	public void stop() {
		stopAnimation();
		renderer = null;
		System.out.println(" destroyed");
		Platform.exit();
	}
}
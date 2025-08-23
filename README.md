# QuickSort Visualization

A JavaFX application that visualizes sorting algorithms in real-time with interactive graphical elements and audio feedback.

![QuickSort Visualization](https://user-images.githubusercontent.com/placeholder-image.jpg)

## Features

- **Interactive Visualization**: Watch sorting algorithms in action with colorful bar representations
- **Algorithm Selection**: Choose between QuickSort and BubbleSort algorithms
- **Step-by-Step Execution**: Control the sorting process with step-by-step visualization
- **Real-time Animation**: Smooth animations for swap operations and pivot highlighting
- **Audio Feedback**: Hear tones during swap operations that correspond to the distance between elements
- **Multiple Control Options**:
  - Restart: Generate a new random array and restart the sorting
  - Iterate: Run the sorting continuously
  - Stop: Pause the ongoing animation
  - Step: Execute one operation at a time
- **Configurable Canvas Size**: Set custom width and height via command line arguments

## Getting Started

### Prerequisites

- Java 11 or higher
- JavaFX SDK (if not included in your Java distribution)

### Installation

1. Clone the repository:
   ```bash
   git clone https://github.com/your-username/quickSort.git
   ```

2. Navigate to the project directory:
   ```bash
   cd quickSort
   ```

3. Compile the project:
   ```bash
   javac --module-path $PATH_TO_FX --add-modules javafx.controls,javafx.fxml -d out src/principal/*.java
   ```

4. Run the application:
   ```bash
   java --module-path $PATH_TO_FX --add-modules javafx.controls,javafx.fxml -cp out principal.QuickSort
   ```

### Command Line Arguments

You can customize the canvas size by providing width and height parameters:

```bash
java --module-path $PATH_TO_FX --add-modules javafx.controls,javafx.fxml -cp out principal.QuickSort --width=1000 --height=700
```

Default size is 800x600 pixels.

## How It Works

The application visualizes sorting algorithms using the following visual elements:

- **Colored Bars**: Each bar represents an element in the array with its height proportional to the value
- **Black Frames**: Each bar is outlined with a black frame for better visibility
- **Pivot Highlighting**: The pivot element flashes during partitioning (QuickSort only)
- **Swap Animation**: Elements being swapped blink before changing positions
- **Audio Feedback**: During swap operations, a tone is played:
  - **Low frequency (bass)**: For elements that are close to each other
  - **High frequency**: For elements that are far apart

### Controls

- **Algorithm Selection**: Dropdown menu to choose between QuickSort and BubbleSort
- **Restart**: Generates a new random array and restarts the visualization with the selected algorithm
- **Iterate**: Runs the sorting algorithm continuously until completion
- **Stop**: Pauses the ongoing sorting animation
- **Step**: Executes a single step of the algorithm

## Algorithm Implementation

The project implements multiple sorting algorithms:

### QuickSort
- Last element as pivot selection
- Two-pointer partitioning scheme
- Proper recursion on subarrays
- Average time complexity of O(n log n)

### BubbleSort
- Simple comparison-based algorithm
- Repeatedly steps through the list, compares adjacent elements and swaps them if they are in the wrong order
- Time complexity of O(n²)

## Code Structure

```
src/principal/
├── QuickSort.java     # Main application class with JavaFX UI
├── Renderer.java      # Visualization rendering engine
```

### Key Classes

#### QuickSort.java
- Main JavaFX application class
- Handles UI components and user interactions
- Manages the animation timer
- Implements multiple sorting algorithms (QuickSort and BubbleSort)
- Provides algorithm selection via dropdown menu
- Generates audio feedback for swap operations

#### Renderer.java
- Handles all visualization aspects
- Manages the animation queue
- Renders array elements as colored bars
- Provides animation effects for sorting operations

## Audio Implementation

The audio feature generates tones based on the distance between swapped elements:

- **Nearby elements** (distance small): Low frequency tones (bass)
- **Distant elements** (distance large): High frequency tones
- Audio is generated using Java's Sound API with sine wave generation
- Sounds are played in separate threads to avoid blocking the UI

## Contributing

Contributions are welcome! Feel free to submit issues or pull requests for:

- Bug fixes
- Performance improvements
- New visualization features
- UI enhancements
- Additional audio effects

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Acknowledgments

- Built with JavaFX for rich client applications
- Uses Java Sound API for audio generation
- Inspired by algorithm visualization techniques for educational purposes
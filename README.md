# Teleprompter Web Application

This project is a web-based teleprompter application that allows users to display and scroll through a script while recording themselves using their device's camera. The application supports both front and rear cameras and allows users to adjust the scrolling speed of the script. Additionally, users can toggle the visibility of the script input area and the video feed.
The application is static (just HTML, JS and CSS), it's very simple and responsive (tested on my Android Smartphone).

## Features

- Display and scroll through a script
- Record video with audio using the device's camera
- Toggle between front and rear cameras
- Adjust the scrolling speed of the script
- Toggle the visibility of the script input area and the video feed
- Save recorded videos in MP4 format

## Getting Started

### Prerequisites

To run this project, you need a modern web browser that supports the MediaRecorder API and getUserMedia API.

### Installation

1. Clone the repository:
    ```sh
    git clone https://github.com/yourusername/teleprompter.git
    cd teleprompter
    ```

2. Open the `index.html` file in your web browser.

### Usage

1. Enter your script in the input area by clicking the "Input Text" button.
2. Click the "Play" button to start scrolling the script.
3. Adjust the scrolling speed using the range input.
4. Click the "Pause" button to stop scrolling.
5. Click the "Record" button to start recording the video.
6. Click the "Record" button again to stop recording and save the video.
7. Click the "Restart" button to reset the script position to the beginning.
8. Use the camera select dropdown to switch between the front and rear cameras.

## Project Structure

- `index.html`: The main HTML file that contains the structure of the web application.
- `styles.css`: The CSS file that contains the styles for the web application.
- `script.js`: The JavaScript file that contains the logic for the web application.

## Code Overview

### HTML

The `index.html` file contains the structure of the web application, including the video element, script display area, control buttons, and script input area.

### CSS

The `styles.css` file contains the styles for the web application, ensuring a responsive layout and proper positioning of elements.

### JavaScript

The `script.js` file contains the logic for the web application, including:

- Handling button clicks for play, pause, record, restart, and toggle input area.
- Adjusting the scrolling speed of the script.
- Managing the video stream and recording using the MediaRecorder API.
- Switching between front and rear cameras.

## Contributing

Contributions are welcome! Please feel free to submit a pull request or open an issue if you have any suggestions or improvements.

## License

This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for more details.

## Acknowledgements

- [MediaRecorder API](https://developer.mozilla.org/en-US/docs/Web/API/MediaRecorder)
- [getUserMedia API](https://developer.mozilla.org/en-US/docs/Web/API/MediaDevices/getUserMedia)
- [ffmpeg.js](https://github.com/ffmpegwasm/ffmpeg.wasm)

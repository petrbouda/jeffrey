# Jeffrey Screenshot Tool

This tool automates capturing screenshots of Jeffrey's UI pages using Claude Computer Use in Chrome.

## Prerequisites

- Jeffrey application running (either from IDE or `jeffrey.jar`)
- Node.js installed
- Chrome browser with Claude Computer Use enabled

### Required Data

The screenshot tool expects the following data to be available in Jeffrey:

1. **Workspace**: `My Projects` - A workspace with this exact name must exist
2. **Profile**: `jeffrey-persons-direct-serde-cpu.jfr` - This JFR profile must be initialized and available within the workspace

To set this up:
1. Start Jeffrey and create a workspace named `My Projects` (or use an existing one)
2. Upload and initialize the `jeffrey-persons-direct-serde-cpu.jfr` recording file
3. Ensure the profile is fully parsed and ready for analysis before running the screenshot tool

## Usage

### Step 1: Start Jeffrey

Start the Jeffrey application using one of these methods:

**From IDE:**
Run the main application class from your IDE.

**From JAR:**
```bash
java -jar jeffrey.jar
```

Ensure Jeffrey is accessible at `http://localhost:8080` (or your configured port).

### Step 2: Start the Screenshot Server

The screenshot server receives images from Claude and saves them to the `./target` directory.

```bash
cd screenshots
node screenshots-server.cjs
```

You should see:
```
Upload server running at http://localhost:3333
Files will be saved to: ./target
```

### Step 3: Open Claude in Chrome

1. Open Chrome browser
2. Navigate to Jeffrey's UI
3. Open Chrome DevTools (F12 or Cmd+Option+I)
4. Enable Claude Computer Use in the console

### Step 4: Run the Screenshot Script

1. Open `create-screenshots.md` in a text editor
2. Copy the entire contents of the file
3. Paste it into the Chrome console where Claude is active
4. Press Enter to execute

### Step 5: Wait for Completion

Claude will:
1. Navigate through the Jeffrey UI pages listed in `create-screenshots.md`
2. Capture screenshots of each page
3. Upload them to the screenshot server
4. Screenshots are saved to `./target` directory

## Output

Screenshots are saved to `screenshots/target/` with the following naming convention:

| Filename | Page |
|----------|------|
| `01-configuration.png` | Configuration (ANALYSIS) |
| `02-guardian-analysis.png` | Guardian Analysis |
| `03-auto-analysis.png` | Auto Analysis |
| `04-performance-counters.png` | Performance Counters |
| `05-event-types.png` | Event Types (EVENTS) |
| `06-event-viewer.png` | Event Viewer |
| `07-jvm-flags.png` | JVM Flags |
| `08-thread-statistics.png` | Statistics (THREADS) |
| `09-threads-timeline.png` | Timeline |
| `10-heap-memory-timeseries.png` | Timeseries (HEAP MEMORY) |
| `11-gc-overview.png` | Overview (GARBAGE COLLECTION) |
| `12-gc-timeseries.png` | Timeseries (GARBAGE COLLECTION) |
| `13-gc-configuration.png` | Configuration (GARBAGE COLLECTION) |
| `14-jit-compilation.png` | JIT Compilation (COMPILER) |
| `15-container-configuration.png` | Container Configuration (INFRASTRUCTURE) |

## Troubleshooting

- **Server not receiving uploads**: Ensure the screenshot server is running on port 3333
- **CORS errors**: The server has CORS enabled for all origins
- **Missing screenshots**: Check that each page fully loads before Claude takes the screenshot
- **Wrong profile**: Ensure you're using the profile `jeffrey-persons-direct-serde-cpu.jfr`
- **Workspace not found**: Create a workspace named `My Projects` before running the tool
- **Profile not initialized**: The JFR file must be fully parsed - check that all analysis pages are accessible

# Create Screenshots for Jeffrey Documentation

### Prerequisites

Recordings needs to be available:
Check recordings at:

```bash
ls ../jeffrey-recordings
```

Fail if you don't any of these files:
- `jeffrey-persons-direct-serde-cpu.jfr.lz4`
- `jeffrey-persons-dom-serde-cpu.jfr.lz4`
- `jeffrey-persons-custom-events.jfr.lz4`
- `jeffrey-persons-dom-serde-method-tracing.jfr.lz4`
- `jeffrey-persons-native-allocation-samples.jfr.lz4`

### 1. Step: Build Jeffrey

Build Jeffrey (skip tests for faster build):

```bash
# Ensure Java 25 is active
source "$HOME/.sdkman/bin/sdkman-init.sh" && sdk use java 25.0.1-amzn

# Build
mvn clean package -DskipTests -q
```

### 2. Step: Start Jeffrey

Start Jeffrey with a unique temporary home directory:

```bash
# Generate UUID and start Jeffrey
UUID=$(uuidgen)
java -jar -Djeffrey.home.dir=/tmp/jeffrey/$UUID build/build-app/target/jeffrey.jar
```

Open and validate that Jeffrey is running: `http://localhost:8080`

**Note:** The `-Djeffrey.home.dir` parameter must be passed to java, not as a program argument.

### 3. Prepare Workspace and Profile

1. **Create Workspace**: Create a Sandbox workspace.
2. **Create Project**: Create a project within the workspace named `My Examples`.

### 4. Upload Recordings via API

Since browser automation cannot interact with native file dialogs, use the API to upload recordings directly.

**API Endpoint for uploading recordings:**
```
POST /api/internal/workspaces/{workspaceId}/projects/{projectId}/recordings
Content-Type: multipart/form-data
Form field: file=@<path-to-file>
```

**Upload all recordings using curl:**
```bash
# Replace {workspaceId} and {projectId} with actual IDs from the URL
# You can get these from the browser URL after creating the workspace and project

WORKSPACE_ID="<workspace-id-from-url>"
PROJECT_ID="<project-id-from-url>"
BASE_URL="http://localhost:8080/api/internal/workspaces/${WORKSPACE_ID}/projects/${PROJECT_ID}/recordings"

# Upload all recordings
curl -X POST "$BASE_URL" -F "file=@../jeffrey-recordings/jeffrey-persons-direct-serde-cpu.jfr.lz4"
curl -X POST "$BASE_URL" -F "file=@../jeffrey-recordings/jeffrey-persons-dom-serde-cpu.jfr.lz4"
curl -X POST "$BASE_URL" -F "file=@../jeffrey-recordings/jeffrey-persons-custom-events.jfr.lz4"
curl -X POST "$BASE_URL" -F "file=@../jeffrey-recordings/jeffrey-persons-dom-serde-method-tracing.jfr.lz4"
curl -X POST "$BASE_URL" -F "file=@../jeffrey-recordings/jeffrey-persons-native-allocation-samples.jfr.lz4"
```

Expected response: HTTP 204 No Content for each successful upload.

### 5. Initialize Profiles via UI

After uploading recordings, create profiles from them using the UI:

1. **Stay on the Recordings page** after uploading
2. **For each recording**, click the green button on the left side of the recording row with title: "Create profile from recording"
3. **Wait for initialization** - the profile parsing takes several seconds (up to tens of seconds for larger files)
4. **Proceed with the next recording** only after the current one finishes initializing
5. **Verify profiles** - go to the "Profiles" tab in the sidebar to confirm all profiles are available

**Note:** The initialization is complete when the green button changes to indicate the profile already exists.

### 6. Step: Profile Selection

Each guide specifies which profile to use. Follow the **Profile Selection** section in each guide file.

**Navigation between guides**: After finishing one guide, use the blue **"Profiles"** button in the top-right corner of the ProfileDetails page to return to the profiles list and select a different profile for the next guide.

### 7. Screenshot Capture Rules

Generic rules for capturing screenshots:

1. **Navigation**: Click sidebar menu item to navigate to the page
2. **Wait for load**: Ensure charts, tables, and data are visible before capture
3. **Tabs**: If a page has tabs, capture each tab separately
4. **Scrolling**: If content extends below the fold, capture additional screenshots with suffixes (-1.png, -2.png)
5. **Filters**: Some pages require filtered views - follow the specific guide instructions

### 8. Section Traversal Guides

Use the following guides for detailed traversal plans:

| Section | Guide File | Output Folder |
|---------|------------|---------------|
| JVM Internals | `jvm-internals-guide.md` | `target/jvm-internals/` |
| Visualization | `visualization-guide.md` | `target/visualization/` |

[//]: # (| Application | `application-guide.md` | `target/application/` |)
[//]: # (| Heap Dump | `heap-dump-guide.md` | `target/heap-dump/` |)

Each guide contains:
- Complete navigation structure
- Folder structure for organizing screenshots
- Detailed capture plan with tab and filter instructions

### 9. Save Screenshots

Save screenshots to `screenshots/target/` following the folder structure defined in each section guide.

---

## Chrome MCP Workflow (--chrome parameter)

When running Claude with the `--chrome` parameter, use this workflow to capture and save screenshots using html2canvas and a local screenshot server.

### How Screenshots Are Saved (Browser → Local Files)

The screenshot capture process works as follows:

1. **html2canvas library** is injected into the browser page via JavaScript
2. **html2canvas captures** the entire page as a canvas element
3. **Canvas is converted** to a PNG blob (binary image data)
4. **Browser sends POST request** with the image blob to the local screenshot server
5. **Screenshot server receives** the binary data and writes it to the `screenshots/target/` directory
6. **Subdirectories are created automatically** based on the filename path (e.g., `jvm-internals/analysis/`)

```
Browser (Jeffrey page)
    │
    ├─► html2canvas captures page → Canvas
    │
    ├─► Canvas.toBlob() → PNG binary data
    │
    └─► fetch POST to localhost:3333/upload/{path/filename.png}
                │
                ▼
        Screenshot Server (Node.js)
                │
                └─► Writes file to screenshots/target/{path/filename.png}
```

### Prerequisites

1. Start Jeffrey application (see steps above)
2. Run Claude with `--chrome` parameter for browser automation
3. Start the screenshot upload server

### Start Screenshot Server

The screenshot server accepts POST requests with image data and saves files to the `screenshots/target/` directory.

```bash
# From the screenshots directory
cd screenshots
node screenshots-server.cjs &
```

The server runs on `http://localhost:3333` and provides:
- `POST /upload/{path/filename.png}` - Save a screenshot to the specified path

### Screenshot Capture Process

Use the `javascript_tool` to inject html2canvas and capture screenshots directly from the browser.

**Important**: html2canvas must be re-injected after each page navigation since the script is cleared when the page changes.

### Capture Script Pattern

For each screenshot, execute this JavaScript in the Jeffrey tab:

```javascript
// Step 1: Inject html2canvas library
const script = document.createElement('script');
script.src = 'https://cdnjs.cloudflare.com/ajax/libs/html2canvas/1.4.1/html2canvas.min.js';
document.head.appendChild(script);

// Step 2: Wait for library to load, capture, and upload
new Promise(r => setTimeout(r, 1500)).then(async () => {
  const canvas = await html2canvas(document.body, {
    useCORS: true,
    allowTaint: true
  });
  const blob = await new Promise(resolve => canvas.toBlob(resolve, 'image/png'));
  const response = await fetch('http://localhost:3333/upload/jvm-internals/analysis/configuration.png', {
    method: 'POST',
    body: blob
  });
  console.log(response.ok ? 'Screenshot saved successfully' : 'Failed to save screenshot');
});
```

Replace `jvm-internals/analysis/configuration.png` with the appropriate path for each screenshot according to the section guide.

### Example Workflow

1. **Navigate** to the target page in Jeffrey using sidebar menu clicks
2. **Wait** for charts and data to fully load
3. **Inject** html2canvas script
4. **Capture and upload** with the appropriate filename
5. **Repeat** for each page/tab

### Capturing Tabs

For pages with multiple tabs:
1. Click the tab to switch views
2. Wait for content to load
3. Re-inject html2canvas (if page reloaded) and capture with tab-specific filename

Example tab filenames:
- `tab-before-after-gc.png`
- `tab-allocation-rate.png`

### Verifying Captures

Check the `screenshots/target/` directory to verify screenshots were saved:

```bash
ls -la screenshots/target/jvm-internals/
```

### Troubleshooting

- **html2canvas not defined**: The library needs time to load. Increase the timeout or re-inject the script.
- **Fetch failed**: Ensure the screenshot server is running on port 3333.
- **Blank screenshots**: Wait longer for page content to render before capturing.
- **CORS errors**: The `useCORS: true` option should handle most cases.

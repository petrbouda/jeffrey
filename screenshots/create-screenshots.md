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

### 6. Step: Navigation Steps:

1. **Select Profile** Click on "Profiles" tab in sidebar menu
2. **Select the profile**: `jeffrey-persons-direct-serde-cpu.jfr`
3. **Ensure JVM Internals section is active** (should be the default view)

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

[//]: # (| Application | `application-guide.md` | `target/application/` |)
[//]: # (| Visualization | `visualization-guide.md` | `target/visualization/` |)
[//]: # (| Heap Dump | `heap-dump-guide.md` | `target/heap-dump/` |)

Each guide contains:
- Complete navigation structure
- Folder structure for organizing screenshots
- Detailed capture plan with tab and filter instructions

### 9. Save Screenshots

Save screenshots to `screenshots/target/` following the folder structure defined in each section guide.

---

## Chrome MCP Workflow (--chrome parameter)

When running Claude with the `--chrome` parameter, use this workflow to capture and save screenshots.

### Prerequisites

1. Start the screenshot upload server:
   ```bash
   cd /path/to/jeffrey/screenshots
   node screenshots-server.cjs
   ```

2. Start Jeffrey application (see steps above)

3. Open Chrome with Claude browser automation

### Screenshot Capture Process

1. **Open two tabs in Chrome:**
   - Tab 1: `http://localhost:3333` (drop zone)
   - Tab 2: `http://localhost:8080` (Jeffrey application)

2. **For each screenshot:**
   - In the drop zone tab, enter the filename (e.g., `guardian-analysis.png`)
   - Switch to Jeffrey tab and navigate to the target page
   - Use `computer` tool with `action="screenshot"` to capture
   - Switch back to drop zone tab
   - Use `upload_image` tool with coordinates targeting the drop zone center
   - The file is automatically saved to `screenshots/target/`

### Example Tool Usage

```
# Take screenshot
computer: action="screenshot", tabId=<jeffrey-tab-id>

# Upload to drop zone (coordinates at center of drop zone)
upload_image: imageId=<from-screenshot>, tabId=<dropzone-tab-id>, coordinate=[400, 350]
```

### After Capture

Once all screenshots are captured to the flat `target/` folder, organize them into the structure defined in the section guide files (e.g., `jvm-internals-guide.md`).

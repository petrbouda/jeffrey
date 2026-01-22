const http = require('http');
const fs = require('fs');
const path = require('path');

const UPLOAD_DIR = './target';

// Ensure directory exists
if (!fs.existsSync(UPLOAD_DIR)) {
    fs.mkdirSync(UPLOAD_DIR, { recursive: true });
}

const DROP_ZONE_HTML = `
<!DOCTYPE html>
<html>
<head>
    <title>Screenshot Upload Server</title>
    <style>
        * { box-sizing: border-box; }
        body {
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
            margin: 0;
            padding: 20px;
            background: #1a1a2e;
            color: #eee;
            min-height: 100vh;
        }
        h1 { color: #00d4ff; margin-bottom: 10px; }
        .container { max-width: 800px; margin: 0 auto; }

        .filename-section {
            margin-bottom: 20px;
            padding: 15px;
            background: #16213e;
            border-radius: 8px;
        }
        .filename-section label {
            display: block;
            margin-bottom: 8px;
            color: #888;
        }
        #filename {
            width: 100%;
            padding: 12px;
            font-size: 16px;
            border: 2px solid #333;
            border-radius: 6px;
            background: #0f0f23;
            color: #fff;
        }
        #filename:focus {
            outline: none;
            border-color: #00d4ff;
        }

        .drop-zone {
            border: 3px dashed #444;
            border-radius: 12px;
            padding: 60px 20px;
            text-align: center;
            background: #16213e;
            transition: all 0.3s ease;
            cursor: pointer;
            margin-bottom: 20px;
        }
        .drop-zone:hover, .drop-zone.dragover {
            border-color: #00d4ff;
            background: #1a1a3e;
        }
        .drop-zone.success {
            border-color: #00ff88;
            background: #1a2e1a;
        }
        .drop-zone.error {
            border-color: #ff4444;
            background: #2e1a1a;
        }
        .drop-zone-icon {
            font-size: 48px;
            margin-bottom: 15px;
        }
        .drop-zone-text {
            font-size: 18px;
            color: #888;
        }

        #file-input {
            display: none;
        }

        .status {
            padding: 15px;
            border-radius: 8px;
            margin-bottom: 15px;
            display: none;
        }
        .status.show { display: block; }
        .status.success { background: #1a2e1a; color: #00ff88; }
        .status.error { background: #2e1a1a; color: #ff4444; }

        .recent-files {
            background: #16213e;
            border-radius: 8px;
            padding: 15px;
        }
        .recent-files h3 {
            margin: 0 0 10px 0;
            color: #888;
            font-size: 14px;
        }
        .file-list {
            list-style: none;
            padding: 0;
            margin: 0;
            max-height: 200px;
            overflow-y: auto;
        }
        .file-list li {
            padding: 8px;
            border-bottom: 1px solid #333;
            font-family: monospace;
            font-size: 13px;
        }
        .file-list li:last-child { border-bottom: none; }
        .file-list .time { color: #666; margin-right: 10px; }
    </style>
</head>
<body>
    <div class="container">
        <h1>Screenshot Drop Zone</h1>
        <p style="color: #666; margin-bottom: 20px;">For use with Claude --chrome browser automation</p>

        <div class="filename-section">
            <label for="filename">Filename (e.g., guardian-analysis.png)</label>
            <input type="text" id="filename" placeholder="screenshot.png" value="screenshot.png">
        </div>

        <div class="drop-zone" id="drop-zone">
            <div class="drop-zone-icon">📸</div>
            <div class="drop-zone-text">Drop screenshot here or click to select</div>
            <input type="file" id="file-input" accept="image/*">
        </div>

        <div class="status" id="status"></div>

        <div class="recent-files">
            <h3>Recently Saved Files</h3>
            <ul class="file-list" id="file-list"></ul>
        </div>
    </div>

    <script>
        const dropZone = document.getElementById('drop-zone');
        const fileInput = document.getElementById('file-input');
        const filenameInput = document.getElementById('filename');
        const status = document.getElementById('status');
        const fileList = document.getElementById('file-list');

        // Click to select
        dropZone.addEventListener('click', () => fileInput.click());

        // Drag events
        dropZone.addEventListener('dragover', (e) => {
            e.preventDefault();
            dropZone.classList.add('dragover');
        });
        dropZone.addEventListener('dragleave', () => {
            dropZone.classList.remove('dragover');
        });
        dropZone.addEventListener('drop', (e) => {
            e.preventDefault();
            dropZone.classList.remove('dragover');
            const files = e.dataTransfer.files;
            if (files.length > 0) uploadFile(files[0]);
        });

        // File input change
        fileInput.addEventListener('change', () => {
            if (fileInput.files.length > 0) uploadFile(fileInput.files[0]);
        });

        async function uploadFile(file) {
            const filename = filenameInput.value.trim() || 'screenshot.png';

            try {
                dropZone.classList.remove('success', 'error');
                status.className = 'status';
                status.textContent = 'Uploading...';
                status.classList.add('show');

                const response = await fetch('/upload/' + encodeURIComponent(filename), {
                    method: 'POST',
                    body: file
                });

                if (response.ok) {
                    dropZone.classList.add('success');
                    status.className = 'status success show';
                    status.textContent = 'Saved: ' + filename;
                    addToFileList(filename);

                    // Auto-increment filename if it ends with a number
                    autoIncrementFilename();

                    setTimeout(() => {
                        dropZone.classList.remove('success');
                    }, 2000);
                } else {
                    throw new Error('Upload failed');
                }
            } catch (err) {
                dropZone.classList.add('error');
                status.className = 'status error show';
                status.textContent = 'Error: ' + err.message;
            }

            // Reset file input
            fileInput.value = '';
        }

        function addToFileList(filename) {
            const li = document.createElement('li');
            const time = new Date().toLocaleTimeString();
            li.innerHTML = '<span class="time">' + time + '</span>' + filename;
            fileList.insertBefore(li, fileList.firstChild);

            // Keep only last 20
            while (fileList.children.length > 20) {
                fileList.removeChild(fileList.lastChild);
            }
        }

        function autoIncrementFilename() {
            const current = filenameInput.value;
            const match = current.match(/^(.+?)(\\d+)(\\.\\w+)$/);
            if (match) {
                const num = parseInt(match[2], 10) + 1;
                filenameInput.value = match[1] + num + match[3];
            }
        }
    </script>
</body>
</html>
`;

const server = http.createServer((req, res) => {
    // Enable CORS
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Access-Control-Allow-Methods', 'POST, OPTIONS');
    res.setHeader('Access-Control-Allow-Headers', 'Content-Type');

    if (req.method === 'OPTIONS') {
        res.writeHead(200);
        res.end();
        return;
    }

    if (req.method === 'POST' && req.url.startsWith('/upload/')) {
        const filename = decodeURIComponent(req.url.replace('/upload/', ''));
        const filepath = path.join(UPLOAD_DIR, filename);

        // Create parent directories if needed
        const dir = path.dirname(filepath);
        fs.mkdirSync(dir, { recursive: true });

        const chunks = [];
        req.on('data', chunk => chunks.push(chunk));
        req.on('end', () => {
            const buffer = Buffer.concat(chunks);
            fs.writeFileSync(filepath, buffer);
            console.log(`Saved: ${filename}`);
            res.writeHead(200, { 'Content-Type': 'text/plain' });
            res.end('OK');
        });
    } else if (req.method === 'GET' && req.url === '/') {
        res.writeHead(200, { 'Content-Type': 'text/html' });
        res.end(DROP_ZONE_HTML);
    } else {
        res.writeHead(404);
        res.end('Not Found');
    }
});

server.listen(3333, () => {
    console.log('Screenshot upload server running at http://localhost:3333');
    console.log(`Files will be saved to: ${UPLOAD_DIR}`);
});

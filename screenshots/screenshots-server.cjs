const http = require('http');
const fs = require('fs');
const path = require('path');

const UPLOAD_DIR = './target';

// Ensure directory exists
if (!fs.existsSync(UPLOAD_DIR)) {
    fs.mkdirSync(UPLOAD_DIR, { recursive: true });
}

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
        res.end(`
            <html><body>
            <h1>Screenshot Upload Server</h1>
            <p>Ready to receive uploads at POST /upload/{filename}</p>
            <h3>Usage:</h3>
            <pre>curl -X POST --data-binary @image.png http://localhost:3333/upload/my-screenshot.png</pre>
            <p>Files are saved to: ${UPLOAD_DIR}</p>
            </body></html>
        `);
    } else {
        res.writeHead(404);
        res.end('Not Found');
    }
});

server.listen(3333, () => {
    console.log('Upload server running at http://localhost:3333');
    console.log(`Files will be saved to: ${UPLOAD_DIR}`);
});

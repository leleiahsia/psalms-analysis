const { app, BrowserWindow } = require('electron');
const http = require('http');
const fs = require('fs');
const path = require('path');

const MIME_TYPES = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8'
};

function startStaticServer() {
  const webRoot = path.join(__dirname, '..', 'web');
  const server = http.createServer((request, response) => {
    const requestedPath = decodeURIComponent((request.url || '/').split('?')[0]);
    const relativePath = requestedPath === '/' ? 'index.html' : requestedPath.replace(/^[/\\]+/, '');
    const filePath = path.resolve(webRoot, relativePath);
    if (!filePath.startsWith(path.resolve(webRoot) + path.sep)) {
      response.writeHead(403); response.end('Forbidden'); return;
    }
    fs.readFile(filePath, (error, data) => {
      if (error) { response.writeHead(404); response.end('Not found'); return; }
      response.writeHead(200, { 'Content-Type': MIME_TYPES[path.extname(filePath)] || 'application/octet-stream' });
      response.end(data);
    });
  });
  return new Promise((resolve, reject) => {
    server.once('error', reject);
    server.listen(0, '127.0.0.1', () => resolve({ server, port: server.address().port }));
  });
}

async function createWindow() {
  const { server, port } = await startStaticServer();
  const window = new BrowserWindow({
    width: 1200,
    height: 900,
    minWidth: 760,
    minHeight: 600,
    backgroundColor: '#f7f1e6',
    webPreferences: { contextIsolation: true, nodeIntegration: false }
  });
  window.on('closed', () => server.close());
  await window.loadURL(`http://127.0.0.1:${port}/`);
}

app.whenReady().then(createWindow).catch(error => {
  console.error(error);
  app.quit();
});

app.on('window-all-closed', () => app.quit());

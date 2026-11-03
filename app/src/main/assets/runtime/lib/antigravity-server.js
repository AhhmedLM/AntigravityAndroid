/**
 * Antigravity Embedded Headless Daemon Server (`antigravity-server.js`)
 * Runs inside the embedded arm64-v8a Node.js runtime with `--max-old-space-size=256`.
 */
const http = require('http');
const port = parseInt(process.env.ANTIGRAVITY_PORT || '8765', 10);

const server = http.createServer((req, res) => {
  res.writeHead(200, { 'Content-Type': 'application/json' });
  res.end(JSON.stringify({
    status: 'listening',
    engine: 'antigravity-embedded-node',
    port: port,
    pid: process.pid,
    heapLimitMb: 256,
    home: process.env.HOME
  }));
});

server.listen(port, '127.0.0.1', () => {
  console.log(`[Antigravity] Daemon listening on http://127.0.0.1:${port} (PID ${process.pid})`);
});

process.on('SIGTERM', () => {
  console.log('[Antigravity] SIGTERM received, closing server gracefully...');
  server.close(() => process.exit(0));
});

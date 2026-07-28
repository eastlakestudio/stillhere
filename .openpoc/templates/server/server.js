const http = require('http');
const fs = require('fs');
const path = require('path');

const PORT = process.env.PORT || 3000;
const ROOT = path.join(__dirname, '..');

// SSE 客户端集合
const sseClients = new Set();

// MIME 类型映射
const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.js':   'application/javascript',
  '.css':  'text/css',
  '.json': 'application/json',
  '.png':  'image/png',
  '.jpg':  'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.gif':  'image/gif',
  '.svg':  'image/svg+xml',
  '.ico':  'image/x-icon',
  '.wasm': 'application/wasm',
};

// ============================================================
// 静态文件服务
// ============================================================
function serveStatic(req, res) {
  let reqPath = req.url.split('?')[0];
  if (reqPath === '/') reqPath = '/index.html';

  const filePath = path.join(ROOT, reqPath);
  const ext = path.extname(filePath).toLowerCase();

  // 安全检查：防止目录穿越
  if (!filePath.startsWith(ROOT)) {
    res.writeHead(403);
    res.end('Forbidden');
    return;
  }

  if (!fs.existsSync(filePath) || fs.statSync(filePath).isDirectory()) {
    res.writeHead(404);
    res.end('Not Found');
    return;
  }

  const contentType = MIME[ext] || 'application/octet-stream';
  res.writeHead(200, { 'Content-Type': contentType });
  fs.createReadStream(filePath).pipe(res);
}

// ============================================================
// Mock API 服务 — 基于 schema.json 自动路由
// ============================================================
function loadSchema() {
  const schemaPath = path.join(__dirname, 'schema.json');
  if (!fs.existsSync(schemaPath)) return { entities: {} };
  return JSON.parse(fs.readFileSync(schemaPath, 'utf8'));
}

function serveAPI(req, res) {
  const entity = req.url.replace(/^\/api\//, '').split('?')[0];

  // 健康检查
  if (entity === 'health') {
    const schema = loadSchema();
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ status: 'ok', entities: Object.keys(schema.entities || {}), count: Object.keys(schema.entities || {}).length }));
    return;
  }

  const dataFile = path.join(__dirname, 'src', 'services', entity + '.json');
  if (!fs.existsSync(dataFile)) {
    res.writeHead(404, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ error: 'Mock data not found: ' + entity }));
    return;
  }

  res.writeHead(200, { 'Content-Type': 'application/json' });
  fs.createReadStream(dataFile).pipe(res);
}

// ============================================================
// SSE Event Bus (替代 WebSocket，零依赖)
// ============================================================
function serveSSE(req, res) {
  res.writeHead(200, {
    'Content-Type': 'text/event-stream',
    'Cache-Control': 'no-cache',
    'Connection': 'keep-alive',
    'Access-Control-Allow-Origin': '*',
  });
  res.write('data: {"type":"system","event":"connected"}\n\n');

  sseClients.add(res);
  req.on('close', () => {
    sseClients.delete(res);
  });
}

// 客户端→服务端消息（SSE 仅服务端→客户端，反向用 POST）
function handlePostEvent(req, res) {
  let body = '';
  req.on('data', chunk => { body += chunk; });
  req.on('end', () => {
    try {
      const msg = JSON.parse(body);
      broadcast(msg, res);
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ ok: true }));
    } catch (e) {
      res.writeHead(400, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ error: 'Invalid JSON' }));
    }
  });
}

function broadcast(msg, excludeRes) {
  const data = 'data: ' + JSON.stringify(msg) + '\n\n';
  sseClients.forEach(client => {
    if (client !== excludeRes) {
      client.write(data);
    }
  });
}

// ============================================================
// 路由分发
// ============================================================
function log(req, code) {
  const time = new Date().toLocaleTimeString('zh-CN', { hour12: false });
  console.log('  [' + time + '] ' + req.method + ' ' + req.url + ' → ' + code);
}

const server = http.createServer((req, res) => {
  // SSE 端点
  if (req.url.startsWith('/api/events')) {
    if (req.method === 'GET')  { log(req, 200); return serveSSE(req, res); }
    if (req.method === 'POST') { log(req, 200); return handlePostEvent(req, res); }
  }

  // API 路由
  if (req.url.startsWith('/api/')) {
    log(req, 200);
    return serveAPI(req, res);
  }

  // 静态文件
  log(req, 200);
  serveStatic(req, res);
});

server.listen(PORT, () => {
  console.log('');
  console.log('  ╔══════════════════════════════════════════╗');
  console.log('  ║   POC Server Ready (zero-deps)           ║');
  console.log('  ║   http://localhost:' + String(PORT).padEnd(21) + ' ║');
  console.log('  ║   SSE: /api/events                       ║');
  console.log('  ╚══════════════════════════════════════════╝');
  console.log('');
});

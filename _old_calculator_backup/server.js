const http = require('http');
const fs = require('fs');
const path = require('path');

const PORT = process.env.PORT || 3000;
const PUBLIC_DIR = path.join(__dirname, 'public');

// MIME types for static assets
const MIME_TYPES = {
  '.html': 'text/html; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.js': 'application/javascript; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
  '.ico': 'image/x-icon'
};

/**
 * Call Gemini API using native fetch
 */
async function generateStoryWithGemini(expression, result, apiKey) {
  const prompt = `You are a friendly, witty math companion. Convert the following calculation into a single, fun, imaginative real-world English sentence using everyday objects, animals, or situations (for instance, for 3 + 2 = 5: "3 potatoes put together with 2 more gives 5 total potatoes!"). Keep it creative and within 1-2 short sentences. Do not return quotes or markdown headers.

Calculation: ${expression} = ${result}`;

  const payload = {
    contents: [
      {
        parts: [{ text: prompt }]
      }
    ]
  };

  // Check candidate models in order of stability
  const candidateModels = [
    'gemini-2.5-flash',
    'gemini-2.0-flash',
    'gemini-1.5-flash',
    'gemini-3.8-flash',
    'gemini-flash-latest'
  ];
  let lastError = null;

  for (const model of candidateModels) {
    try {
      const endpoint = `https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent?key=${encodeURIComponent(apiKey)}`;
      const response = await fetch(endpoint, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
      });

      const data = await response.json();

      if (!response.ok) {
        const errorMsg = data?.error?.message || `HTTP ${response.status} error`;
        console.warn(`[Gemini API] Model ${model} returned: ${errorMsg}`);
        lastError = new Error(errorMsg);
        continue; // Try next candidate model if 404 or unsupported
      }

      const text = data?.candidates?.[0]?.content?.parts?.[0]?.text;
      if (text) {
        return { success: true, story: text.trim(), modelUsed: model };
      } else {
        lastError = new Error('No text returned in Gemini candidate response');
      }
    } catch (err) {
      console.warn(`[Gemini API] Network/fetch error on ${model}: ${err.message}`);
      lastError = err;
    }
  }

  throw lastError || new Error('Failed to generate response from Gemini API');
}

/**
 * Read request JSON body helper
 */
function readJsonBody(req) {
  return new Promise((resolve, reject) => {
    let body = '';
    req.on('data', chunk => {
      body += chunk.toString();
      if (body.length > 1e6) { // 1MB limit
        req.destroy();
        reject(new Error('Payload too large'));
      }
    });
    req.on('end', () => {
      try {
        resolve(body ? JSON.parse(body) : {});
      } catch (err) {
        reject(new Error('Invalid JSON format'));
      }
    });
    req.on('error', reject);
  });
}

// Create HTTP server
const server = http.createServer(async (req, res) => {
  const reqUrl = new URL(req.url, `http://${req.headers.host || 'localhost'}`);
  const pathname = reqUrl.pathname;

  // Set standard CORS headers
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, x-goog-api-key');

  if (req.method === 'OPTIONS') {
    res.writeHead(204);
    res.end();
    return;
  }

  // API Endpoint: Calculate
  if (req.method === 'POST' && pathname === '/api/calculate') {
    try {
      const { num1, num2, operation } = await readJsonBody(req);
      const a = Number(num1);
      const b = Number(num2);

      if (isNaN(a) || isNaN(b)) {
        res.writeHead(400, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ error: 'Valid numbers num1 and num2 are required.' }));
        return;
      }

      let result;
      switch (operation) {
        case '+':
          result = a + b;
          break;
        case '-':
        case '−':
          result = a - b;
          break;
        case '*':
        case 'x':
        case '×':
          result = a * b;
          break;
        case '/':
        case '÷':
          if (b === 0) {
            res.writeHead(400, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ error: 'Division by zero is undefined.' }));
            return;
          }
          result = a / b;
          break;
        case '%':
          result = a % b;
          break;
        default:
          res.writeHead(400, { 'Content-Type': 'application/json' });
          res.end(JSON.stringify({ error: `Unsupported operation: ${operation}` }));
          return;
      }

      // Round floating point inaccuracies (e.g. 0.1 + 0.2 = 0.30000000000000004)
      const roundedResult = Number(Math.round(result + 'e+10') + 'e-10');
      const expression = `${a} ${operation} ${b}`;

      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({
        success: true,
        num1: a,
        num2: b,
        operation,
        result: roundedResult,
        expression: `${expression} = ${roundedResult}`
      }));
    } catch (err) {
      res.writeHead(400, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ error: err.message }));
    }
    return;
  }

  // API Endpoint: Explain with Gemini
  if (req.method === 'POST' && pathname === '/api/explain') {
    try {
      const { expression, result, apiKey } = await readJsonBody(req);
      const activeKey = (apiKey && apiKey.trim()) || process.env.GEMINI_API_KEY;

      if (!activeKey) {
        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({
          success: false,
          error: 'NO_API_KEY',
          message: 'No Gemini API key found. You can set the GEMINI_API_KEY environment variable or enter your key in the UI field.'
        }));
        return;
      }

      if (expression === undefined || result === undefined) {
        res.writeHead(400, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ error: 'expression and result are required.' }));
        return;
      }

      const aiResponse = await generateStoryWithGemini(expression, result, activeKey);
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify(aiResponse));
    } catch (err) {
      const isNetworkError = err.message && (err.message.includes('fetch failed') || err.message.includes('ENOTFOUND') || err.message.includes('ECONNREFUSED'));
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({
        success: false,
        error: isNetworkError ? 'NETWORK_ERROR' : 'API_ERROR',
        message: err.message || 'Error communicating with the Gemini API.'
      }));
    }
    return;
  }

  // Static File Serving
  if (req.method === 'GET') {
    let safePath = path.normalize(pathname).replace(/^(\.\.[\/\\])+/, '');
    if (safePath === '/' || safePath === '\\') {
      safePath = '/index.html';
    }

    const filePath = path.join(PUBLIC_DIR, safePath);

    // Prevent directory traversal
    if (!filePath.startsWith(PUBLIC_DIR)) {
      res.writeHead(403, { 'Content-Type': 'text/plain' });
      res.end('403 Forbidden');
      return;
    }

    fs.readFile(filePath, (err, content) => {
      if (err) {
        if (err.code === 'ENOENT') {
          res.writeHead(404, { 'Content-Type': 'text/plain' });
          res.end('404 Not Found');
        } else {
          res.writeHead(500, { 'Content-Type': 'text/plain' });
          res.end('500 Internal Server Error');
        }
        return;
      }

      const ext = path.extname(filePath);
      const contentType = MIME_TYPES[ext] || 'application/octet-stream';
      res.writeHead(200, { 'Content-Type': contentType });
      res.end(content);
    });
    return;
  }

  res.writeHead(405, { 'Content-Type': 'text/plain' });
  res.end('Method Not Allowed');
});

// Start server listening
server.listen(PORT, () => {
  console.log(`\n🧮 Calculator server is running!`);
  console.log(`➡️  Open your browser at: http://localhost:${PORT}`);
  console.log(`🔑 Gemini API key: ${process.env.GEMINI_API_KEY ? 'Configured via environment variable' : 'Not set (Optional: can be entered directly in web UI)'}\n`);
});

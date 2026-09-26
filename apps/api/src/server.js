import http from 'node:http';
import { readFile } from 'node:fs/promises';
import { extname, join } from 'node:path';
import { createPkceChallenge, oauthConfigured } from './auth.js';

const port = Number(process.env.PORT || 3000);
const publicDir = new URL('../../web/public/', import.meta.url);
const type = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript; charset=utf-8', '.css': 'text/css; charset=utf-8', '.json': 'application/json; charset=utf-8' };
const sendJson = (res, status, body) => { res.writeHead(status, { 'content-type': 'application/json', 'cache-control': 'no-store' }); res.end(JSON.stringify(body)); };
const dataStatus = () => ({ marketData: 'DATA_UNAVAILABLE', execution: 'DISABLED', reason: process.env.DERIV_APP_ID ? 'Server-side Deriv connection is not active.' : 'Deriv server configuration is incomplete.' });

http.createServer(async (req, res) => {
  const url = new URL(req.url, `http://${req.headers.host}`);
  if (url.pathname === '/health') return sendJson(res, 200, { status: 'ok', service: 'deriv-ai-api' });
  if (url.pathname === '/ready') return sendJson(res, 503, { ready: false, ...dataStatus() });
  if (url.pathname === '/metrics') return res.end('# deriv_ai_real_money_execution_enabled 0\n');
  if (url.pathname === '/api/status') return sendJson(res, 200, { mode: 'PAPER', oauthConfigured: oauthConfigured(process.env), ...dataStatus() });
  if (url.pathname === '/api/auth/deriv/start') {
    if (!oauthConfigured(process.env)) return sendJson(res, 503, { error: 'Deriv OAuth is not configured on this server.' });
    const pkce = createPkceChallenge();
    const payload = Buffer.from(JSON.stringify({ verifier: pkce.verifier, state: pkce.state, expiresAt: Date.now() + 600000 })).toString('base64url');
    res.setHeader('set-cookie', `deriv_oauth=${payload}; HttpOnly; Secure; SameSite=Lax; Path=/api/auth/deriv; Max-Age=600`);
    const auth = new URL(process.env.DERIV_OAUTH_AUTHORIZE_URL);
    auth.searchParams.set('client_id', process.env.DERIV_OAUTH_CLIENT_ID);
    auth.searchParams.set('redirect_uri', process.env.DERIV_REDIRECT_URI);
    auth.searchParams.set('response_type', 'code'); auth.searchParams.set('code_challenge_method', 'S256');
    auth.searchParams.set('code_challenge', pkce.challenge); auth.searchParams.set('state', pkce.state);
    res.writeHead(302, { location: auth.toString() }); return res.end();
  }
  if (url.pathname.startsWith('/api/')) return sendJson(res, 404, { error: 'Not found.' });
  const file = url.pathname === '/' ? 'index.html' : url.pathname.replace(/^\//, '');
  if (file.includes('..')) return sendJson(res, 400, { error: 'Invalid path.' });
  try { const body = await readFile(join(publicDir.pathname, file)); res.writeHead(200, { 'content-type': type[extname(file)] || 'application/octet-stream' }); res.end(body); }
  catch { sendJson(res, 404, { error: 'Not found.' }); }
}).listen(port, () => console.info(JSON.stringify({ event: 'server_started', port, realMoneyExecution: false })));

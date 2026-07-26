import { Env, ALLOWED_EMAIL } from '../shared';

const GOOGLE_AUTH = 'https://accounts.google.com/o/oauth2/v2/auth';
const GOOGLE_TOKEN = 'https://oauth2.googleapis.com/token';
const SESSION_COOKIE = 'stillhere_session';
const COOKIE_MAX_AGE = 86400; // 24 小时

/** 生成随机 state 防 CSRF */
function randomState(): string {
  const arr = new Uint8Array(16);
  crypto.getRandomValues(arr);
  return Array.from(arr, b => b.toString(16).padStart(2, '0')).join('');
}

/** HMAC-SHA256 签名会话 token */
async function signSession(email: string, secret: string): Promise<string> {
  const encoder = new TextEncoder();
  const key = await crypto.subtle.importKey(
    'raw', encoder.encode(secret),
    { name: 'HMAC', hash: 'SHA-256' }, false, ['sign']
  );
  const payload = `${email}:${Math.floor(Date.now() / 1000)}`;
  const sig = await crypto.subtle.sign('HMAC', key, encoder.encode(payload));
  const sigHex = Array.from(new Uint8Array(sig), b => b.toString(16).padStart(2, '0')).join('');
  return `${btoa(payload)}.${sigHex}`;
}

/** 验证会话 token */
async function verifySession(token: string, secret: string): Promise<string | null> {
  try {
    const [payloadB64, sigHex] = token.split('.');
    if (!payloadB64 || !sigHex) return null;
    const payload = atob(payloadB64);
    const encoder = new TextEncoder();
    const key = await crypto.subtle.importKey(
      'raw', encoder.encode(secret),
      { name: 'HMAC', hash: 'SHA-256' }, false, ['sign']
    );
    const sig = await crypto.subtle.sign('HMAC', key, encoder.encode(payload));
    const expectedHex = Array.from(new Uint8Array(sig), b => b.toString(16).padStart(2, '0')).join('');
    if (sigHex !== expectedHex) return null;
    const email = payload.split(':')[0];
    return email === ALLOWED_EMAIL ? email : null;
  } catch {
    return null;
  }
}

/** 从请求中提取已验证的 email，未登录返回 null */
export async function getSessionEmail(request: Request, env: Env): Promise<string | null> {
  const cookie = request.headers.get('Cookie') || '';
  const match = cookie.match(new RegExp(`${SESSION_COOKIE}=([^;]+)`));
  if (!match) return null;
  return verifySession(match[1], env.SESSION_SECRET);
}

/** 创建可修改 headers 的重定向 Response */
function redirect(location: string, status = 302): Response {
  return new Response(null, {
    status,
    headers: { Location: location },
  });
}

/** 重定向到 Google OAuth 登录页 */
export function redirectToGoogle(request: Request, env: Env): Response {
  const state = randomState();
  const params = new URLSearchParams({
    client_id: env.GOOGLE_CLIENT_ID,
    redirect_uri: getRedirectUri(request),
    response_type: 'code',
    scope: 'openid email',
    state,
    access_type: 'online',
    prompt: 'select_account',
  });
  const resp = redirect(`${GOOGLE_AUTH}?${params}`);
  resp.headers.set('Set-Cookie', `oauth_state=${state}; Path=/; HttpOnly; Secure; SameSite=Lax; Max-Age=300`);
  return resp;
}

/** 处理 Google OAuth 回调：拿 code 换 token，验证邮箱，设置会话 */
export async function handleCallback(request: Request, env: Env): Promise<Response> {
  const url = new URL(request.url);
  const code = url.searchParams.get('code');
  const state = url.searchParams.get('state');

  // 验证 state
  const cookies = request.headers.get('Cookie') || '';
  const stateMatch = cookies.match(/oauth_state=([^;]+)/);
  if (!state || !stateMatch || state !== stateMatch[1]) {
    return new Response('Invalid state', { status: 403 });
  }
  if (!code) {
    return new Response('Missing code', { status: 400 });
  }

  // 用 code 换 token
  const tokenResp = await fetch(GOOGLE_TOKEN, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({
      code,
      client_id: env.GOOGLE_CLIENT_ID,
      client_secret: env.GOOGLE_CLIENT_SECRET,
      redirect_uri: getRedirectUri(request),
      grant_type: 'authorization_code',
    }),
  });

  if (!tokenResp.ok) {
    const err = await tokenResp.text();
    return new Response(`OAuth failed: ${err}`, { status: 500 });
  }

  const tokens: any = await tokenResp.json();
  const idToken = tokens.id_token;
  if (!idToken) {
    return new Response('No id_token received', { status: 500 });
  }

  // 解码 id_token 获取 email
  const payloadB64 = idToken.split('.')[1];
  const payload = JSON.parse(atob(payloadB64));
  const email: string = payload.email;

  if (email !== ALLOWED_EMAIL) {
    return new Response(`Access denied: ${email} not authorized`, { status: 403 });
  }

  // 签发会话
  const sessionToken = await signSession(email, env.SESSION_SECRET);
  return new Response(null, {
    status: 302,
    headers: {
      Location: getDashboardUri(request),
      'Set-Cookie': `${SESSION_COOKIE}=${sessionToken}; Path=/; HttpOnly; Secure; SameSite=Lax; Max-Age=${COOKIE_MAX_AGE}`,
    },
  });
}

/** 登出 */
export function logout(request: Request): Response {
  return new Response(null, {
    status: 302,
    headers: {
      Location: getDashboardUri(request),
      'Set-Cookie': `${SESSION_COOKIE}=; Path=/; Max-Age=0`,
    },
  });
}

function getRedirectUri(request: Request): string {
  const url = new URL(request.url);
  return `${url.protocol}//${url.host}/auth/callback`;
}

function getDashboardUri(request: Request): string {
  const url = new URL(request.url);
  return `${url.protocol}//${url.host}/dashboard`;
}

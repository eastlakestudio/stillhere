import { Env } from '../shared';

const DEFAULT_APNS_BASE = 'https://api.push.apple.com';

export interface ApnsPayload {
  env: Env;
  deviceToken: string;
  title: string;
  body: string;
  badge?: number;
}

/**
 * 发送 APNs 推送通知（HTTP/2 Provider API）
 *
 * 使用 Web Crypto API 签名 ES256 JWT，无需依赖 npm apn 包。
 */
export async function sendApnsAlert(payload: ApnsPayload): Promise<void> {
  const { env, deviceToken, title, body, badge } = payload;

  const jwt = await signJwt({
    p8key: env.APNS_P8_KEY,
    keyId: env.APNS_KEY_ID,
    teamId: env.APNS_TEAM_ID,
  });

  const topic = env.APP_BUNDLE_ID;

  // 本地/测试环境可注入 APNS_BASE 指向 mock 服务器
  const apnsBase = (env as any).APNS_BASE || DEFAULT_APNS_BASE;

  const resp = await fetch(`${apnsBase}/3/device/${deviceToken}`, {
    method: 'POST',
    headers: {
      'Authorization': `bearer ${jwt}`,
      'apns-topic': topic,
      'apns-push-type': 'alert',
      'apns-priority': '10', // 立即
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      aps: {
        alert: { title, body },
        badge,
        sound: 'default',
      },
    }),
  });

  if (!resp.ok) {
    const text = await resp.text();
    throw new Error(`APNs HTTP ${resp.status}: ${text}`);
  }
}

// ---------- JWT 签名 ----------

interface JwtParams {
  p8key: string;
  keyId: string;
  teamId: string;
}

async function signJwt(params: JwtParams): Promise<string> {
  const { p8key, keyId, teamId } = params;

  // 清理 .p8 文件的头尾标记和换行
  const keyContent = p8key
    .replace(/-----BEGIN PRIVATE KEY-----/, '')
    .replace(/-----END PRIVATE KEY-----/, '')
    .replace(/\s+/g, '');

  // 导入 EC 私钥
  const binaryKey = Uint8Array.from(atob(keyContent), (c) => c.charCodeAt(0));
  const cryptoKey = await crypto.subtle.importKey(
    'pkcs8',
    binaryKey,
    { name: 'ECDSA', namedCurve: 'P-256' },
    false,
    ['sign']
  );

  // 构造 JWT header + payload
  const now = Math.floor(Date.now() / 1000);
  const header = { alg: 'ES256', kid: keyId };
  const payload = { iss: teamId, iat: now };

  const encodedHeader = base64url(JSON.stringify(header));
  const encodedPayload = base64url(JSON.stringify(payload));
  const signingInput = `${encodedHeader}.${encodedPayload}`;

  // 签名
  const signature = await crypto.subtle.sign(
    { name: 'ECDSA', hash: 'SHA-256' },
    cryptoKey,
    new TextEncoder().encode(signingInput)
  );

  const encodedSignature = base64url(String.fromCharCode(...new Uint8Array(signature)));

  return `${signingInput}.${encodedSignature}`;
}

function base64url(input: string): string {
  return btoa(input).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

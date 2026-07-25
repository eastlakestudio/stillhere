import { Env, jsonResponse, now } from '../shared';

/**
 * POST /generate-bind-code
 * 
 * 生成 6 位绑定码（5 分钟有效），返回 bindCode + qrContent。
 * Body: { userId }
 */
export async function handleBindCode(request: Request, env: Env): Promise<Response> {
  if (request.method !== 'POST') {
    return jsonResponse({ error: 'method not allowed' }, 405);
  }

  let body: { userId?: string };
  try {
    body = await request.json();
  } catch {
    return jsonResponse({ error: 'invalid JSON' }, 400);
  }

  const { userId } = body;
  if (!userId || typeof userId !== 'string') {
    return jsonResponse({ error: 'userId is required' }, 400);
  }

  // 确保用户存在
  await env.DB.prepare(`
    INSERT OR IGNORE INTO users (id, last_active_time) VALUES (?1, 0)
  `).bind(userId).run();

  // 清除该 userId 历史未消费码
  await env.DB.prepare(`
    DELETE FROM bind_codes WHERE user_id = ?1
  `).bind(userId).run();

  // 生成 6 位随机码（D1 不支持 random，在应用层生成）
  const code = String(Math.floor(100000 + Math.random() * 900000)); // 100000-999999
  const ts = now();
  const expiresAt = ts + 5 * 60; // 5 分钟

  await env.DB.prepare(`
    INSERT INTO bind_codes (code, user_id, created_at, expires_at)
    VALUES (?1, ?2, ?3, ?4)
  `).bind(code, userId, ts, expiresAt).run();

  // 二维码内容：JSON 串，供「还在」端扫码解析
  const qrContent = JSON.stringify({ code, userId });

  return jsonResponse({ bindCode: code, qrContent, expiresAt });
}

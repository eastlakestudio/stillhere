import { Env, jsonResponse, now } from '../shared';

/**
 * POST /heartbeat
 * 
 * iOS 端每次唤醒事件上报，更新 last_active_time。
 * Body: { userId, deviceToken?, isCharging? }
 */
export async function handleHeartbeat(request: Request, env: Env): Promise<Response> {
  if (request.method !== 'POST') {
    return jsonResponse({ error: 'method not allowed' }, 405);
  }

  let body: { userId?: string; deviceToken?: string; isCharging?: boolean };
  try {
    body = await request.json();
  } catch {
    return jsonResponse({ error: 'invalid JSON' }, 400);
  }

  const { userId, deviceToken, isCharging } = body;
  if (!userId || typeof userId !== 'string') {
    return jsonResponse({ error: 'userId is required' }, 400);
  }

  const ts = now();

  // upsert: 新用户 insert，已存在 update
  await env.DB.prepare(`
    INSERT INTO users (id, device_token, last_active_time, is_alerted, is_charging)
    VALUES (?1, ?2, ?3, 0, ?4)
    ON CONFLICT(id) DO UPDATE SET
      device_token = COALESCE(?2, device_token),
      last_active_time = ?3,
      is_alerted = 0,
      is_charging = ?4
  `)
    .bind(userId, deviceToken || null, ts, isCharging ? 1 : 0)
    .run();

  return jsonResponse({ ok: true, timestamp: ts });
}

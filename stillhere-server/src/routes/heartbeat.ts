import { Env, jsonResponse, now } from '../shared';

/**
 * POST /heartbeat
 *
 * 客户端周期上报（约每小时一次），更新 last_active_time。
 * 不再由服务端判超时告警，告警逻辑移至客户端本地。
 * Body: { userId, careCode?, deviceToken?, isCharging? }
 */
export async function handleHeartbeat(request: Request, env: Env): Promise<Response> {
  if (request.method !== 'POST') {
    return jsonResponse({ error: 'method not allowed' }, 405);
  }

  let body: { userId?: string; careCode?: string; deviceToken?: string; isCharging?: boolean };
  try {
    body = await request.json();
  } catch {
    return jsonResponse({ error: 'invalid JSON' }, 400);
  }

  const { userId, careCode, deviceToken, isCharging } = body;
  if (!userId || typeof userId !== 'string') {
    return jsonResponse({ error: 'userId is required' }, 400);
  }

  const ts = now();

  // 从 Cloudflare 边缘节点获取 IP 归属城市
  const city = (request as any).cf?.city || null;
  const country = (request as any).cf?.country || null;
  const loc = city && country ? `${city}, ${country}` : (city || country || null);

  // 检查旧状态（用于在线恢复检测）
  const oldUser = await env.DB.prepare(
    'SELECT online_status FROM users WHERE id = ?1'
  ).bind(userId).first<{ online_status: string }>();

  // 标准化 careCode（供 users.care_code 冗余存储）
  const code = careCode?.trim().toUpperCase().slice(0, 6) || null;
  const validCode = code && code.length === 6 ? code : null;

  // upsert: 新用户 insert，已存在 update（同时标记为 online）
  await env.DB.prepare(`
    INSERT INTO users (id, care_code, device_token, last_active_time, is_charging, last_city, online_status)
    VALUES (?1, ?2, ?3, ?4, ?5, ?6, 'online')
    ON CONFLICT(id) DO UPDATE SET
      care_code = COALESCE(?2, care_code),
      device_token = COALESCE(?3, device_token),
      last_active_time = ?4,
      is_charging = ?5,
      last_city = COALESCE(?6, last_city),
      online_status = 'online'
  `)
    .bind(userId, validCode, deviceToken || null, ts, isCharging ? 1 : 0, loc)
    .run();

  // 如果用户之前是离线状态 → 插入恢复告警，通知关心人
  const wasOffline = oldUser?.online_status === 'offline';

  // 查询有多少人在关心当前用户（使用客户端上报的 careCode 精确匹配）
  let caredByCount = 0;
  if (validCode) {
    const caredByResult = await env.DB.prepare(
      'SELECT COUNT(*) as count FROM care_relations WHERE to_code = ?1'
    ).bind(validCode).first<{ count: number }>();
    caredByCount = caredByResult?.count || 0;

    // 如果之前离线，插入上线恢复告警
    if (wasOffline && caredByCount > 0) {
      await env.DB.prepare(`
        INSERT INTO alerts (user_id, care_code, alert_type, created_at, is_resolved)
        VALUES (?1, ?2, 'online', ?3, 0)
      `).bind(userId, validCode, ts).run();
    }
  }

  return jsonResponse({ ok: true, timestamp: ts, caredByCount });
}

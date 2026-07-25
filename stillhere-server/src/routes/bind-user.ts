import { Env, jsonResponse, now } from '../shared';

/**
 * POST /bind-user
 * 
 * 「还在」端扫码或手输 6 位码后，绑定被监控人。
 * Body: { bindCode, followerId }
 */
export async function handleBindUser(request: Request, env: Env): Promise<Response> {
  if (request.method !== 'POST') {
    return jsonResponse({ error: 'method not allowed' }, 405);
  }

  let body: { bindCode?: string; followerId?: string };
  try {
    body = await request.json();
  } catch {
    return jsonResponse({ error: 'invalid JSON' }, 400);
  }

  const { bindCode, followerId } = body;
  if (!bindCode || !followerId) {
    return jsonResponse({ error: 'bindCode and followerId are required' }, 400);
  }

  const ts = now();

  // 查找有效且未过期的绑定码
  const codeRecord = await env.DB.prepare(`
    SELECT user_id, expires_at FROM bind_codes WHERE code = ?1
  `).bind(bindCode).first<{ user_id: string; expires_at: number }>();

  if (!codeRecord) {
    return jsonResponse({ error: 'invalid bind code' }, 404);
  }
  if (codeRecord.expires_at < ts) {
    // 过期码清理
    await env.DB.prepare(`DELETE FROM bind_codes WHERE code = ?1`).bind(bindCode).run();
    return jsonResponse({ error: 'bind code expired' }, 410);
  }

  const monitoredId = codeRecord.user_id;

  // 禁止自绑
  if (monitoredId === followerId) {
    return jsonResponse({ error: 'cannot bind yourself' }, 400);
  }

  // 禁止重复绑定（被监控人已有 contact_id）
  const user = await env.DB.prepare(`
    SELECT contact_id FROM users WHERE id = ?1
  `).bind(monitoredId).first<{ contact_id: string | null }>();
  if (user?.contact_id) {
    return jsonResponse({ error: 'already bound' }, 409);
  }

  // 确保 follower 用户存在
  await env.DB.prepare(`
    INSERT OR IGNORE INTO users (id, last_active_time) VALUES (?1, 0)
  `).bind(followerId).run();

  // 执行绑定：更新被监控人 contact_id
  await env.DB.prepare(`
    UPDATE users SET contact_id = ?1, bind_at = ?2 WHERE id = ?3
  `).bind(followerId, ts, monitoredId).run();

  // 删除已消费绑定码
  await env.DB.prepare(`DELETE FROM bind_codes WHERE code = ?1`).bind(bindCode).run();

  return jsonResponse({ ok: true, monitoredId });
}

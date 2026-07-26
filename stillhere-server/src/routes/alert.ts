import { Env, jsonResponse, now } from '../shared';
import { sendApnsAlert } from '../lib/apns';

/**
 * POST /alert
 *
 * 被关心者 APP 检测到本地告警时上报，Server 转发给所有关心人。
 * Body: { userId, careCode, idleMinutes?, isCharging? }
 *
 * POST /alert/cancel
 *
 * 被关心者恢复活动时上报，Server 将所有未解决告警标记为已恢复，
 * 并通知关心人。
 * Body: { userId, careCode }
 */
export async function handleAlert(request: Request, env: Env): Promise<Response> {
  if (request.method !== 'POST') {
    return jsonResponse({ error: 'method not allowed' }, 405);
  }

  const url = new URL(request.url);
  const isCancel = url.pathname === '/alert/cancel';

  let body: { userId?: string; careCode?: string; idleMinutes?: number; isCharging?: boolean };
  try {
    body = await request.json();
  } catch {
    return jsonResponse({ error: 'invalid JSON' }, 400);
  }

  const { userId, careCode, idleMinutes, isCharging } = body;
  if (!userId || !careCode) {
    return jsonResponse({ error: 'userId and careCode are required' }, 400);
  }

  const code = careCode.trim().toUpperCase();
  if (code.length !== 6) {
    return jsonResponse({ error: 'careCode must be 6 characters' }, 400);
  }

  const ts = now();

  if (isCancel) {
    // 取消告警：标记所有未解决告警为已恢复
    await env.DB.prepare(`
      UPDATE alerts SET is_resolved = 1, resolved_at = ?1
      WHERE user_id = ?2 AND care_code = ?3 AND is_resolved = 0
    `).bind(ts, userId, code).run();

    // 查询关心人列表
    const carers = await env.DB.prepare(
      'SELECT cr.from_device_id, u.device_token FROM care_relations cr LEFT JOIN users u ON u.id = cr.from_device_id WHERE cr.to_code = ?1'
    ).bind(code).all<{ from_device_id: string; device_token: string | null }>();

    if (carers.results?.length) {
      for (const carer of carers.results) {
        if (carer.device_token) {
          try {
            await sendApnsAlert({
              env,
              deviceToken: carer.device_token,
              title: '安好 · 活动已恢复',
              body: '您关心的用户已恢复活动 ✓',
              badge: 0,
            });
          } catch (e: any) {
            console.error(`APNs recovery push failed: ${e.message}`);
          }
        }
      }
    }

    return jsonResponse({ ok: true, action: 'cancel' });
  }

  // 新增告警
  await env.DB.prepare(`
    INSERT INTO alerts (user_id, care_code, alert_type, idle_minutes, is_charging, created_at)
    VALUES (?1, ?2, 'idle', ?3, ?4, ?5)
  `).bind(userId, code, idleMinutes || 0, isCharging ? 1 : 0, ts).run();

  // 查询关心人列表并推送
  const carers = await env.DB.prepare(
    'SELECT cr.from_device_id, u.device_token FROM care_relations cr LEFT JOIN users u ON u.id = cr.from_device_id WHERE cr.to_code = ?1'
  ).bind(code).all<{ from_device_id: string; device_token: string | null }>();

  let pushedCount = 0;
  if (carers.results?.length) {
    for (const carer of carers.results) {
      if (carer.device_token) {
        try {
          const bodyText = isCharging
            ? `您关心的用户已 ${idleMinutes || 0} 分钟无活动（充电中）`
            : `您关心的用户已 ${idleMinutes || 0} 分钟无活动`;

          await sendApnsAlert({
            env,
            deviceToken: carer.device_token,
            title: '安好 · 活动超时提醒',
            body: bodyText,
            badge: 1,
          });
          pushedCount++;
        } catch (e: any) {
          console.error(`APNs alert push failed: ${e.message}`);
        }
      }
    }
  }

  return jsonResponse({ ok: true, caredByCount: carers.results?.length || 0, pushed: pushedCount });
}

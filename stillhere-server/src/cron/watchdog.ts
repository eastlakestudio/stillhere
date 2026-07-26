import { Env, now } from '../shared';
import { sendApnsAlert } from '../lib/apns';

/**
 * Cron 触发器：每 5 分钟执行一次。
 *
 * 职责：检测超过 24 小时未心跳的用户，标记为离线并推送通知给关心人。
 * 不再做分时段阈值判断（告警由客户端本地裁决并 POST /alert 上报）。
 */
export async function runWatchdog(env: Env): Promise<void> {
  const ts = now();
  const offlineThreshold = ts - 86400; // 24 小时

  // 查询在线但心跳超时的用户
  const offlineUsers = await env.DB.prepare(`
    SELECT id, last_active_time
    FROM users
    WHERE online_status = 'online'
      AND last_active_time > 0
      AND last_active_time < ?1
  `).bind(offlineThreshold).all<{ id: string; last_active_time: number }>();

  for (const user of offlineUsers.results || []) {
    // 标记为离线
    await env.DB.prepare(
      'UPDATE users SET online_status = \'offline\' WHERE id = ?1'
    ).bind(user.id).run();

    // 从 userId 后6位反向查 care_code（best effort）
    // 优先从 alerts 表查历史 care_code
    let careCode: string | null = null;
    const alertRow = await env.DB.prepare(
      'SELECT care_code FROM alerts WHERE user_id = ?1 ORDER BY created_at DESC LIMIT 1'
    ).bind(user.id).first<{ care_code: string }>();
    if (alertRow?.care_code) {
      careCode = alertRow.care_code;
    } else {
      // 回退：从 care_relations 反查（依赖之前 registerCare 留下的记录）
      const codeFromId = user.id.replace(/-/g, '').slice(-6).toUpperCase();
      const relRow = await env.DB.prepare(
        'SELECT to_code FROM care_relations WHERE to_code = ?1 LIMIT 1'
      ).bind(codeFromId).first<{ to_code: string }>();
      if (relRow?.to_code) {
        careCode = relRow.to_code;
      }
    }

    if (careCode) {
      // 插入离线告警
      await env.DB.prepare(`
        INSERT INTO alerts (user_id, care_code, alert_type, created_at, is_resolved)
        VALUES (?1, ?2, 'offline', ?3, 0)
      `).bind(user.id, careCode, ts).run();

      // 查询关心人并推送
      const carers = await env.DB.prepare(
        'SELECT cr.from_device_id, u.device_token FROM care_relations cr LEFT JOIN users u ON u.id = cr.from_device_id WHERE cr.to_code = ?1'
      ).bind(careCode).all<{ from_device_id: string; device_token: string | null }>();

      for (const carer of carers.results || []) {
        if (carer.device_token) {
          try {
            await sendApnsAlert({
              env,
              deviceToken: carer.device_token,
              title: '安好 · 离线提醒',
              body: '您关心的用户可能已离线超过 24 小时',
              badge: 1,
            });
          } catch (e: any) {
            console.error(`APNs offline push failed: ${e.message}`);
          }
        }
      }
    }
  }
}

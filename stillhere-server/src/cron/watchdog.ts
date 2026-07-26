import { Env, now } from '../shared';
import { sendApnsAlert } from '../lib/apns';

/**
 * Cron 触发器：每 5 分钟执行一次。
 *
 * 职责：
 *   1. 检测超过 2 小时未心跳的用户，插入「心跳过期」告警（不标记离线）
 *   2. 检测超过 24 小时未心跳的用户，标记为离线并推送通知给关心人。
 * 不再做分时段阈值判断（告警由客户端本地裁决并 POST /alert 上报）。
 */
export async function runWatchdog(env: Env): Promise<void> {
  const ts = now();
  const staleThreshold = ts - 7200;   // 2 小时
  const offlineThreshold = ts - 86400; // 24 小时

  // ── Phase 1: 2 小时心跳过期（stale），仅插入告警，不标记离线 ──
  const staleUsers = await env.DB.prepare(`
    SELECT id, last_active_time
    FROM users
    WHERE online_status = 'online'
      AND last_active_time > 0
      AND last_active_time < ?1
      AND last_active_time >= ?2
  `).bind(staleThreshold, offlineThreshold).all<{ id: string; last_active_time: number }>();

  for (const user of staleUsers.results || []) {
    // 从 userId 后6位反向查 care_code
    let careCode: string | null = null;
    const alertRow = await env.DB.prepare(
      'SELECT care_code FROM alerts WHERE user_id = ?1 ORDER BY created_at DESC LIMIT 1'
    ).bind(user.id).first<{ care_code: string }>();
    if (alertRow?.care_code) {
      careCode = alertRow.care_code;
    } else {
      const codeFromId = user.id.replace(/-/g, '').slice(-6).toUpperCase();
      const relRow = await env.DB.prepare(
        'SELECT to_code FROM care_relations WHERE to_code = ?1 LIMIT 1'
      ).bind(codeFromId).first<{ to_code: string }>();
      if (relRow?.to_code) {
        careCode = relRow.to_code;
      }
    }

    if (!careCode) continue;

    // 避免重复插入：检查最近 2 小时内是否已有未解决的 stale/offline 告警
    const recentAlert = await env.DB.prepare(
      'SELECT id FROM alerts WHERE user_id = ?1 AND care_code = ?2 AND is_resolved = 0 AND alert_type IN (\'stale\', \'offline\') AND created_at > ?3 LIMIT 1'
    ).bind(user.id, careCode, ts - 7200).first<{ id: number }>();
    if (recentAlert) continue;

    // 插入心跳过期告警
    const idleMinutes = Math.floor((ts - user.last_active_time) / 60);
    await env.DB.prepare(`
      INSERT INTO alerts (user_id, care_code, alert_type, idle_minutes, is_charging, created_at, is_resolved)
      VALUES (?1, ?2, 'stale', ?3, 0, ?4, 0)
    `).bind(user.id, careCode, idleMinutes, ts).run();

    console.log(`[watchdog] stale alert inserted for user ${user.id}, idle ${idleMinutes}min`);
  }

  // ── Phase 2: 24 小时离线（原有逻辑） ──

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

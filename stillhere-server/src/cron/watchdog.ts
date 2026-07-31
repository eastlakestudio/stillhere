import { Env, now } from '../shared';
import { sendApnsAlert } from '../lib/apns';

/**
 * Cron 触发器：每 5 分钟执行一次。
 *
 * 职责：
 *   1. 检测超过 2 小时未心跳的用户，插入「心跳过期」告警并推送通知给关心人
 *   2. 检测超过 24 小时未心跳的用户，标记为离线并推送通知给关心人
 *
 * careCode 通过 users.care_code 冗余字段获取（heartbeat 上报时已存储），
 * 不再做不可靠的 deviceId 后缀反查。
 */
export async function runWatchdog(env: Env): Promise<void> {
  const ts = now();
  const staleThreshold = ts - 7200;   // 2 小时
  const offlineThreshold = ts - 86400; // 24 小时

  // ── Phase 1: 2 小时心跳过期（stale），插入告警 + 推送 ──
  const staleUsers = await env.DB.prepare(`
    SELECT id, care_code, last_active_time
    FROM users
    WHERE online_status = 'online'
      AND last_active_time > 0
      AND last_active_time < ?1
      AND last_active_time >= ?2
      AND care_code IS NOT NULL
  `).bind(staleThreshold, offlineThreshold).all<{ id: string; care_code: string; last_active_time: number }>();

  for (const user of staleUsers.results || []) {
    // 避免重复插入：检查最近 2 小时内是否已有未解决的 stale/offline 告警
    const recentAlert = await env.DB.prepare(
      "SELECT id FROM alerts WHERE user_id = ?1 AND care_code = ?2 AND is_resolved = 0 AND alert_type IN ('stale', 'offline') AND created_at > ?3 LIMIT 1"
    ).bind(user.id, user.care_code, ts - 7200).first<{ id: number }>();
    if (recentAlert) continue;

    // 插入心跳过期告警
    const idleMinutes = Math.floor((ts - user.last_active_time) / 60);
    await env.DB.prepare(`
      INSERT INTO alerts (user_id, care_code, alert_type, idle_minutes, is_charging, created_at, is_resolved)
      VALUES (?1, ?2, 'stale', ?3, 0, ?4, 0)
    `).bind(user.id, user.care_code, idleMinutes, ts).run();

    // 推送给关心人
    await notifyCarers(env, user.care_code, '安好 · 心跳异常', `您关心的用户已 ${idleMinutes} 分钟未上报状态`, 1);
    console.log(`[watchdog] stale alert + push for user ${user.id}, idle ${idleMinutes}min`);
  }

  // ── Phase 2: 24 小时离线，标记 offline + 插入告警 + 推送 ──
  const offlineUsers = await env.DB.prepare(`
    SELECT id, care_code, last_active_time
    FROM users
    WHERE online_status = 'online'
      AND last_active_time > 0
      AND last_active_time < ?1
  `).bind(offlineThreshold).all<{ id: string; care_code: string | null; last_active_time: number }>();

  for (const user of offlineUsers.results || []) {
    // 标记为离线
    await env.DB.prepare(
      'UPDATE users SET online_status = \'offline\' WHERE id = ?1'
    ).bind(user.id).run();

    // 获取 careCode（冗余字段优先，否则从 alerts 历史反查）
    let careCode = user.care_code;
    if (!careCode) {
      const alertRow = await env.DB.prepare(
        'SELECT care_code FROM alerts WHERE user_id = ?1 ORDER BY created_at DESC LIMIT 1'
      ).bind(user.id).first<{ care_code: string }>();
      careCode = alertRow?.care_code || null;
    }
    if (!careCode) continue;

    // 插入离线告警
    await env.DB.prepare(`
      INSERT INTO alerts (user_id, care_code, alert_type, created_at, is_resolved)
      VALUES (?1, ?2, 'offline', ?3, 0)
    `).bind(user.id, careCode, ts).run();

    // 推送给关心人
    await notifyCarers(env, careCode, '安好 · 离线提醒', '您关心的用户可能已离线超过 24 小时', 1);
    console.log(`[watchdog] offline alert + push for user ${user.id}`);
  }
}

/** 查询关心人并 APNs 推送 */
async function notifyCarers(env: Env, careCode: string, title: string, body: string, badge: number): Promise<void> {
  const carers = await env.DB.prepare(
    'SELECT cr.from_device_id, u.device_token FROM care_relations cr LEFT JOIN users u ON u.id = cr.from_device_id WHERE cr.to_code = ?1'
  ).bind(careCode).all<{ from_device_id: string; device_token: string | null }>();

  for (const carer of carers.results || []) {
    if (carer.device_token) {
      try {
        await sendApnsAlert({
          env,
          deviceToken: carer.device_token,
          title,
          body,
          badge,
        });
      } catch (e: any) {
        console.error(`APNs push failed: ${e.message}`);
      }
    }
  }
}

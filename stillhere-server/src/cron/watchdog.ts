import { Env, now } from '../shared';
import { sendApnsAlert } from '../lib/apns';

/**
 * Cron 触发器：每 5 分钟执行一次。
 *
 * 职责：
 *   0. 守护时段阈值裁决：用户上传了守护配置（device_config）时，
 *      在守护时段内超过阈值无心跳 → 插入 idle 告警 + 推送关心人
 *   1. 兜底：超过 2 小时未心跳且无配置的用户，插入 stale 告警 + 推送
 *   2. 超过 24 小时未心跳，标记离线 + 插入 offline 告警 + 推送
 */
export async function runWatchdog(env: Env): Promise<void> {
  const ts = now();
  const staleThreshold = ts - 7200;   // 2 小时
  const offlineThreshold = ts - 86400; // 24 小时

  // ── Phase 0: 守护时段阈值裁决（依赖 device_config）──
  // 候选：在线、有 care_code、超过 5 分钟未心跳（刚心跳的无需检查）
  const candidates = await env.DB.prepare(`
    SELECT id, care_code, last_active_time
    FROM users
    WHERE online_status = 'online'
      AND last_active_time > 0
      AND care_code IS NOT NULL
      AND last_active_time < ?1
  `).bind(ts - 300).all<{ id: string; care_code: string; last_active_time: number }>();

  for (const user of candidates.results || []) {
    // 读取用户守护配置（客户端变更时已上传）
    const cfgRow = await env.DB.prepare(
      'SELECT config_json FROM device_config WHERE device_id = ?1'
    ).bind(user.id).first<{ config_json: string }>();
    if (!cfgRow?.config_json) continue; // 无配置 → 走 Phase 1 stale 兜底

    let cfg: any;
    try { cfg = JSON.parse(cfgRow.config_json); } catch { continue; }

    const threshold = Number(cfg.idleAlertMinutes) || 30;
    const windows: any[] = Array.isArray(cfg.monitoringWindows) ? cfg.monitoringWindows : [];
    if (windows.length === 0) continue; // 未配置时段 → 兜底

    // 用户本地时间（时区偏移由客户端上传，默认 UTC+8）
    const tzRaw = Number(cfg.timezoneOffsetMinutes);
    const tzMinutes = Number.isFinite(tzRaw) ? tzRaw : 480;
    const local = new Date((ts + tzMinutes * 60) * 1000);
    const localMinutes = local.getUTCHours() * 60 + local.getUTCMinutes();

    // 是否在守护时段内（支持跨日窗口，如 22:00-06:00）
    const inWindow = windows.some(w => windowContains(w, localMinutes));
    if (!inWindow) continue;

    // 空闲是否超阈值
    const idleMinutes = Math.floor((ts - user.last_active_time) / 60);
    if (idleMinutes <= threshold) continue;

    // 去重：2 小时内已有未解决的 idle/stale 告警（含客户端上报的）则跳过
    const dup = await env.DB.prepare(
      "SELECT id FROM alerts WHERE user_id = ?1 AND is_resolved = 0 AND alert_type IN ('idle', 'stale') AND created_at > ?2 LIMIT 1"
    ).bind(user.id, ts - 7200).first<{ id: number }>();
    if (dup) continue;

    await env.DB.prepare(`
      INSERT INTO alerts (user_id, care_code, alert_type, idle_minutes, is_charging, created_at, is_resolved)
      VALUES (?1, ?2, 'idle', ?3, 0, ?4, 0)
    `).bind(user.id, user.care_code, idleMinutes, ts).run();

    await notifyCarers(env, user.care_code, '晴好 · 活动超时提醒', `守护时段内已 ${idleMinutes} 分钟无活动`, 1);
    console.log(`[watchdog] server-adjudicated idle alert for ${user.id}, idle ${idleMinutes}min`);
  }

  // ── Phase 1: 2 小时心跳过期（stale，无配置用户的兜底），插入告警 + 推送 ──
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
    // 有配置的用户已由 Phase 0 按自身阈值处理
    const hasCfg = await env.DB.prepare(
      'SELECT device_id FROM device_config WHERE device_id = ?1'
    ).bind(user.id).first<{ device_id: string }>();
    if (hasCfg) continue;

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
    await notifyCarers(env, user.care_code, '晴好 · 心跳异常', `您关心的用户已 ${idleMinutes} 分钟未上报状态`, 1);
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

    if (!user.care_code) continue;

    // 插入离线告警
    await env.DB.prepare(`
      INSERT INTO alerts (user_id, care_code, alert_type, created_at, is_resolved)
      VALUES (?1, ?2, 'offline', ?3, 0)
    `).bind(user.id, user.care_code, ts).run();

    // 推送给关心人
    await notifyCarers(env, user.care_code, '晴好 · 离线提醒', '您关心的用户可能已离线超过 24 小时', 1);
    console.log(`[watchdog] offline alert + push for user ${user.id}`);
  }
}

/** 判断 localMinutes 是否在窗口内（支持跨日，如 22:00-06:00） */
function windowContains(w: any, m: number): boolean {
  const s = (Number(w.startHour) || 0) * 60 + (Number(w.startMinute) || 0);
  const e = (Number(w.endHour) || 0) * 60 + (Number(w.endMinute) || 0);
  if (s === e) return true; // 全天
  return s < e ? (m >= s && m < e) : (m >= s || m < e);
}

/** 查询关心人并 APNs 推送 */
export async function notifyCarers(env: Env, careCode: string, title: string, body: string, badge: number): Promise<void> {
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

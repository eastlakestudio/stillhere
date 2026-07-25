import { Env, now } from '../shared';
import { sendApnsAlert } from '../lib/apns';

/**
 * Cron 触发器：每 5 分钟执行一次。
 * 查询"已绑定 + 未告警 + 可能超时"的用户，分时段阈值判定，超时则推送 APNs。
 */
export async function runWatchdog(env: Env): Promise<void> {
  const ts = now();

  // 查询候选用户：已绑定 + 未告警
  const users = await env.DB.prepare(`
    SELECT id, device_token, last_active_time, is_charging, contact_id, threshold_minutes
    FROM users
    WHERE contact_id IS NOT NULL AND is_alerted = 0
  `).all<UserRow>();

  if (!users.results.length) return;

  for (const user of users.results) {
    // 分时段阈值 + 充电豁免
    const threshold = getThresholdMinutes(user.is_charging === 1, ts);
    const elapsed = Math.floor((ts - user.last_active_time) / 60); // 分钟

    if (elapsed < threshold) continue; // 未超时

    // 超时 → 标记告警
    await env.DB.prepare(`
      UPDATE users SET is_alerted = 1 WHERE id = ?1
    `).bind(user.id).run();

    // 推送 APNs 给关注人（还在端）
    if (user.device_token) {
      try {
        await sendApnsAlert({
          env,
          deviceToken: user.device_token,
          title: '「安好」活动超时提醒',
          body: `您关心的用户已 ${elapsed} 分钟无活动记录`,
          badge: 1,
        });
      } catch (e: any) {
        console.error(`APNs push failed for user ${user.id}: ${e.message}`);
        // 推送失败不标记 is_alerted，允许下次重试
        await env.DB.prepare(`
          UPDATE users SET is_alerted = 0 WHERE id = ?1
        `).bind(user.id).run();
      }
    }
  }
}

// ---------- 阈值策略 ----------

/**
 * 分时段阈值 + 充电豁免
 * - 白天（8:00-22:00）: 120 分钟
 * - 夜间（22:00-8:00）: 480 分钟
 * - 充电中：阈值 × 2
 */
function getThresholdMinutes(isCharging: boolean, ts: number): number {
  const hour = new Date(ts * 1000).getHours();
  const base = (hour >= 22 || hour < 8) ? 480 : 120;
  return isCharging ? base * 2 : base;
}

interface UserRow {
  id: string;
  device_token: string | null;
  last_active_time: number;
  is_charging: number;
  contact_id: string;
  threshold_minutes: number;
}

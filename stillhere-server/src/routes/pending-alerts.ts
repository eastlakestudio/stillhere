import { Env, jsonResponse } from '../shared';

/**
 * GET /pending-alerts?deviceId=xxx
 *
 * Android 关心人轮询拉取待处理的告警（替代 FCM 推送）。
 * 返回该用户关心的所有人的告警列表（按时间倒序，最多 50 条）。
 */
export async function handlePendingAlerts(request: Request, env: Env): Promise<Response> {
  if (request.method !== 'GET') {
    return jsonResponse({ error: 'method not allowed' }, 405);
  }

  const url = new URL(request.url);
  const deviceId = url.searchParams.get('deviceId');
  if (!deviceId) {
    return jsonResponse({ error: 'deviceId query parameter is required' }, 400);
  }

  // 查该用户关心的所有关心码
  const codes = await env.DB.prepare(
    'SELECT DISTINCT to_code FROM care_relations WHERE from_device_id = ?1'
  ).bind(deviceId).all<{ to_code: string }>();

  if (!codes.results?.length) {
    return jsonResponse({ alerts: [] });
  }

  const codeList = codes.results.map(r => r.to_code);

  // 构建 IN 子句占位符
  const placeholders = codeList.map(() => '?').join(',');

  // 查询所有相关告警（最近 50 条，按时间倒序）
  const alerts = await env.DB.prepare(`
    SELECT 
      a.id,
      a.user_id,
      a.care_code,
      a.alert_type,
      a.idle_minutes,
      a.is_charging,
      a.is_resolved,
      a.created_at,
      a.resolved_at
    FROM alerts a
    WHERE a.care_code IN (${placeholders})
    ORDER BY a.created_at DESC
    LIMIT 50
  `).bind(...codeList).all<{
    id: number;
    user_id: string;
    care_code: string;
    alert_type: string;
    idle_minutes: number;
    is_charging: number;
    is_resolved: number;
    created_at: number;
    resolved_at: number | null;
  }>();

  return jsonResponse({
    alerts: (alerts.results || []).map(a => ({
      id: a.id,
      alertType: a.alert_type,
      caredName: '用户',
      idleMinutes: a.idle_minutes,
      isCharging: a.is_charging === 1,
      isResolved: a.is_resolved === 1,
      createdAt: a.created_at,
      resolvedAt: a.resolved_at,
    })),
  });
}

import { Env, jsonResponse, toCareCode } from '../shared';

/**
 * POST /cared-status
 *
 * 查询一组关心码对应设备的最近活动状态。
 * Body: { codes: string[] }
 * 返回: { [code]: { lastActive: number | null, isCharging: boolean, city: string | null } }
 */
export async function handleCaredStatus(request: Request, env: Env): Promise<Response> {
  if (request.method !== 'POST') {
    return jsonResponse({ error: 'method not allowed' }, 405);
  }

  let body: { codes?: string[] };
  try {
    body = await request.json();
  } catch {
    return jsonResponse({ error: 'invalid JSON' }, 400);
  }

  const { codes } = body;
  if (!codes || !Array.isArray(codes) || codes.length === 0) {
    return jsonResponse({ error: 'codes array is required' }, 400);
  }

  // 标准化 + 去重
  const normalized = [...new Set(codes.map(c => c.trim().toUpperCase().slice(0, 6)))];
  if (normalized.some(c => c.length !== 6)) {
    return jsonResponse({ error: 'each code must be 6 characters' }, 400);
  }

  // 查询 users 表：通过 userId 后6位匹配
  // SQLite 不支持 LIKE 多个模式，改用多个 OR 或 IN 子查询
  // 构建占位符：每个 code 匹配 userId 后6位
  const result: Record<string, { lastActive: number | null; isCharging: boolean; city: string | null }> = {};

  // 先初始化所有 code 为未找到
  for (const code of normalized) {
    result[code] = { lastActive: null, isCharging: false, city: null };
  }

  // 查询所有用户，在应用层匹配后6位
  const { results } = await env.DB.prepare(
    'SELECT id, last_active_time, is_charging, last_city FROM users WHERE last_active_time IS NOT NULL ORDER BY last_active_time DESC LIMIT 500'
  ).all<{ id: string; last_active_time: number | null; is_charging: number; last_city: string | null }>();

  if (results) {
    for (const row of results) {
      const userCode = await toCareCode(row.id);
      if (normalized.includes(userCode)) {
        result[userCode] = {
          lastActive: row.last_active_time,
          isCharging: row.is_charging === 1,
          city: row.last_city,
        };
      }
    }
  }

  return jsonResponse({ codes: result });
}

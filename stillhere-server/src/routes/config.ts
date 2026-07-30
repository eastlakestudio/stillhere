import { Env, jsonResponse } from '../shared';

/**
 * POST /config — 保存设备配置（守护时段、告警阈值等）
 * GET  /config?deviceId=XXX — 拉取设备配置
 */
export async function handleConfig(request: Request, env: Env): Promise<Response> {
  const url = new URL(request.url);
  const deviceId = url.searchParams.get('deviceId') || '';

  if (request.method === 'GET') {
    if (!deviceId) return jsonResponse({ error: 'deviceId required' }, 400);
    const row = await env.DB.prepare(
      'SELECT config_json FROM device_config WHERE device_id = ?1'
    ).bind(deviceId).first<{ config_json: string }>();
    if (!row) return jsonResponse({ config: null });
    return jsonResponse({ config: JSON.parse(row.config_json) });
  }

  if (request.method === 'POST') {
    let body: { deviceId?: string; config?: any };
    try { body = await request.json(); } catch { return jsonResponse({ error: 'invalid json' }, 400); }
    if (!body.deviceId || !body.config) return jsonResponse({ error: 'deviceId and config required' }, 400);

    const json = JSON.stringify(body.config);
    const ts = Math.floor(Date.now() / 1000);
    await env.DB.prepare(
      'INSERT INTO device_config (device_id, config_json, updated_at) VALUES (?1, ?2, ?3) ON CONFLICT(device_id) DO UPDATE SET config_json = ?2, updated_at = ?3'
    ).bind(body.deviceId, json, ts).run();
    return jsonResponse({ ok: true });
  }

  return jsonResponse({ error: 'method not allowed' }, 405);
}

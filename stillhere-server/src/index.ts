import { handleHeartbeat } from './routes/heartbeat';
import { handleBindCode } from './routes/bind-code';
import { handleBindUser } from './routes/bind-user';
import { runWatchdog } from './cron/watchdog';
import { Env, jsonResponse } from './shared';

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);
    const path = url.pathname;

    // CORS 预检
    if (request.method === 'OPTIONS') {
      const resp = new Response(null, { status: 204 });
      resp.headers.set('Access-Control-Allow-Origin', '*');
      resp.headers.set('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
      resp.headers.set('Access-Control-Allow-Headers', 'Content-Type');
      return resp;
    }

    try {
      switch (path) {
        case '/heartbeat':
          return await handleHeartbeat(request, env);
        case '/generate-bind-code':
          return await handleBindCode(request, env);
        case '/bind-user':
          return await handleBindUser(request, env);
        default:
          return jsonResponse({ error: 'not found' }, 404);
      }
    } catch (e: any) {
      return jsonResponse({ error: e.message || 'internal error' }, 500);
    }
  },

  // Cron 触发器：每 5 分钟 watchdog 巡检
  async scheduled(_event: ScheduledEvent, env: Env): Promise<void> {
    await runWatchdog(env);
  },
};

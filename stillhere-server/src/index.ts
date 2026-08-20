import { handleHeartbeat } from './routes/heartbeat';
import { handleCare } from './routes/care';
import { handleCaredStatus } from './routes/cared-status';
import { handleCaredByMe } from './routes/cared-by-me';
import { handleCaring } from './routes/caring';
import { handleConfig } from './routes/config';
import { handleGreeting } from './routes/greeting';
import { handleDashboard } from './routes/dashboard';
import { handleAlert } from './routes/alert';
import { handlePendingAlerts } from './routes/pending-alerts';
import { handleCallback, redirectToGoogle, logout } from './lib/auth';
import { runWatchdog } from './cron/watchdog';
import { Env, jsonResponse } from './shared';

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);
    const path = url.pathname;

    // CORS 预检（仅 API 路由需要）
    if (request.method === 'OPTIONS') {
      const resp = new Response(null, { status: 204 });
      resp.headers.set('Access-Control-Allow-Origin', '*');
      resp.headers.set('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
      resp.headers.set('Access-Control-Allow-Headers', 'Content-Type');
      return resp;
    }

    try {
      switch (path) {
        case '/':
          return jsonResponse({ name: '晴好 API', version: '0.1.0' });
        case '/heartbeat':
          return await handleHeartbeat(request, env);
        case '/care':
          return await handleCare(request, env);
        case '/cared-status':
          return await handleCaredStatus(request, env);
        case '/dashboard':
          return await handleDashboard(request, env);
        case '/alert':
        case '/alert/cancel':
          return await handleAlert(request, env);
        case '/pending-alerts':
          return await handlePendingAlerts(request, env);
        case '/cared-by-me':
          return await handleCaredByMe(request, env);
        case '/caring':
          return await handleCaring(request, env);
        case '/config':
          return await handleConfig(request, env);
        case '/greeting':
        case '/greeting/reply':
        case '/greeting-history':
        case '/pending-greetings':
          return await handleGreeting(request, env);
        case '/auth/login':
          return redirectToGoogle(request, env);
        case '/auth/callback':
          return await handleCallback(request, env);
        case '/auth/logout':
          return logout(request);
        default:
          return jsonResponse({ error: 'not found' }, 404);
      }
    } catch (e: any) {
      return jsonResponse({ error: e.message || 'internal error' }, 500);
    }
  },

  async scheduled(controller: ScheduledController, env: Env): Promise<void> {
    console.log(`[cron] watchdog triggered at ${controller.cron}`);
    await runWatchdog(env);
  },
};

-- StillHere D1 数据库 Schema（精简版：绑定关系由客户端本地管理）

-- 用户表：记录心跳上报
CREATE TABLE IF NOT EXISTS users (
    id              TEXT PRIMARY KEY,          -- 客户端生成的 UUID（deviceId）
    device_token    TEXT DEFAULT NULL,         -- APNs device token（可选，push 用）
    last_active_time INTEGER NOT NULL DEFAULT 0, -- 最后活动时间戳（秒）
    threshold_minutes INTEGER NOT NULL DEFAULT 120, -- 超时阈值（分），可动态调整
    is_alerted      INTEGER NOT NULL DEFAULT 0, -- 告警状态
    is_charging     INTEGER NOT NULL DEFAULT 0, -- 是否充电
    last_city       TEXT DEFAULT NULL,         -- 最后一次上报时的 IP 归属城市
    online_status   TEXT DEFAULT 'online',     -- online / offline
    api_token       TEXT DEFAULT NULL,         -- 预留 API 鉴权 token
    created_at      INTEGER NOT NULL DEFAULT (unixepoch()) -- 用户创建时间
);

-- 关心关系表：记录谁在关心谁的关心码
CREATE TABLE IF NOT EXISTS care_relations (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    from_device_id  TEXT NOT NULL,              -- 发起关心的设备 ID
    to_code         TEXT NOT NULL,              -- 被关心方的关心码（6 位）
    name            TEXT NOT NULL,              -- 昵称
    created_at      INTEGER NOT NULL DEFAULT (unixepoch())
);

CREATE INDEX IF NOT EXISTS idx_care_to_code ON care_relations(to_code);

-- 告警事件表：记录被关心者的异常/离线/恢复事件，供关心人拉取或推送
CREATE TABLE IF NOT EXISTS alerts (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id         TEXT NOT NULL,              -- 被关心者的 deviceId
    care_code       TEXT NOT NULL,              -- 被关心者的关心码（6 位）
    alert_type      TEXT NOT NULL DEFAULT 'idle', -- idle / offline / online / recovery
    idle_minutes    INTEGER DEFAULT 0,          -- 空闲分钟数（idle 类型时有效）
    is_charging     INTEGER DEFAULT 0,          -- 是否充电
    is_resolved     INTEGER NOT NULL DEFAULT 0, -- 0=进行中, 1=已恢复
    created_at      INTEGER NOT NULL DEFAULT (unixepoch()),
    resolved_at     INTEGER
);

CREATE INDEX IF NOT EXISTS idx_alerts_care_code ON alerts(care_code);
CREATE INDEX IF NOT EXISTS idx_alerts_unresolved ON alerts(is_resolved, created_at);
CREATE INDEX IF NOT EXISTS idx_alerts_user ON alerts(user_id, created_at);

-- 问安消息表：记录关心人对被关心者的问安及回复
CREATE TABLE IF NOT EXISTS greetings (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    from_device_id  TEXT NOT NULL,              -- 发起问安的人（deviceId）
    to_code         TEXT NOT NULL,              -- 接收方的关心码（6 位）
    message         TEXT DEFAULT '问安',        -- 问安文本
    reply           TEXT,                       -- 回复内容
    replied_at      INTEGER,                    -- 回复时间戳
    notified        INTEGER NOT NULL DEFAULT 0, -- 0=未通知, 1=已通知客户端
    reply_to_id     INTEGER,                    -- 若为回复产生的反向记录，指向原问候 id
    created_at      INTEGER NOT NULL DEFAULT (unixepoch())
);

CREATE INDEX IF NOT EXISTS idx_greetings_to_code ON greetings(to_code, replied_at);
CREATE INDEX IF NOT EXISTS idx_greetings_from ON greetings(from_device_id);

-- Habitable backend schema (PostgreSQL).
-- Server-side account information and synchronized records only.
-- Photos never reach the server; evidence_anchors holds hashes and timestamps.

CREATE TABLE IF NOT EXISTS users (
  id            BIGSERIAL PRIMARY KEY,
  email         TEXT NOT NULL UNIQUE,
  password_hash TEXT NOT NULL,
  name          TEXT NOT NULL DEFAULT '',
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS sensors (
  id          BIGSERIAL PRIMARY KEY,
  user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  address     TEXT NOT NULL,
  model       TEXT NOT NULL DEFAULT '',
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (user_id, address)
);

-- Insert-only. (user_id, sensor_id, recorded_at) is unique so re-uploads are idempotent.
CREATE TABLE IF NOT EXISTS temperature_readings (
  id            BIGSERIAL PRIMARY KEY,
  user_id       BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  sensor_id     BIGINT NOT NULL REFERENCES sensors(id) ON DELETE CASCADE,
  client_id     BIGINT NOT NULL,
  temp_f        DOUBLE PRECISION NOT NULL,
  humidity_pct  DOUBLE PRECISION,
  recorded_at   BIGINT NOT NULL,
  flags         TEXT[] NOT NULL DEFAULT '{}',
  received_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (user_id, sensor_id, recorded_at)
);
CREATE INDEX IF NOT EXISTS idx_readings_user_time ON temperature_readings (user_id, recorded_at);

CREATE TABLE IF NOT EXISTS evidence_anchors (
  id               BIGSERIAL PRIMARY KEY,
  user_id          BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  sha256           TEXT NOT NULL,
  kind             TEXT NOT NULL,
  captured_at      BIGINT NOT NULL,
  server_timestamp BIGINT NOT NULL,
  UNIQUE (user_id, sha256)
);

CREATE TABLE IF NOT EXISTS issues (
  id          BIGSERIAL PRIMARY KEY,
  user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  client_id   BIGINT NOT NULL,
  category    TEXT NOT NULL,
  title       TEXT NOT NULL,
  status      TEXT NOT NULL,
  opened_at   BIGINT NOT NULL,
  closed_at   BIGINT,
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (user_id, client_id)
);

CREATE TABLE IF NOT EXISTS notices (
  id          BIGSERIAL PRIMARY KEY,
  user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  issue_id    BIGINT REFERENCES issues(id) ON DELETE SET NULL,
  sent_at     BIGINT NOT NULL,
  method      TEXT NOT NULL,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

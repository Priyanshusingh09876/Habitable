// Storage layer. Two implementations behind one small interface:
//   - PgStore:     PostgreSQL via `pg` when DATABASE_URL is set.
//   - MemoryStore: plain JS maps, used by the tests and for laptop demos.
// Routes only ever call the methods below, so swapping is a one-line decision.

const { Pool } = require('pg');

class MemoryStore {
  constructor() {
    this.users = [];
    this.sensors = [];
    this.readings = [];
    this.anchors = [];
    this.issues = [];
    this.notices = [];
    this._id = 1;
  }
  nextId() { return this._id++; }

  async findUserByEmail(email) { return this.users.find((u) => u.email === email) || null; }
  async findUserById(id) { return this.users.find((u) => u.id === id) || null; }
  async createUser({ email, passwordHash, name }) {
    const u = { id: this.nextId(), email, password_hash: passwordHash, name, created_at: new Date() };
    this.users.push(u);
    return u;
  }

  async ensureSensor(userId, address, model) {
    let s = this.sensors.find((x) => x.user_id === userId && x.address === address);
    if (!s) { s = { id: this.nextId(), user_id: userId, address, model: model || '' }; this.sensors.push(s); }
    return s;
  }

  async insertReadings(userId, rows) {
    const accepted = [];
    for (const r of rows) {
      const dup = this.readings.find((x) => x.user_id === userId && x.sensor_id === r.sensorId && x.recorded_at === r.recordedAt);
      if (!dup) {
        this.readings.push({ id: this.nextId(), user_id: userId, sensor_id: r.sensorId, client_id: r.clientId, temp_f: r.tempF, humidity_pct: r.humidityPct, recorded_at: r.recordedAt, flags: r.flags, received_at: new Date() });
      }
      accepted.push(r.clientId); // idempotent: a duplicate still counts as synced
    }
    return accepted;
  }
  async listReadings(userId, from, to, limit) {
    return this.readings.filter((x) => x.user_id === userId && x.recorded_at >= from && x.recorded_at <= to)
      .sort((a, b) => a.recorded_at - b.recorded_at).slice(0, limit);
  }
  async countReadings(userId) { return this.readings.filter((x) => x.user_id === userId).length; }

  async upsertAnchor(userId, { sha256, kind, capturedAt }, serverTimestamp) {
    let a = this.anchors.find((x) => x.user_id === userId && x.sha256 === sha256);
    if (!a) { a = { id: this.nextId(), user_id: userId, sha256, kind, captured_at: capturedAt, server_timestamp: serverTimestamp }; this.anchors.push(a); }
    return a;
  }
  async findAnchor(userId, sha256) { return this.anchors.find((x) => x.user_id === userId && x.sha256 === sha256) || null; }

  async upsertIssue(userId, i) {
    let row = this.issues.find((x) => x.user_id === userId && x.client_id === i.clientId);
    if (row) Object.assign(row, { category: i.category, title: i.title, status: i.status, opened_at: i.openedAt, closed_at: i.closedAt ?? null, updated_at: new Date() });
    else { row = { id: this.nextId(), user_id: userId, client_id: i.clientId, category: i.category, title: i.title, status: i.status, opened_at: i.openedAt, closed_at: i.closedAt ?? null, updated_at: new Date() }; this.issues.push(row); }
    return row;
  }
  async listIssues(userId) { return this.issues.filter((x) => x.user_id === userId).sort((a, b) => b.opened_at - a.opened_at); }

  async insertNotice(userId, n) {
    const row = { id: this.nextId(), user_id: userId, issue_id: n.issueId ?? null, sent_at: n.sentAt, method: n.method, created_at: new Date() };
    this.notices.push(row);
    return row;
  }
  async listNotices(userId) { return this.notices.filter((x) => x.user_id === userId).sort((a, b) => b.sent_at - a.sent_at); }

  async health() { return { store: 'memory', users: this.users.length, readings: this.readings.length }; }
  async close() {}
}

class PgStore {
  constructor(url) {
    this.pool = new Pool({ connectionString: url, ssl: url.includes('sslmode=require') ? { rejectUnauthorized: false } : undefined });
  }
  async q(text, params) { return (await this.pool.query(text, params)).rows; }

  async findUserByEmail(email) { return (await this.q('SELECT * FROM users WHERE email = $1', [email]))[0] || null; }
  async findUserById(id) { return (await this.q('SELECT * FROM users WHERE id = $1', [id]))[0] || null; }
  async createUser({ email, passwordHash, name }) {
    return (await this.q('INSERT INTO users (email, password_hash, name) VALUES ($1,$2,$3) RETURNING *', [email, passwordHash, name]))[0];
  }

  async ensureSensor(userId, address, model) {
    return (await this.q(
      `INSERT INTO sensors (user_id, address, model) VALUES ($1,$2,$3)
       ON CONFLICT (user_id, address) DO UPDATE SET model = EXCLUDED.model RETURNING *`,
      [userId, address, model || ''],
    ))[0];
  }

  async insertReadings(userId, rows) {
    const accepted = [];
    for (const r of rows) {
      await this.q(
        `INSERT INTO temperature_readings (user_id, sensor_id, client_id, temp_f, humidity_pct, recorded_at, flags)
         VALUES ($1,$2,$3,$4,$5,$6,$7) ON CONFLICT (user_id, sensor_id, recorded_at) DO NOTHING`,
        [userId, r.sensorId, r.clientId, r.tempF, r.humidityPct, r.recordedAt, r.flags],
      );
      accepted.push(r.clientId);
    }
    return accepted;
  }
  async listReadings(userId, from, to, limit) {
    return this.q('SELECT * FROM temperature_readings WHERE user_id=$1 AND recorded_at BETWEEN $2 AND $3 ORDER BY recorded_at LIMIT $4', [userId, from, to, limit]);
  }
  async countReadings(userId) { return Number((await this.q('SELECT COUNT(*)::int AS n FROM temperature_readings WHERE user_id=$1', [userId]))[0].n); }

  async upsertAnchor(userId, { sha256, kind, capturedAt }, serverTimestamp) {
    return (await this.q(
      `INSERT INTO evidence_anchors (user_id, sha256, kind, captured_at, server_timestamp) VALUES ($1,$2,$3,$4,$5)
       ON CONFLICT (user_id, sha256) DO UPDATE SET sha256 = EXCLUDED.sha256 RETURNING *`,
      [userId, sha256, kind, capturedAt, serverTimestamp],
    ))[0];
  }
  async findAnchor(userId, sha256) { return (await this.q('SELECT * FROM evidence_anchors WHERE user_id=$1 AND sha256=$2', [userId, sha256]))[0] || null; }

  async upsertIssue(userId, i) {
    return (await this.q(
      `INSERT INTO issues (user_id, client_id, category, title, status, opened_at, closed_at) VALUES ($1,$2,$3,$4,$5,$6,$7)
       ON CONFLICT (user_id, client_id) DO UPDATE SET category=EXCLUDED.category, title=EXCLUDED.title, status=EXCLUDED.status,
       opened_at=EXCLUDED.opened_at, closed_at=EXCLUDED.closed_at, updated_at=now() RETURNING *`,
      [userId, i.clientId, i.category, i.title, i.status, i.openedAt, i.closedAt ?? null],
    ))[0];
  }
  async listIssues(userId) { return this.q('SELECT * FROM issues WHERE user_id=$1 ORDER BY opened_at DESC', [userId]); }

  async insertNotice(userId, n) {
    return (await this.q('INSERT INTO notices (user_id, issue_id, sent_at, method) VALUES ($1,$2,$3,$4) RETURNING *', [userId, n.issueId ?? null, n.sentAt, n.method]))[0];
  }
  async listNotices(userId) { return this.q('SELECT * FROM notices WHERE user_id=$1 ORDER BY sent_at DESC', [userId]); }

  async health() {
    const r = await this.q('SELECT 1 AS ok');
    return { store: 'postgres', ok: r[0].ok === 1 };
  }
  async close() { await this.pool.end(); }
}

function createStore(env = process.env) {
  if (env.DATABASE_URL) return new PgStore(env.DATABASE_URL);
  return new MemoryStore();
}

module.exports = { createStore, MemoryStore, PgStore };

// Runs with Node's built-in test runner: `npm test`. No database needed.
const { test, describe, before } = require('node:test');
const assert = require('node:assert/strict');
const request = require('supertest');
const { createApp } = require('../src/app');
const { MemoryStore } = require('../src/db');

const env = { JWT_SECRET: 'test-secret-not-for-production', JWT_EXPIRES_IN: '1h' };
let now = 1_794_322_800_000; // 2026-11-10 10:00 America/New_York

function build() {
  return createApp({ store: new MemoryStore(), env, clock: () => now });
}

async function registered(app, email = 'tenant@example.com') {
  const res = await request(app).post('/auth/register').send({ email, password: 'correct-horse-battery', name: 'Tenant' });
  assert.equal(res.status, 201);
  return res.body.token;
}

describe('health', () => {
  test('reports ok and the store type', async () => {
    const res = await request(build()).get('/health');
    assert.equal(res.status, 200);
    assert.equal(res.body.ok, true);
    assert.equal(res.body.store.store, 'memory');
  });
});

describe('auth', () => {
  test('register returns a token, login works, duplicates are rejected', async () => {
    const app = build();
    const token = await registered(app);
    assert.ok(token.split('.').length === 3);

    const login = await request(app).post('/auth/login').send({ email: 'Tenant@Example.com', password: 'correct-horse-battery' });
    assert.equal(login.status, 200);
    assert.ok(login.body.token);

    const dup = await request(app).post('/auth/register').send({ email: 'tenant@example.com', password: 'correct-horse-battery' });
    assert.equal(dup.status, 409);
  });

  test('wrong password and unknown email both give 401 with the same message', async () => {
    const app = build();
    await registered(app);
    const a = await request(app).post('/auth/login').send({ email: 'tenant@example.com', password: 'wrong-password-1' });
    const b = await request(app).post('/auth/login').send({ email: 'nobody@example.com', password: 'wrong-password-1' });
    assert.equal(a.status, 401);
    assert.equal(b.status, 401);
    assert.deepEqual(a.body, b.body);
  });

  test('validation: short password and bad email are 400', async () => {
    const res = await request(build()).post('/auth/register').send({ email: 'nope', password: 'short' });
    assert.equal(res.status, 400);
    assert.equal(res.body.details.length, 2);
  });

  test('protected routes need a bearer token', async () => {
    const app = build();
    assert.equal((await request(app).get('/readings')).status, 401);
    assert.equal((await request(app).get('/readings').set('Authorization', 'Bearer not.a.jwt')).status, 401);
  });
});

describe('readings', () => {
  let app; let token;
  before(async () => { app = build(); token = await registered(app); });

  const batch = (n, start = now - 16 * 3_600_000) => ({
    readings: Array.from({ length: n }, (_, i) => ({
      clientId: i + 1, sensorAddress: 'SIM', tempF: 70 - i * 0.5, humidityPct: 40, recordedAt: start + i * 600_000, flags: ['SIMULATED'],
    })),
  });

  test('batch upload accepts readings and is idempotent', async () => {
    const first = await request(app).post('/readings').set('Authorization', `Bearer ${token}`).send(batch(5));
    assert.equal(first.status, 200);
    assert.equal(first.body.accepted, 5);
    assert.deepEqual(first.body.clientIds, [1, 2, 3, 4, 5]);

    const again = await request(app).post('/readings').set('Authorization', `Bearer ${token}`).send(batch(5));
    assert.equal(again.body.accepted, 5); // still acknowledged so the phone marks them synced

    const count = await request(app).get('/readings/count').set('Authorization', `Bearer ${token}`);
    assert.equal(count.body.count, 5); // but not stored twice
  });

  test('list by time window', async () => {
    const res = await request(app).get('/readings').set('Authorization', `Bearer ${token}`)
      .query({ from: now - 16 * 3_600_000, to: now - 16 * 3_600_000 + 1_200_000 });
    assert.equal(res.status, 200);
    assert.equal(res.body.count, 3);
    assert.equal(res.body.readings[0].temp_f, 70);
  });

  test('rejects an empty batch and out-of-range values', async () => {
    const empty = await request(app).post('/readings').set('Authorization', `Bearer ${token}`).send({ readings: [] });
    assert.equal(empty.status, 400);
    const bad = await request(app).post('/readings').set('Authorization', `Bearer ${token}`)
      .send({ readings: [{ clientId: 1, sensorAddress: 'SIM', tempF: 999, recordedAt: now }] });
    assert.equal(bad.status, 400);
    assert.match(bad.body.details[0], /tempF/);
  });

  test('users cannot see each other\'s readings', async () => {
    const other = await registered(app, 'other@example.com');
    const res = await request(app).get('/readings/count').set('Authorization', `Bearer ${other}`);
    assert.equal(res.body.count, 0);
  });
});

describe('evidence anchors', () => {
  test('first anchor gets the server time; re-anchoring returns the original', async () => {
    const app = build();
    const token = await registered(app);
    const sha = 'a'.repeat(64);
    const first = await request(app).post('/evidence/anchor').set('Authorization', `Bearer ${token}`).send({ sha256: sha, kind: 'photo', capturedAt: now - 60_000 });
    assert.equal(first.status, 201);
    assert.equal(first.body.serverTimestamp, now);

    now += 3_600_000; // an hour later the same hash is sent again
    const second = await request(app).post('/evidence/anchor').set('Authorization', `Bearer ${token}`).send({ sha256: sha.toUpperCase(), kind: 'photo', capturedAt: now - 60_000 });
    assert.equal(second.status, 200);
    assert.equal(second.body.serverTimestamp, first.body.serverTimestamp);

    const get = await request(app).get(`/evidence/anchor/${sha}`).set('Authorization', `Bearer ${token}`);
    assert.equal(get.status, 200);
    const missing = await request(app).get(`/evidence/anchor/${'b'.repeat(64)}`).set('Authorization', `Bearer ${token}`);
    assert.equal(missing.status, 404);
  });

  test('rejects a malformed hash', async () => {
    const app = build();
    const token = await registered(app);
    const res = await request(app).post('/evidence/anchor').set('Authorization', `Bearer ${token}`).send({ sha256: 'nothex', kind: 'photo', capturedAt: now });
    assert.equal(res.status, 400);
  });
});

describe('issues and notices', () => {
  test('upsert by clientId and list', async () => {
    const app = build();
    const token = await registered(app);
    const body = { clientId: 1, category: 'HEAT', title: 'No heat', status: 'OPEN', openedAt: now };
    const a = await request(app).put('/issues').set('Authorization', `Bearer ${token}`).send(body);
    assert.equal(a.status, 200);
    const b = await request(app).put('/issues').set('Authorization', `Bearer ${token}`).send({ ...body, status: 'NOTICE_SENT' });
    assert.equal(b.body.id, a.body.id);
    assert.equal(b.body.status, 'NOTICE_SENT');

    const n = await request(app).post('/issues/notices').set('Authorization', `Bearer ${token}`).send({ issueId: a.body.id, sentAt: now, method: 'email' });
    assert.equal(n.status, 201);

    const list = await request(app).get('/issues').set('Authorization', `Bearer ${token}`);
    assert.equal(list.body.issues.length, 1);
    const notices = await request(app).get('/issues/notices').set('Authorization', `Bearer ${token}`);
    assert.equal(notices.body.notices.length, 1);
  });
});

describe('errors', () => {
  test('unknown route is 404 JSON and malformed JSON is 400', async () => {
    const app = build();
    assert.equal((await request(app).get('/nope')).status, 404);
    const res = await request(app).post('/auth/login').set('Content-Type', 'application/json').send('{"email":');
    assert.equal(res.status, 400);
    assert.equal(res.body.error, 'malformed JSON');
  });
});

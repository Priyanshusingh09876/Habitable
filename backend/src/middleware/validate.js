// Tiny hand-rolled validators. Every route validates its body before touching the store.

const EMAIL = /^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/;
const SHA256 = /^[a-f0-9]{64}$/i;

function isInt(v) { return Number.isInteger(v); }
function isNum(v) { return typeof v === 'number' && Number.isFinite(v); }
function isStr(v, max = 500) { return typeof v === 'string' && v.length <= max; }

function validateAuth(body) {
  const errors = [];
  if (!isStr(body.email, 200) || !EMAIL.test(body.email)) errors.push('email must be a valid address');
  if (!isStr(body.password, 200) || body.password.length < 8) errors.push('password must be at least 8 characters');
  if (body.name !== undefined && body.name !== null && !isStr(body.name, 100)) errors.push('name too long');
  return errors;
}

function validateReadingsBatch(body) {
  const errors = [];
  if (!Array.isArray(body.readings)) return ['readings must be an array'];
  if (body.readings.length === 0) errors.push('readings must not be empty');
  if (body.readings.length > 2000) errors.push('at most 2000 readings per batch');
  body.readings.forEach((r, i) => {
    if (!isInt(r.clientId)) errors.push(`readings[${i}].clientId must be an integer`);
    if (!isStr(r.sensorAddress, 64) || !r.sensorAddress) errors.push(`readings[${i}].sensorAddress required`);
    if (!isNum(r.tempF) || r.tempF < -60 || r.tempF > 160) errors.push(`readings[${i}].tempF out of range`);
    if (r.humidityPct !== null && r.humidityPct !== undefined && (!isNum(r.humidityPct) || r.humidityPct < 0 || r.humidityPct > 100)) errors.push(`readings[${i}].humidityPct out of range`);
    if (!isInt(r.recordedAt) || r.recordedAt <= 0) errors.push(`readings[${i}].recordedAt must be epoch millis`);
    if (r.flags !== undefined && (!Array.isArray(r.flags) || !r.flags.every((f) => isStr(f, 40)))) errors.push(`readings[${i}].flags must be strings`);
  });
  return errors;
}

function validateAnchor(body) {
  const errors = [];
  if (!isStr(body.sha256, 64) || !SHA256.test(body.sha256)) errors.push('sha256 must be 64 hex characters');
  if (!isStr(body.kind, 40) || !body.kind) errors.push('kind required');
  if (!isInt(body.capturedAt) || body.capturedAt <= 0) errors.push('capturedAt must be epoch millis');
  return errors;
}

function validateIssue(body) {
  const errors = [];
  if (!isInt(body.clientId)) errors.push('clientId must be an integer');
  if (!isStr(body.category, 40) || !body.category) errors.push('category required');
  if (!isStr(body.title, 120) || !body.title) errors.push('title required');
  if (!isStr(body.status, 40) || !body.status) errors.push('status required');
  if (!isInt(body.openedAt)) errors.push('openedAt must be epoch millis');
  if (body.closedAt !== undefined && body.closedAt !== null && !isInt(body.closedAt)) errors.push('closedAt must be epoch millis');
  return errors;
}

function validateNotice(body) {
  const errors = [];
  if (body.issueId !== undefined && body.issueId !== null && !isInt(body.issueId)) errors.push('issueId must be an integer');
  if (!isInt(body.sentAt)) errors.push('sentAt must be epoch millis');
  if (!isStr(body.method, 40) || !body.method) errors.push('method required');
  return errors;
}

module.exports = { validateAuth, validateReadingsBatch, validateAnchor, validateIssue, validateNotice };

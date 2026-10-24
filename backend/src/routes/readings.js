const express = require('express');
const { validateReadingsBatch } = require('../middleware/validate');

module.exports = function readingsRoutes(store) {
  const router = express.Router();

  // POST /readings  { readings: [{clientId, sensorAddress, tempF, humidityPct, recordedAt, flags}] }
  // Idempotent: re-sending the same (sensor, recordedAt) is accepted and deduplicated.
  router.post('/', async (req, res, next) => {
    try {
      const errors = validateReadingsBatch(req.body || {});
      if (errors.length) return res.status(400).json({ error: 'invalid request', details: errors.slice(0, 10) });

      const sensorIds = new Map();
      const rows = [];
      for (const r of req.body.readings) {
        if (!sensorIds.has(r.sensorAddress)) {
          const s = await store.ensureSensor(req.userId, r.sensorAddress, r.sensorAddress === 'SIM' ? 'Simulated thermometer' : 'BLE thermometer');
          sensorIds.set(r.sensorAddress, s.id);
        }
        rows.push({
          clientId: r.clientId,
          sensorId: sensorIds.get(r.sensorAddress),
          tempF: r.tempF,
          humidityPct: r.humidityPct ?? null,
          recordedAt: r.recordedAt,
          flags: r.flags || [],
        });
      }
      const clientIds = await store.insertReadings(req.userId, rows);
      return res.json({ accepted: clientIds.length, clientIds });
    } catch (e) { return next(e); }
  });

  // GET /readings?from=&to=&limit=   (epoch millis)
  router.get('/', async (req, res, next) => {
    try {
      const from = Number(req.query.from) || 0;
      const to = Number(req.query.to) || Date.now();
      const limit = Math.min(Number(req.query.limit) || 1000, 5000);
      const rows = await store.listReadings(req.userId, from, to, limit);
      return res.json({ count: rows.length, readings: rows });
    } catch (e) { return next(e); }
  });

  router.get('/count', async (req, res, next) => {
    try { return res.json({ count: await store.countReadings(req.userId) }); } catch (e) { return next(e); }
  });

  return router;
};

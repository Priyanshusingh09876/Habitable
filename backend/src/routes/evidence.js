const express = require('express');
const { validateAnchor } = require('../middleware/validate');

module.exports = function evidenceRoutes(store, clock = Date.now) {
  const router = express.Router();

  // POST /evidence/anchor { sha256, kind, capturedAt }
  // Records when the server first saw this hash. The photo itself never arrives.
  // Re-anchoring the same hash returns the ORIGINAL timestamp, which is the point.
  router.post('/anchor', async (req, res, next) => {
    try {
      const errors = validateAnchor(req.body || {});
      if (errors.length) return res.status(400).json({ error: 'invalid request', details: errors });
      const sha256 = req.body.sha256.toLowerCase();
      const existing = await store.findAnchor(req.userId, sha256);
      const row = existing || (await store.upsertAnchor(req.userId, { sha256, kind: req.body.kind, capturedAt: req.body.capturedAt }, clock()));
      return res.status(existing ? 200 : 201).json({ sha256: row.sha256, serverTimestamp: Number(row.server_timestamp), kind: row.kind, capturedAt: Number(row.captured_at) });
    } catch (e) { return next(e); }
  });

  // GET /evidence/anchor/:sha256  — verify a hash was seen and when
  router.get('/anchor/:sha256', async (req, res, next) => {
    try {
      const row = await store.findAnchor(req.userId, String(req.params.sha256).toLowerCase());
      if (!row) return res.status(404).json({ error: 'not anchored' });
      return res.json({ sha256: row.sha256, serverTimestamp: Number(row.server_timestamp), kind: row.kind, capturedAt: Number(row.captured_at) });
    } catch (e) { return next(e); }
  });

  return router;
};

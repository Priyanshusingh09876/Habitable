const express = require('express');
const { validateIssue, validateNotice } = require('../middleware/validate');

module.exports = function issuesRoutes(store) {
  const router = express.Router();

  // PUT /issues  { clientId, category, title, status, openedAt, closedAt? }  (upsert by clientId)
  router.put('/', async (req, res, next) => {
    try {
      const errors = validateIssue(req.body || {});
      if (errors.length) return res.status(400).json({ error: 'invalid request', details: errors });
      const row = await store.upsertIssue(req.userId, req.body);
      return res.json(row);
    } catch (e) { return next(e); }
  });

  router.get('/', async (req, res, next) => {
    try { return res.json({ issues: await store.listIssues(req.userId) }); } catch (e) { return next(e); }
  });

  // POST /notices { issueId?, sentAt, method }
  router.post('/notices', async (req, res, next) => {
    try {
      const errors = validateNotice(req.body || {});
      if (errors.length) return res.status(400).json({ error: 'invalid request', details: errors });
      const row = await store.insertNotice(req.userId, req.body);
      return res.status(201).json(row);
    } catch (e) { return next(e); }
  });

  router.get('/notices', async (req, res, next) => {
    try { return res.json({ notices: await store.listNotices(req.userId) }); } catch (e) { return next(e); }
  });

  return router;
};

const express = require('express');
const bcrypt = require('bcryptjs');
const { signToken } = require('../middleware/auth');
const { validateAuth } = require('../middleware/validate');

module.exports = function authRoutes(store, env) {
  const router = express.Router();

  // POST /auth/register  { email, password, name? }
  router.post('/register', async (req, res, next) => {
    try {
      const errors = validateAuth(req.body || {});
      if (errors.length) return res.status(400).json({ error: 'invalid request', details: errors });
      const email = req.body.email.toLowerCase().trim();
      if (await store.findUserByEmail(email)) return res.status(409).json({ error: 'email already registered' });
      const passwordHash = await bcrypt.hash(req.body.password, 10);
      const user = await store.createUser({ email, passwordHash, name: (req.body.name || '').trim() });
      return res.status(201).json({ token: signToken(user, env), userId: user.id });
    } catch (e) { return next(e); }
  });

  // POST /auth/login  { email, password }
  router.post('/login', async (req, res, next) => {
    try {
      const errors = validateAuth(req.body || {});
      if (errors.length) return res.status(400).json({ error: 'invalid request', details: errors });
      const email = req.body.email.toLowerCase().trim();
      const user = await store.findUserByEmail(email);
      // Same response for unknown email and wrong password so the API does not leak which emails exist.
      if (!user || !(await bcrypt.compare(req.body.password, user.password_hash))) {
        return res.status(401).json({ error: 'invalid email or password' });
      }
      return res.json({ token: signToken(user, env), userId: user.id });
    } catch (e) { return next(e); }
  });

  return router;
};

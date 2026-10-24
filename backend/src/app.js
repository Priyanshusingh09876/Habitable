// Express app factory. server.js wires it to a port; tests build it with a MemoryStore.
const express = require('express');
const cors = require('cors');
const helmet = require('helmet');
const { requireAuth } = require('./middleware/auth');
const authRoutes = require('./routes/auth');
const readingsRoutes = require('./routes/readings');
const evidenceRoutes = require('./routes/evidence');
const issuesRoutes = require('./routes/issues');

function createApp({ store, env = process.env, clock = Date.now }) {
  if (!env.JWT_SECRET) throw new Error('JWT_SECRET must be set (see .env.example)');

  const app = express();
  app.disable('x-powered-by');
  app.use(helmet());
  app.use(cors());
  app.use(express.json({ limit: '1mb' }));

  app.get('/health', async (req, res) => {
    const store_ = await store.health().catch((e) => ({ error: e.message }));
    res.json({ ok: !store_.error, service: 'habitable-backend', time: new Date(clock()).toISOString(), store: store_ });
  });

  app.use('/auth', authRoutes(store, env));
  app.use('/readings', requireAuth(env), readingsRoutes(store));
  app.use('/evidence', requireAuth(env), evidenceRoutes(store, clock));
  app.use('/issues', requireAuth(env), issuesRoutes(store));

  app.use((req, res) => res.status(404).json({ error: 'not found' }));

  // eslint-disable-next-line no-unused-vars
  app.use((err, req, res, next) => {
    if (err.type === 'entity.parse.failed') return res.status(400).json({ error: 'malformed JSON' });
    console.error(err);
    res.status(500).json({ error: 'internal error' });
  });

  return app;
}

module.exports = { createApp };

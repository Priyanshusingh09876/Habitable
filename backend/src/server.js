require('dotenv').config();
const { createApp } = require('./app');
const { createStore } = require('./db');

const port = Number(process.env.PORT) || 3000;
const store = createStore();
const app = createApp({ store });

const server = app.listen(port, () => {
  console.log(`Habitable backend listening on http://localhost:${port} (store: ${process.env.DATABASE_URL ? 'postgres' : 'memory'})`);
});

for (const sig of ['SIGINT', 'SIGTERM']) {
  process.on(sig, () => {
    server.close(() => store.close().then(() => process.exit(0)));
  });
}

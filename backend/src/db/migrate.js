// Applies schema.sql to DATABASE_URL. Idempotent (CREATE ... IF NOT EXISTS).
require('dotenv').config();
const fs = require('fs');
const path = require('path');
const { Pool } = require('pg');

async function main() {
  const url = process.env.DATABASE_URL;
  if (!url) {
    console.error('DATABASE_URL is not set. Nothing to migrate (the in-memory store needs no schema).');
    process.exit(1);
  }
  const pool = new Pool({ connectionString: url, ssl: url.includes('sslmode=require') ? { rejectUnauthorized: false } : undefined });
  const sql = fs.readFileSync(path.join(__dirname, 'schema.sql'), 'utf8');
  await pool.query(sql);
  await pool.end();
  console.log('Schema applied.');
}

main().catch((e) => { console.error(e); process.exit(1); });

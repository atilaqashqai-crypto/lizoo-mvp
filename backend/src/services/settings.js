const db = require('../config/db');

async function getSettings(client = db) {
  const { rows } = await client.query('SELECT key, value FROM settings');
  const m = Object.fromEntries(rows.map((r) => [r.key, r.value]));
  return {
    baseRate: Number(m.base_rate_toman || 40000),
    idleMinutes: Number(m.idle_minutes || 30),
    idleRadiusM: Number(m.idle_radius_m || 50),
    minSettlement: Number(m.min_settlement_toman || 100000),
  };
}

module.exports = { getSettings };

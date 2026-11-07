const db = require('../config/db');
const { normalizePhone } = require('../utils/phone');

(async () => {
  const phone = normalizePhone(process.argv[2]);
  if (!phone) {
    console.error('Usage: npm run make-admin -- 09123456789');
    process.exit(1);
  }
  try {
    await db.query(
      `INSERT INTO users (phone, role, status, full_name)
       VALUES ($1, 'admin', 'approved', 'Admin')
       ON CONFLICT (phone) DO UPDATE SET role = 'admin', status = 'approved'`,
      [phone]
    );
    console.log('Admin ready:', phone);
  } catch (e) {
    console.error(e.message);
    process.exitCode = 1;
  } finally {
    await db.pool.end();
  }
})();

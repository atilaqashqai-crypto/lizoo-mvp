const jwt = require('jsonwebtoken');
const db = require('../config/db');

async function requireAuth(req, res, next) {
  try {
    const header = req.headers.authorization || '';
    const token = header.startsWith('Bearer ') ? header.slice(7) : null;
    if (!token) return res.status(401).json({ error: 'unauthorized' });

    const payload = jwt.verify(token, process.env.JWT_SECRET);
    const { rows } = await db.query('SELECT * FROM users WHERE id = $1', [payload.sub]);
    if (!rows[0]) return res.status(401).json({ error: 'unauthorized' });

    req.user = rows[0];
    next();
  } catch (e) {
    res.status(401).json({ error: 'unauthorized' });
  }
}

function requireAdmin(req, res, next) {
  if (req.user.role !== 'admin') return res.status(403).json({ error: 'forbidden' });
  next();
}

function requireApproved(req, res, next) {
  if (req.user.status !== 'approved') {
    return res.status(403).json({ error: 'not_approved', status: req.user.status });
  }
  next();
}

module.exports = { requireAuth, requireAdmin, requireApproved };

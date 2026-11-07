const path = require('path');
const express = require('express');
const cors = require('cors');
const helmet = require('helmet');
const rateLimit = require('express-rate-limit');
const db = require('./config/db');

const app = express();
app.set('trust proxy', 1);
app.use(
  helmet({
    contentSecurityPolicy: {
      useDefaults: true,
      directives: { 'upgrade-insecure-requests': null },
    },
  })
);
app.use(cors());
app.use(express.json({ limit: '1mb' }));

app.use('/panel', express.static(path.join(__dirname, '..', 'public')));

app.get('/health', async (req, res) => {
  try {
    await db.query('SELECT 1');
    res.json({ ok: true });
  } catch (e) {
    res.status(500).json({ ok: false });
  }
});

const authLimiter = rateLimit({
  windowMs: 15 * 60 * 1000,
  limit: 30,
  standardHeaders: true,
  legacyHeaders: false,
});

app.use('/auth', authLimiter, require('./routes/auth'));
app.use('/users', require('./routes/users'));
app.use('/activities', require('./routes/activities'));
app.use('/earnings', require('./routes/earnings'));
app.use('/settlements', require('./routes/settlements'));
app.use('/admin/settlements', require('./routes/adminSettlements'));
app.use('/admin', require('./routes/admin'));

app.use((req, res) => res.status(404).json({ error: 'not_found' }));

module.exports = app;

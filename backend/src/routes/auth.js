const express = require('express');
const bcrypt = require('bcryptjs');
const jwt = require('jsonwebtoken');
const crypto = require('crypto');
const db = require('../config/db');
const { normalizePhone } = require('../utils/phone');
const { sendOtp } = require('../services/sms');

const router = express.Router();
const MAX_ATTEMPTS = 5;
const RESEND_SECONDS = 60;

router.post('/request-otp', async (req, res) => {
  try {
    const phone = normalizePhone(req.body.phone);
    if (!phone) return res.status(400).json({ error: 'invalid_phone' });

    const last = await db.query(
      `SELECT created_at FROM otp_codes
       WHERE phone = $1 ORDER BY created_at DESC LIMIT 1`,
      [phone]
    );
    if (last.rows[0]) {
      const age = (Date.now() - new Date(last.rows[0].created_at).getTime()) / 1000;
      if (age < RESEND_SECONDS) {
        return res.status(429).json({
          error: 'too_soon',
          retry_after: Math.ceil(RESEND_SECONDS - age),
        });
      }
    }

    const demo = /^\d{5}$/.test(process.env.DEMO_OTP || '') ? process.env.DEMO_OTP : null;
    const code = demo || String(crypto.randomInt(10000, 100000));
    const hash = await bcrypt.hash(code, 8);
    const ttl = parseInt(process.env.OTP_EXPIRES_SECONDS || '120', 10);

    await db.query(
      `INSERT INTO otp_codes (phone, code_hash, expires_at)
       VALUES ($1, $2, now() + ($3 || ' seconds')::interval)`,
      [phone, hash, String(ttl)]
    );

    await sendOtp(phone, code);
    res.json({ ok: true, expires_in: ttl });
  } catch (e) {
    console.error('request-otp:', e.message);
    res.status(500).json({ error: 'server_error' });
  }
});

router.post('/verify-otp', async (req, res) => {
  try {
    const phone = normalizePhone(req.body.phone);
    const code = String(req.body.code || '').trim();
    if (!phone || !/^\d{5}$/.test(code)) {
      return res.status(400).json({ error: 'invalid_input' });
    }

    const { rows } = await db.query(
      `SELECT * FROM otp_codes
       WHERE phone = $1 AND used = false AND expires_at > now()
       ORDER BY created_at DESC LIMIT 1`,
      [phone]
    );
    const otp = rows[0];
    if (!otp) return res.status(400).json({ error: 'otp_expired' });
    if (otp.attempts >= MAX_ATTEMPTS) {
      return res.status(429).json({ error: 'too_many_attempts' });
    }

    const ok = await bcrypt.compare(code, otp.code_hash);
    if (!ok) {
      await db.query('UPDATE otp_codes SET attempts = attempts + 1 WHERE id = $1', [otp.id]);
      return res.status(400).json({ error: 'wrong_code' });
    }

    await db.query('UPDATE otp_codes SET used = true WHERE id = $1', [otp.id]);

    const up = await db.query(
      `INSERT INTO users (phone) VALUES ($1)
       ON CONFLICT (phone) DO UPDATE SET phone = EXCLUDED.phone
       RETURNING *`,
      [phone]
    );
    const user = up.rows[0];

    const token = jwt.sign({ sub: user.id, role: user.role }, process.env.JWT_SECRET, {
      expiresIn: process.env.JWT_EXPIRES_IN || '30d',
    });

    res.json({ token, user: publicUser(user) });
  } catch (e) {
    console.error('verify-otp:', e.message);
    res.status(500).json({ error: 'server_error' });
  }
});

function publicUser(u) {
  return {
    id: u.id,
    phone: u.phone,
    full_name: u.full_name,
    city: u.city,
    vehicle_plate: u.vehicle_plate,
    role: u.role,
    status: u.status,
    profile_complete: Boolean(u.full_name && u.city && u.vehicle_plate),
  };
}

module.exports = router;
module.exports.publicUser = publicUser;

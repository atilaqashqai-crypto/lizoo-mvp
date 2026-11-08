const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const express = require('express');
const db = require('../config/db');
const { requireAuth } = require('../middleware/auth');
const { publicUser } = require('./auth');
const {
  normalizeSheba, isValidSheba, normalizeCard, isValidCard, isValidUsername,
} = require('../utils/validators');

const router = express.Router();
router.use(requireAuth);

const UPLOAD_DIR =
  process.env.UPLOAD_DIR || path.join(__dirname, '..', '..', 'uploads');

router.get('/me', (req, res) => {
  res.json({ user: publicUser(req.user) });
});

router.put('/me/profile', async (req, res) => {
  try {
    const full_name = String(req.body.full_name || '').trim();
    const city = String(req.body.city || '').trim();
    const vehicle_plate = String(req.body.vehicle_plate || '').trim();
    const username = req.body.username ? String(req.body.username).trim() : null;

    if (full_name.length < 3 || full_name.length > 120 ||
        city.length < 2 || city.length > 80 ||
        vehicle_plate.length < 3 || vehicle_plate.length > 30 ||
        (username && !isValidUsername(username))) {
      return res.status(400).json({ error: 'invalid_input' });
    }

    // after rejection, editing resubmits the request for review
    const { rows } = await db.query(
      `UPDATE users
       SET full_name = $1, city = $2, vehicle_plate = $3,
           username = COALESCE($5, username),
           status = CASE WHEN status = 'rejected' THEN 'pending' ELSE status END
       WHERE id = $4
       RETURNING *`,
      [full_name, city, vehicle_plate, req.user.id, username]
    );
    res.json({ user: publicUser(rows[0]) });
  } catch (e) {
    if (e.code === '23505') return res.status(409).json({ error: 'username_taken' });
    console.error('profile:', e.message);
    res.status(500).json({ error: 'server_error' });
  }
});

router.put('/me/bank', async (req, res) => {
  try {
    const sheba = normalizeSheba(req.body.sheba);
    const card = normalizeCard(req.body.card_number);
    if (!isValidSheba(sheba)) return res.status(400).json({ error: 'invalid_sheba' });
    if (!isValidCard(card)) return res.status(400).json({ error: 'invalid_card' });

    const { rows } = await db.query(
      'UPDATE users SET sheba = $1, card_number = $2 WHERE id = $3 RETURNING *',
      [sheba, card, req.user.id]
    );
    res.json({ user: publicUser(rows[0]) });
  } catch (e) {
    console.error('bank:', e.message);
    res.status(500).json({ error: 'server_error' });
  }
});

function detectImage(buf) {
  if (buf.length > 3 && buf[0] === 0xff && buf[1] === 0xd8 && buf[2] === 0xff) return 'jpg';
  if (buf.length > 4 && buf[0] === 0x89 && buf[1] === 0x50 && buf[2] === 0x4e && buf[3] === 0x47) return 'png';
  return null;
}

// body: { image_base64: "..." } (the app compresses the photo first)
router.post('/me/id-document', async (req, res) => {
  try {
    const raw = String(req.body.image_base64 || '').replace(/^data:[^,]+,/, '');
    const buf = Buffer.from(raw, 'base64');
    if (buf.length < 1000 || buf.length > 6 * 1024 * 1024) {
      return res.status(400).json({ error: 'invalid_image' });
    }
    const ext = detectImage(buf);
    if (!ext) return res.status(400).json({ error: 'invalid_image' });

    fs.mkdirSync(UPLOAD_DIR, { recursive: true });
    const name = `${req.user.id}-${crypto.randomBytes(8).toString('hex')}.${ext}`;
    fs.writeFileSync(path.join(UPLOAD_DIR, name), buf, { mode: 0o600 });

    const old = req.user.id_doc_file;
    const { rows } = await db.query(
      'UPDATE users SET id_doc_file = $1 WHERE id = $2 RETURNING *',
      [name, req.user.id]
    );
    if (old && /^[\w.-]+$/.test(old)) fs.rm(path.join(UPLOAD_DIR, old), () => {});
    res.json({ user: publicUser(rows[0]) });
  } catch (e) {
    console.error('id-document:', e.message);
    res.status(500).json({ error: 'server_error' });
  }
});

module.exports = router;
module.exports.UPLOAD_DIR = UPLOAD_DIR;

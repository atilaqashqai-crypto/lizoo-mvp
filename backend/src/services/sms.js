require('dotenv').config();

async function sendOtp(phone, code) {
  // حالت دمو: اگر DEMO_OTP تنظیم شده باشد پیامکی فرستاده نمی‌شود
  if (process.env.DEMO_OTP) return;
  const key = process.env.SMS_API_KEY;
  if (!key) {
    if (process.env.NODE_ENV === 'production') {
      throw new Error('SMS_API_KEY is not set');
    }
    console.log(`[DEV OTP] ${phone} -> ${code}`);
    return;
  }
  const params = new URLSearchParams({
    receptor: phone,
    token: code,
    template: process.env.SMS_TEMPLATE || 'lizoo-otp',
  });
  const url = `https://api.kavenegar.com/v1/${key}/verify/lookup.json?${params}`;
  const res = await fetch(url);
  if (!res.ok) {
    throw new Error(`SMS provider error ${res.status}`);
  }
}

module.exports = { sendOtp };

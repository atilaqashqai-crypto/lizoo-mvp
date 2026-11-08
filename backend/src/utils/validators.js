const FA = '۰۱۲۳۴۵۶۷۸۹';
const AR = '٠١٢٣٤٥٦٧٨٩';

function toEnDigits(s) {
  return String(s ?? '')
    .replace(/[۰-۹]/g, (d) => FA.indexOf(d))
    .replace(/[٠-٩]/g, (d) => AR.indexOf(d));
}

function normalizeSheba(v) {
  let s = toEnDigits(v).replace(/[\s-]/g, '').toUpperCase();
  if (/^\d{24}$/.test(s)) s = 'IR' + s;
  return s;
}

function isValidSheba(s) {
  if (!/^IR\d{24}$/.test(s)) return false;
  const r = s.slice(4) + '1827' + s.slice(2, 4); // I=18, R=27
  let rem = 0;
  for (const ch of r) rem = (rem * 10 + Number(ch)) % 97;
  return rem === 1;
}

function normalizeCard(v) {
  return toEnDigits(v).replace(/[\s-]/g, '');
}

function isValidCard(s) {
  if (!/^\d{16}$/.test(s)) return false;
  let sum = 0;
  for (let i = 0; i < 16; i++) {
    let d = Number(s[i]);
    if (i % 2 === 0) {
      d *= 2;
      if (d > 9) d -= 9;
    }
    sum += d;
  }
  return sum % 10 === 0;
}

function isValidUsername(s) {
  return /^[A-Za-z0-9_.]{3,30}$/.test(s);
}

module.exports = {
  toEnDigits, normalizeSheba, isValidSheba, normalizeCard, isValidCard, isValidUsername,
};

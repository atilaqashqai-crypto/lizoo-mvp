const FA = '۰۱۲۳۴۵۶۷۸۹';
const AR = '٠١٢٣٤٥٦٧٨٩';

function normalizePhone(input) {
  let s = String(input || '')
    .replace(/[۰-۹]/g, (d) => FA.indexOf(d))
    .replace(/[٠-٩]/g, (d) => AR.indexOf(d))
    .replace(/\D/g, '');
  if (s.startsWith('0098')) s = '0' + s.slice(4);
  else if (s.startsWith('98')) s = '0' + s.slice(2);
  else if (s.length === 10 && s.startsWith('9')) s = '0' + s;
  return /^09\d{9}$/.test(s) ? s : null;
}

module.exports = { normalizePhone };

let token = localStorage.getItem('lizoo_token');
let courierFilter = 'pending';
let settleFilter = 'pending';

const $ = (s) => document.querySelector(s);
const fmt = (n) => Number(n || 0).toLocaleString('fa-IR');
const fmtDate = (d) => (d ? new Date(d).toLocaleString('fa-IR') : '-');

function el(tag, attrs = {}, ...kids) {
  const e = document.createElement(tag);
  for (const [k, v] of Object.entries(attrs)) {
    if (k === 'class') e.className = v;
    else if (k.startsWith('on')) e.addEventListener(k.slice(2), v);
    else e.setAttribute(k, v);
  }
  for (const kid of kids.flat()) {
    if (kid == null) continue;
    e.append(kid.nodeType ? kid : String(kid));
  }
  return e;
}

async function api(path, method = 'GET', body) {
  const res = await fetch(path, {
    method,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: 'Bearer ' + token } : {}),
    },
    body: body ? JSON.stringify(body) : undefined,
  });
  let data = {};
  try { data = await res.json(); } catch (e) {}
  if (res.status === 401 && token) { logout(); throw new Error('unauthorized'); }
  if (!res.ok) throw new Error(data.error || 'error');
  return data;
}

function table(heads, rows) {
  return el('div', { class: 'scroll' },
    el('table', {},
      el('thead', {}, el('tr', {}, heads.map((h) => el('th', {}, h)))),
      el('tbody', {}, rows.map((r) => el('tr', {}, r.map((c) => el('td', {}, c)))))));
}

function filterSelect(options, value, onChange) {
  const s = el('select', {}, options.map(([v, l]) => el('option', { value: v }, l)));
  s.value = value;
  s.addEventListener('change', () => onChange(s.value));
  return s;
}

async function act(path, body, tab) {
  try {
    await api(path, 'POST', body || {});
    show(tab);
  } catch (e) {
    alert('خطا: ' + e.message);
  }
}

const views = {
  async stats(root) {
    const s = await api('/admin/stats');
    const card = (t, v) => el('div', { class: 'card' }, t, el('b', {}, v));
    root.append(el('div', { class: 'cards' },
      card('پیک‌های در انتظار', fmt(s.pending_couriers)),
      card('تسویه‌های در انتظار', fmt(s.pending_settlements)),
      card('فعالیت‌های فعال', fmt(s.active_activities)),
      card('درآمد امروز پیک‌ها (تومان)', fmt(s.today_earnings_toman))));
  },

  async couriers(root) {
    const { users } = await api('/admin/users?status=' + courierFilter);
    root.append(filterSelect(
      [['pending', 'در انتظار'], ['approved', 'تأییدشده'], ['rejected', 'ردشده']],
      courierFilter, (v) => { courierFilter = v; show('couriers'); }));
    if (!users.length) return root.append(el('p', {}, 'موردی نیست'));
    root.append(table(
      ['نام', 'موبایل', 'شهر', 'پلاک', 'تاریخ', 'عملیات'],
      users.map((u) => {
        const ok = el('button', { class: 'ok', onclick: () => act(`/admin/users/${u.id}/approve`, {}, 'couriers') }, 'تأیید');
        const no = el('button', { class: 'bad', onclick: () => act(`/admin/users/${u.id}/reject`, {}, 'couriers') }, 'رد');
        const btns = u.status === 'pending' ? [ok, no] : u.status === 'approved' ? [no] : [ok];
        return [u.full_name || '-', u.phone, u.city || '-', u.vehicle_plate || '-', fmtDate(u.created_at), el('div', { class: 'row' }, btns)];
      })));
  },

  async settlements(root) {
    const { settlements } = await api('/admin/settlements?status=' + settleFilter);
    root.append(filterSelect(
      [['pending', 'در انتظار'], ['approved', 'تأییدشده (منتظر پرداخت)'], ['paid', 'پرداخت‌شده'], ['rejected', 'ردشده']],
      settleFilter, (v) => { settleFilter = v; show('settlements'); }));
    if (!settlements.length) return root.append(el('p', {}, 'موردی نیست'));
    root.append(table(
      ['پیک', 'موبایل', 'مبلغ (تومان)', 'تاریخ درخواست', 'عملیات'],
      settlements.map((s) => {
        const approve = el('button', { class: 'ok', onclick: () => act(`/admin/settlements/${s.id}/approve`, {}, 'settlements') }, 'تأیید');
        const pay = el('button', { class: 'pri', onclick: () => {
          if (confirm('پرداخت دستی انجام شد؟')) act(`/admin/settlements/${s.id}/pay`, {}, 'settlements');
        } }, 'پرداخت شد');
        const reject = el('button', { class: 'bad', onclick: () => {
          const note = prompt('دلیل رد (اختیاری)');
          if (note === null) return;
          act(`/admin/settlements/${s.id}/reject`, note ? { note } : {}, 'settlements');
        } }, 'رد');
        const btns = s.status === 'pending' ? [approve, reject] : s.status === 'approved' ? [pay, reject] : [];
        return [s.full_name || '-', s.phone, fmt(s.amount_toman), fmtDate(s.requested_at), el('div', { class: 'row' }, btns)];
      })));
  },

  async zones(root) {
    const { zones } = await api('/admin/zones');

    const f = {
      name: el('input', { type: 'text', placeholder: 'نام منطقه' }),
      lat: el('input', { type: 'text', placeholder: 'عرض (lat)', dir: 'ltr' }),
      lng: el('input', { type: 'text', placeholder: 'طول (lng)', dir: 'ltr' }),
      radius: el('input', { type: 'number', placeholder: 'شعاع متر' }),
      mult: el('input', { type: 'text', placeholder: 'ضریب مثلاً 1.5', dir: 'ltr' }),
    };
    const add = el('button', { class: 'pri', onclick: async () => {
      try {
        await api('/admin/zones', 'POST', {
          name: f.name.value, center_lat: f.lat.value, center_lng: f.lng.value,
          radius_m: f.radius.value, multiplier: f.mult.value,
        });
        show('zones');
      } catch (e) { alert('ورودی نامعتبر است'); }
    } }, 'افزودن منطقه');
    root.append(el('div', { class: 'row' }, Object.values(f), add));
    root.append(el('p', {}, 'مختصات را از نقشه (نگه‌داشتن روی نقطه) کپی کن. ضریب بین ۰.۱ تا ۱۰ است.'));

    if (!zones.length) return root.append(el('p', {}, 'هنوز منطقه‌ای ثبت نشده'));
    root.append(table(
      ['نام', 'lat', 'lng', 'شعاع', 'ضریب', 'فعال', ''],
      zones.map((z) => {
        const radius = el('input', { type: 'number', value: z.radius_m });
        const mult = el('input', { type: 'text', value: z.multiplier, dir: 'ltr' });
        const active = el('input', { type: 'checkbox' });
        active.checked = z.active;
        const save = el('button', { class: 'ok', onclick: async () => {
          try {
            await api('/admin/zones/' + z.id, 'PUT', {
              name: z.name, center_lat: z.center_lat, center_lng: z.center_lng,
              radius_m: radius.value, multiplier: mult.value, active: active.checked,
            });
            show('zones');
          } catch (e) { alert('ورودی نامعتبر است'); }
        } }, 'ذخیره');
        return [z.name, z.center_lat, z.center_lng, radius, mult, active, save];
      })));
  },

  async settings(root) {
    const { settings } = await api('/admin/settings');
    const fields = [
      ['base_rate_toman', 'نرخ پایه (تومان در ساعت)'],
      ['idle_minutes', 'قطع خودکار بعد از (دقیقه)'],
      ['idle_radius_m', 'شعاع ثابت‌ماندن (متر)'],
      ['min_settlement_toman', 'حداقل مبلغ تسویه (تومان)'],
    ];
    const inputs = {};
    for (const [k, label] of fields) {
      inputs[k] = el('input', { type: 'number', value: settings[k] });
      root.append(el('label', {}, label), inputs[k]);
    }
    root.append(el('div', { class: 'row' }, el('button', { class: 'pri', onclick: async () => {
      const body = {};
      for (const [k] of fields) body[k] = inputs[k].value;
      try { await api('/admin/settings', 'PUT', body); alert('ذخیره شد'); }
      catch (e) { alert('مقدار نامعتبر است'); }
    } }, 'ذخیره تنظیمات')));
  },
};

const tabs = [
  ['stats', 'خانه'], ['couriers', 'پیک‌ها'], ['settlements', 'تسویه‌ها'],
  ['zones', 'مناطق'], ['settings', 'تنظیمات'],
];

async function show(name) {
  for (const b of document.querySelectorAll('#nav button')) {
    b.classList.toggle('on', b.dataset.t === name);
  }
  const root = $('#view');
  root.replaceChildren(el('p', {}, 'در حال بارگذاری...'));
  try {
    const box = el('div');
    await views[name](box);
    root.replaceChildren(box);
  } catch (e) {
    if (e.message !== 'unauthorized') {
      root.replaceChildren(el('p', { class: 'err' }, 'خطا: ' + e.message));
    }
  }
}

function showApp() {
  $('#login').classList.add('hidden');
  $('#app').classList.remove('hidden');
  const nav = $('#nav');
  nav.replaceChildren(...tabs.map(([t, l]) =>
    el('button', { 'data-t': t, onclick: () => show(t) }, l)));
  show('stats');
}

function showLogin() {
  $('#app').classList.add('hidden');
  $('#login').classList.remove('hidden');
}

function logout() {
  token = null;
  localStorage.removeItem('lizoo_token');
  showLogin();
}

$('#logout').addEventListener('click', logout);

$('#sendOtp').addEventListener('click', async () => {
  $('#loginErr').textContent = '';
  try {
    await api('/auth/request-otp', 'POST', { phone: $('#phone').value });
    $('#code').classList.remove('hidden');
    $('#verify').classList.remove('hidden');
  } catch (e) {
    $('#loginErr').textContent = e.message === 'too_soon' ? 'کمی بعد دوباره تلاش کن' : 'شماره نامعتبر است';
  }
});

$('#verify').addEventListener('click', async () => {
  $('#loginErr').textContent = '';
  try {
    const d = await api('/auth/verify-otp', 'POST', { phone: $('#phone').value, code: $('#code').value });
    if (d.user.role !== 'admin') {
      $('#loginErr').textContent = 'این حساب ادمین نیست';
      return;
    }
    token = d.token;
    localStorage.setItem('lizoo_token', token);
    showApp();
  } catch (e) {
    $('#loginErr').textContent = 'کد اشتباه یا منقضی است';
  }
});

(async () => {
  if (!token) return showLogin();
  try {
    const d = await api('/users/me');
    if (d.user.role === 'admin') showApp(); else logout();
  } catch (e) {
    showLogin();
  }
})();

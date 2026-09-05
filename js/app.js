/* =====================================================================
  Sunrise - shared front-end helpers
   Exposes a single global: SC
   Everything that talks to the Java HttpServer goes through SC.api().
   ===================================================================== */
window.SC = (function () {
  'use strict';

  /* WAMP serves these pages on :80, the Java server listens on :8090
     (8080 is already taken by WAMP Apache on this machine). */
  var API_BASE = (location.port === '8090' || location.protocol === 'file:')
    ? '' : 'http://localhost:8090';

  var SESSION_KEY = 'sc_session';
  var DEMO_KEY = 'sc_demo_db';
  var offline = false;          // flipped the first time the server cannot be reached

  /* ------------------------------------------------------------------
     formatting
  ------------------------------------------------------------------ */
  function money(value) {
    var n = Number(value || 0);
    return 'Rs. ' + n.toLocaleString('en-LK', {
      minimumFractionDigits: 2, maximumFractionDigits: 2
    });
  }

  function fmtDate(value) {
    if (!value) return '-';
    var d = new Date(value);
    if (isNaN(d.getTime())) return value;
    return d.toLocaleDateString('en-GB', { weekday: 'short', day: 'numeric', month: 'short', year: 'numeric' });
  }

  function fmtTime(value) {
    if (!value) return '-';
    var bits = String(value).split(':');
    var h = parseInt(bits[0], 10);
    var m = bits[1] || '00';
    if (isNaN(h)) return value;
    var suffix = h < 12 ? 'a.m.' : 'p.m.';
    var h12 = h % 12 === 0 ? 12 : h % 12;
    return h12 + '.' + m + ' ' + suffix;
  }

  function today() {
    var d = new Date();
    return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate());
  }
  function pad(n) { return (n < 10 ? '0' : '') + n; }

  function initials(name) {
    return String(name || 'User').trim().split(/\s+/).slice(0, 2)
      .map(function (w) { return w.charAt(0).toUpperCase(); }).join('');
  }

  function param(key) {
    return new URLSearchParams(location.search).get(key);
  }

  function escapeHtml(str) {
    return String(str == null ? '' : str).replace(/[&<>"']/g, function (c) {
      return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c];
    });
  }

  /* ------------------------------------------------------------------
     session (kept in sessionStorage so closing the tab logs the user out)
  ------------------------------------------------------------------ */
  var session = {
    get: function () {
      try { return JSON.parse(sessionStorage.getItem(SESSION_KEY)); }
      catch (e) { return null; }
    },
    set: function (user) {
      try { sessionStorage.setItem(SESSION_KEY, JSON.stringify(user)); } catch (e) {}
    },
    clear: function () {
      try { sessionStorage.removeItem(SESSION_KEY); } catch (e) {}
    }
  };

  function requireAuth() {
    var user = session.get();
    if (!user) {
      location.replace('login.html?next=' + encodeURIComponent(location.pathname.split('/').pop()));
      return null;
    }
    return user;
  }

  function protectStaffPage() {
    return !document.body.classList.contains('app-body') || !!requireAuth();
  }

  function logout() {
    api('/api/auth/logout', { method: 'POST' }).catch(function () { /* nothing to do */ });
    session.clear();
    location.href = 'login.html?bye=1';
  }

  /* ------------------------------------------------------------------
     api(): fetch wrapper. If the Java server is not running the request
     is answered by the local demo store instead, so the interface can
     still be shown. Delete demo.js and this fallback for the live build.
  ------------------------------------------------------------------ */
  function api(path, options) {
    var opts = options || {};
    var init = {
      method: opts.method || 'GET',
      headers: { 'Accept': 'application/json' }
    };
    if (opts.body) {
      init.headers['Content-Type'] = 'application/json';
      init.body = JSON.stringify(opts.body);
    }
    var user = session.get();
    if (user && user.token) init.headers['Authorization'] = 'Bearer ' + user.token;

    return fetch(API_BASE + path, init).then(function (res) {
      return res.text().then(function (text) {
        var data = null;
        try { data = text ? JSON.parse(text) : null; } catch (e) { data = null; }
        if (!res.ok) {
          var err = new Error((data && data.message) || ('Request failed (' + res.status + ')'));
          err.status = res.status;
          err.data = data;
          throw err;
        }
        return data;
      });
    }).catch(function (err) {
      // a real HTTP error (404, 401...) must bubble up untouched
      if (err.status) throw err;
      // network error: the backend is not up
      offline = true;
      showDemoBanner();
      return Demo.handle(init.method, path, opts.body);
    });
  }

  function isOffline() { return offline; }

  function showDemoBanner() {
    if (document.getElementById('demoBanner')) return;
    var host = document.querySelector('[data-demo-slot]');
    if (!host) return;
    var el = document.createElement('div');
    el.id = 'demoBanner';
    el.className = 'notice notice--demo';
    el.innerHTML = '<span aria-hidden="true">&#9888;</span><span><strong>Demo mode</strong>' +
      'The Java server on port 8090 is not responding, so this page is using sample data ' +
      'stored in your browser. Start the backend to work with the real database.</span>';
    host.prepend(el);
  }

  /* ------------------------------------------------------------------
     toasts
  ------------------------------------------------------------------ */
  function toast(message, kind) {
    var stack = document.querySelector('.toast-stack');
    if (!stack) {
      stack = document.createElement('div');
      stack.className = 'toast-stack';
      document.body.appendChild(stack);
    }
    var el = document.createElement('div');
    el.className = 'toast' + (kind ? ' toast--' + kind : '');
    el.setAttribute('role', 'status');
    el.textContent = message;
    stack.appendChild(el);
    setTimeout(function () {
      el.classList.add('is-out');
      setTimeout(function () { el.remove(); }, 260);
    }, 4200);
  }

  /* ------------------------------------------------------------------
     application shell: sidebar, active link, user chip, logout
  ------------------------------------------------------------------ */
  function initShell() {
    if (!protectStaffPage()) return;

    var side = document.querySelector('.side');
    var toggle = document.querySelector('.side-toggle');
    var backdrop = document.querySelector('.side-backdrop');

    if (side && toggle) {
      var setSide = function (open) {
        side.classList.toggle('is-open', open);
        if (backdrop) backdrop.classList.toggle('is-open', open);
        toggle.setAttribute('aria-expanded', String(open));
      };
      toggle.addEventListener('click', function () {
        setSide(!side.classList.contains('is-open'));
      });
      if (backdrop) backdrop.addEventListener('click', function () { setSide(false); });
      side.addEventListener('click', function (e) { if (e.target.closest('a')) setSide(false); });
      document.addEventListener('keydown', function (e) { if (e.key === 'Escape') setSide(false); });
    }

    // mark the current page in the sidebar
    var here = location.pathname.split('/').pop() || 'index.html';
    document.querySelectorAll('.side__nav a').forEach(function (a) {
      if (a.getAttribute('href').split('?')[0] === here) a.classList.add('is-active');
    });

    // signed-in user chip
    var user = session.get();
    if (user) {
      var av = document.querySelector('.side__avatar');
      var nm = document.querySelector('[data-user-name]');
      var rl = document.querySelector('[data-user-role]');
      if (av) av.textContent = initials(user.fullName || user.username);
      if (nm) nm.textContent = user.fullName || user.username;
      if (rl) rl.textContent = user.role || 'staff';
    }

    // the Manage section (reports, admin) is for admins only
    var isAdmin = !!(user && String(user.role).toLowerCase() === 'admin');
    document.querySelectorAll('[data-admin-only]').forEach(function (el) {
      el.hidden = !isAdmin;
    });

    document.querySelectorAll('[data-logout]').forEach(function (btn) {
      btn.addEventListener('click', function () {
        if (confirm('Exit the system and sign out?')) logout();
      });
    });

    // simple accordion, used on the help page
    document.querySelectorAll('[data-accordion] > button').forEach(function (btn) {
      btn.addEventListener('click', function () {
        var open = btn.getAttribute('aria-expanded') === 'true';
        btn.setAttribute('aria-expanded', String(!open));
        var panel = document.getElementById(btn.getAttribute('aria-controls'));
        if (panel) panel.hidden = open;
      });
    });

    var yr = document.getElementById('year');
    if (yr) yr.textContent = new Date().getFullYear();
  }

  /* ------------------------------------------------------------------
     Demo store - only used when the backend cannot be reached, so the
     screens can still be demonstrated. Remove this block once the Java
     server is running against MySQL.
  ------------------------------------------------------------------ */
  var Demo = (function () {
    var seed = {
      users: [
        { username: 'admin', password: 'admin123', role: 'admin', fullName: 'Chathura Bandara' },
        { username: 'reception', password: 'clinic123', role: 'receptionist', fullName: 'Ishara Madushani' }
      ],
      dentists: [
        { id: 1, name: 'Dr. Anushka Fernando', consultationFee: 2000 },
        { id: 2, name: 'Dr. Ruwan Jayasuriya', consultationFee: 2500 },
        { id: 3, name: 'Dr. Sanduni Wickramasinghe', consultationFee: 2500 }
      ],
      treatments: [
        { id: 1, treatmentType: 'Routine check-up', baseCost: 1500 },
        { id: 2, treatmentType: 'Scaling and polishing', baseCost: 4500 },
        { id: 3, treatmentType: 'Tooth filling', baseCost: 3500 },
        { id: 4, treatmentType: 'Root canal treatment', baseCost: 18000 },
        { id: 5, treatmentType: 'Tooth extraction', baseCost: 5000 },
        { id: 6, treatmentType: 'Braces consultation', baseCost: 95000 }
      ],
      appointments: [
        { appointmentNo: 'APT-1042', patientName: 'Nimal Perera', address: '45/2, Temple Road, Nugegoda', contactNo: '0771234567', dentistId: 1, treatmentId: 2, date: today(), time: '10:30', status: 'Confirmed' },
        { appointmentNo: 'APT-1043', patientName: 'Kamani Silva', address: '12, Flower Road, Colombo 07', contactNo: '0712223344', dentistId: 1, treatmentId: 3, date: today(), time: '11:15', status: 'Pending' },
        { appointmentNo: 'APT-1044', patientName: 'Rashmi Alwis', address: '8, Lake Drive, Rajagiriya', contactNo: '0765556677', dentistId: 2, treatmentId: 4, date: today(), time: '14:00', status: 'Completed' },
        { appointmentNo: 'APT-1045', patientName: 'Sunil Wijesinghe', address: '221B, Galle Road, Dehiwala', contactNo: '0778889900', dentistId: 3, treatmentId: 1, date: today(), time: '15:30', status: 'Cancelled' }
      ],
      bills: [],
      nextNo: 1046
    };

    function db() {
      try {
        var raw = localStorage.getItem(DEMO_KEY);
        if (raw) return JSON.parse(raw);
      } catch (e) {}
      save(seed);
      return JSON.parse(JSON.stringify(seed));
    }

    function save(data) {
      try { localStorage.setItem(DEMO_KEY, JSON.stringify(data)); } catch (e) {}
    }

    // joins the dentist and treatment rows onto an appointment
    function expand(data, a) {
      var d = data.dentists.filter(function (x) { return x.id === Number(a.dentistId); })[0] || {};
      var t = data.treatments.filter(function (x) { return x.id === Number(a.treatmentId); })[0] || {};
      return Object.assign({}, a, {
        dentistName: d.name || '-',
        consultationFee: d.consultationFee || 0,
        treatmentType: t.treatmentType || '-',
        treatmentCost: t.baseCost || 0
      });
    }

    function fail(status, message) {
      var e = new Error(message);
      e.status = status;
      return Promise.reject(e);
    }

    function handle(method, path, body) {
      var data = db();
      var url = path.split('?')[0];
      var query = new URLSearchParams(path.split('?')[1] || '');
      var m;

      if (method === 'POST' && url === '/api/auth/login') {
        var u = data.users.filter(function (x) {
          return x.username === String(body.username || '').toLowerCase() && x.password === body.password;
        })[0];
        if (!u) return fail(401, 'Username or password is incorrect.');
        return Promise.resolve({ username: u.username, fullName: u.fullName, role: u.role, token: 'demo-token' });
      }

      if (method === 'POST' && url === '/api/auth/logout') return Promise.resolve({ ok: true });
      if (method === 'GET' && url === '/api/dentists') return Promise.resolve(data.dentists);
      if (method === 'GET' && url === '/api/treatments') return Promise.resolve(data.treatments);

      m = url.match(/^\/api\/appointments\/([A-Za-z0-9\-]+)$/);
      if (m) {
        var found = data.appointments.filter(function (a) {
          return a.appointmentNo.toUpperCase() === m[1].toUpperCase();
        })[0];
        if (!found) return fail(404, 'No appointment found for ' + m[1] + '.');

        if (method === 'GET') return Promise.resolve(expand(data, found));
        if (method === 'PUT' || method === 'PATCH') {
          Object.assign(found, body || {});
          save(data);
          return Promise.resolve(expand(data, found));
        }
      }

      if (url === '/api/appointments') {
        if (method === 'GET') {
          var list = data.appointments.map(function (a) { return expand(data, a); });
          var qDate = query.get('date');
          var qStatus = query.get('status');
          var qText = String(query.get('q') || '').toLowerCase();

          if (qDate) list = list.filter(function (a) { return a.date === qDate; });
          if (qStatus) list = list.filter(function (a) { return a.status === qStatus; });
          if (qText) {
            list = list.filter(function (a) {
              return (a.patientName + ' ' + a.appointmentNo + ' ' + a.contactNo).toLowerCase().indexOf(qText) > -1;
            });
          }
          list.sort(function (a, b) { return (a.date + a.time).localeCompare(b.date + b.time); });
          return Promise.resolve(list);
        }

        if (method === 'POST') {
          var clash = data.appointments.filter(function (a) {
            return a.date === body.date && a.time === body.time &&
              Number(a.dentistId) === Number(body.dentistId) && a.status !== 'Cancelled';
          })[0];
          if (clash) return fail(409, 'That dentist already has a patient booked at this time.');

          var created = Object.assign({}, body, {
            appointmentNo: 'APT-' + data.nextNo++,
            status: 'Pending'
          });
          data.appointments.push(created);
          save(data);
          return Promise.resolve(expand(data, created));
        }
      }

      m = url.match(/^\/api\/bills\/([A-Za-z0-9\-]+)$/);
      if (m && method === 'GET') {
        var bill = data.bills.filter(function (b) {
          return b.appointmentNo.toUpperCase() === m[1].toUpperCase();
        })[0];
        if (!bill) return fail(404, 'No bill has been issued for ' + m[1] + ' yet.');
        return Promise.resolve(bill);
      }

      if (method === 'POST' && url === '/api/bills') {
        var wanted = String((body && body.appointmentNo) || '').toUpperCase();
        var appt = data.appointments.filter(function (a) {
          return a.appointmentNo.toUpperCase() === wanted;
        })[0];
        if (!appt) return fail(404, 'No appointment found for ' + wanted + '.');
        if (appt.status === 'Cancelled') return fail(409, 'This appointment was cancelled, so it cannot be billed.');

        var full = expand(data, appt);
        var newBill = {
          billNo: 'BIL-' + (7100 + data.bills.length + 1),
          appointmentNo: full.appointmentNo,
          patientName: full.patientName,
          dentistName: full.dentistName,
          treatmentType: full.treatmentType,
          treatmentCost: full.treatmentCost,
          consultationFee: full.consultationFee,
          total: full.treatmentCost + full.consultationFee,
          issuedAt: new Date().toISOString()
        };
        data.bills = data.bills.filter(function (b) { return b.appointmentNo !== newBill.appointmentNo; });
        data.bills.push(newBill);
        appt.status = 'Completed';
        save(data);
        return Promise.resolve(newBill);
      }

      return fail(404, 'Endpoint not available in demo mode: ' + method + ' ' + url);
    }

    return { handle: handle };
  }());

  document.addEventListener('DOMContentLoaded', initShell);
  window.addEventListener('pageshow', function () {
    if (document.body.classList.contains('app-body') && !session.get()) {
      requireAuth();
    }
  });

  return {
    API_BASE: API_BASE,
    api: api,
    isOffline: isOffline,
    session: session,
    requireAuth: requireAuth,
    logout: logout,
    toast: toast,
    money: money,
    fmtDate: fmtDate,
    fmtTime: fmtTime,
    today: today,
    initials: initials,
    param: param,
    escapeHtml: escapeHtml
  };
}());

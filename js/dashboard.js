/* =====================================================================
   Dashboard
     GET /api/reports/summary
     GET /api/reports/revenue?days=7
     GET /api/reports/appointments?days=7
     GET /api/reports/treatments?limit=5
     GET /api/reports/status
     GET /api/reports/workload
   ===================================================================== */
(function () {
  'use strict';

  var user = SC.requireAuth();
  if (!user) return;

  var greeting = document.getElementById('greeting');
  if (greeting) {
    var hour = new Date().getHours();
    var part = hour < 12 ? 'Good morning' : (hour < 17 ? 'Good afternoon' : 'Good evening');
    greeting.textContent = part + (user.fullName ? ', ' + user.fullName.split(' ')[0] : '');
  }

  function set(id, text) {
    var el = document.getElementById(id);
    if (el) el.textContent = text;
  }

  /* ------------------------------------------------------------------
     the last n calendar days, oldest first, as yyyy-mm-dd strings
  ------------------------------------------------------------------ */
  function lastDays(n) {
    var out = [];
    var d = new Date();
    for (var i = n - 1; i >= 0; i--) {
      var x = new Date(d);
      x.setDate(d.getDate() - i);
      out.push(x.getFullYear() + '-' + String(x.getMonth() + 1).padStart(2, '0') + '-' + String(x.getDate()).padStart(2, '0'));
    }
    return out;
  }

  function shortDay(iso) {
    var d = new Date(iso + 'T00:00:00');
    return isNaN(d.getTime()) ? iso : d.toLocaleDateString('en-GB', { weekday: 'short' });
  }

  /* ------------------------------------------------------------------
     vertical bar chart: rows keyed by day, filling any gap with zero
  ------------------------------------------------------------------ */
  function drawBars(el, rows, valueOf, labelOf) {
    var days = lastDays(7);
    var byDay = {};
    (rows || []).forEach(function (r) { byDay[r.day] = r; });

    var max = 0;
    days.forEach(function (day) {
      var v = byDay[day] ? valueOf(byDay[day]) : 0;
      if (v > max) max = v;
    });
    if (max <= 0) max = 1;

    el.innerHTML = days.map(function (day) {
      var row = byDay[day];
      var v = row ? valueOf(row) : 0;
      var pct = Math.max(3, Math.round((v / max) * 100));
      return '<div class="bars__col">' +
        '<strong>' + (labelOf ? labelOf(v) : v) + '</strong>' +
        '<div class="bars__fill" style="height:' + pct + '%"></div>' +
        '<span>' + shortDay(day) + '</span>' +
        '</div>';
    }).join('');
  }

  /* ------------------------------------------------------------------
     horizontal bar list, already sorted by the API
  ------------------------------------------------------------------ */
  function drawHBars(el, rows, labelOf, valueOf, displayOf) {
    if (!rows || !rows.length) {
      el.innerHTML = '<p class="hint">Nothing to show yet.</p>';
      return;
    }
    var max = Math.max.apply(null, rows.map(valueOf)) || 1;
    el.innerHTML = rows.map(function (r) {
      var pct = Math.max(4, Math.round((valueOf(r) / max) * 100));
      return '<div class="hbar">' +
        '<span class="hbar__label">' + SC.escapeHtml(labelOf(r)) + '</span>' +
        '<span class="hbar__track"><span class="hbar__fill" style="width:' + pct + '%"></span></span>' +
        '<span class="hbar__value">' + SC.escapeHtml(displayOf(r)) + '</span>' +
        '</div>';
    }).join('');
  }

  /* ------------------------------------------------------------------
     load everything
  ------------------------------------------------------------------ */
  SC.api('/api/reports/summary').then(function (s) {
    set('statToday', s.todayCount);
    set('statPending', s.pendingCount);
    set('statRevenue', SC.money(s.monthRevenue));
    set('statBills', s.monthBills + ' bill' + (s.monthBills === 1 ? '' : 's') + ' this month');
    set('statPatients', s.patientCount);
  }).catch(function (err) {
    SC.toast(err.message || 'The dashboard summary could not be loaded.', 'bad');
  });

  SC.api('/api/reports/revenue?days=7').then(function (rows) {
    drawBars(document.getElementById('revenueBars'), rows,
      function (r) { return Number(r.revenue || 0); },
      function (v) { return 'Rs. ' + Math.round(v / 1000) + 'k'; });
  }).catch(function () {
    document.getElementById('revenueBars').innerHTML = '<p class="hint">The revenue trend could not be loaded.</p>';
  });

  SC.api('/api/reports/appointments?days=7').then(function (rows) {
    drawBars(document.getElementById('apptBars'), rows,
      function (r) { return Number(r.total || 0); });
  }).catch(function () {
    document.getElementById('apptBars').innerHTML = '<p class="hint">The appointment trend could not be loaded.</p>';
  });

  SC.api('/api/reports/treatments?limit=5').then(function (rows) {
    drawHBars(document.getElementById('treatmentBars'), rows,
      function (r) { return r.treatment; },
      function (r) { return r.bookings; },
      function (r) { return r.bookings + ' booking' + (r.bookings === 1 ? '' : 's'); });
  }).catch(function () {
    document.getElementById('treatmentBars').innerHTML = '<p class="hint">Treatment popularity could not be loaded.</p>';
  });

  SC.api('/api/reports/status').then(function (s) {
    var rows = [
      { label: 'Pending', value: s.Pending },
      { label: 'Confirmed', value: s.Confirmed },
      { label: 'Completed', value: s.Completed },
      { label: 'Cancelled', value: s.Cancelled }
    ];
    drawHBars(document.getElementById('statusBars'), rows,
      function (r) { return r.label; },
      function (r) { return r.value; },
      function (r) { return r.value; });
  }).catch(function () {
    document.getElementById('statusBars').innerHTML = '<p class="hint">The status breakdown could not be loaded.</p>';
  });

  SC.api('/api/reports/workload').then(function (rows) {
    drawHBars(document.getElementById('workloadBars'), rows,
      function (r) { return r.dentist; },
      function (r) { return r.appointments; },
      function (r) { return r.appointments + ' (' + r.completed + ' done)'; });
  }).catch(function () {
    document.getElementById('workloadBars').innerHTML = '<p class="hint">Dentist workload could not be loaded.</p>';
  });

}());

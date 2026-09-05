/* =====================================================================
   Reports
     GET /api/appointments?date=          daily appointments
     GET /api/reports/revenue?from=&to=   revenue over a range
     GET /api/reports/treatments?limit=   most common treatments
     GET /api/reports/patients?limit=     top patients
     GET /api/reports/workload            dentist workload
   ===================================================================== */
(function () {
  'use strict';

  if (!SC.requireAuth()) return;

  function statusClass(status) {
    return 'status status--' + String(status || 'pending').toLowerCase();
  }

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
     daily appointments
  ------------------------------------------------------------------ */
  var dayInput = document.getElementById('dayInput');
  var dayWrap = document.getElementById('dayWrap');

  function loadDay() {
    var date = dayInput.value;
    if (!date) return;
    dayWrap.innerHTML = '<div class="empty"><strong>Loading</strong><p>One moment please.</p></div>';

    SC.api('/api/appointments?date=' + encodeURIComponent(date)).then(function (list) {
      if (!list.length) {
        dayWrap.innerHTML = '<div class="empty"><strong>Nothing booked</strong><p>No appointment falls on this date.</p></div>';
        return;
      }
      var rows = list.map(function (a) {
        return '<tr>' +
          '<td class="mono">' + SC.escapeHtml(a.appointmentNo) + '</td>' +
          '<td>' + SC.escapeHtml(a.patientName) + '</td>' +
          '<td>' + SC.escapeHtml(a.dentistName || '-') + '</td>' +
          '<td>' + SC.escapeHtml(a.treatmentType || '-') + '</td>' +
          '<td>' + SC.fmtTime(a.time) + '</td>' +
          '<td><span class="' + statusClass(a.status) + '">' + SC.escapeHtml(a.status) + '</span></td>' +
          '</tr>';
      }).join('');
      dayWrap.innerHTML =
        '<div class="table-wrap"><table class="table"><thead><tr>' +
        '<th>Number</th><th>Patient</th><th>Dentist</th><th>Treatment</th><th>Time</th><th>Status</th>' +
        '</tr></thead><tbody>' + rows + '</tbody></table></div>' +
        '<p class="hint" style="margin-top:12px">' + list.length + ' appointment' + (list.length === 1 ? '' : 's') + ' on this day.</p>';
    }).catch(function (err) {
      dayWrap.innerHTML = '<div class="empty"><strong>Could not load</strong><p>' + SC.escapeHtml(err.message || '') + '</p></div>';
    });
  }

  document.getElementById('dayForm').addEventListener('submit', function (e) {
    e.preventDefault();
    loadDay();
  });
  dayInput.value = SC.today();
  loadDay();

  /* ------------------------------------------------------------------
     revenue over a date range
  ------------------------------------------------------------------ */
  var revFrom = document.getElementById('revFrom');
  var revTo = document.getElementById('revTo');
  var revenueBars = document.getElementById('revenueBars');
  var revenueTotal = document.getElementById('revenueTotal');

  function loadRevenue() {
    var from = revFrom.value, to = revTo.value;
    if (!from || !to) return;

    SC.api('/api/reports/revenue?from=' + encodeURIComponent(from) + '&to=' + encodeURIComponent(to))
      .then(function (rows) {
        var total = rows.reduce(function (sum, r) { return sum + Number(r.revenue || 0); }, 0);
        var bills = rows.reduce(function (sum, r) { return sum + Number(r.bills || 0); }, 0);
        revenueTotal.textContent = SC.money(total) + ' from ' + bills + ' bill' + (bills === 1 ? '' : 's') +
          ' between ' + SC.fmtDate(from) + ' and ' + SC.fmtDate(to) + '.';

        if (!rows.length) {
          revenueBars.innerHTML = '<p class="hint">No bills were issued in this range.</p>';
          return;
        }
        var max = Math.max.apply(null, rows.map(function (r) { return Number(r.revenue || 0); })) || 1;
        revenueBars.innerHTML = rows.map(function (r) {
          var v = Number(r.revenue || 0);
          var pct = Math.max(3, Math.round((v / max) * 100));
          return '<div class="bars__col">' +
            '<strong>' + Math.round(v / 1000) + 'k</strong>' +
            '<div class="bars__fill" style="height:' + pct + '%"></div>' +
            '<span>' + new Date(r.day + 'T00:00:00').toLocaleDateString('en-GB', { day: 'numeric', month: 'short' }) + '</span>' +
            '</div>';
        }).join('');
      }).catch(function (err) {
        revenueTotal.textContent = '';
        revenueBars.innerHTML = '<p class="hint">' + SC.escapeHtml(err.message || 'The revenue report could not be loaded.') + '</p>';
      });
  }

  document.getElementById('revenueForm').addEventListener('submit', function (e) {
    e.preventDefault();
    loadRevenue();
  });
  (function () {
    var to = new Date();
    var from = new Date();
    from.setDate(to.getDate() - 13);
    revTo.value = SC.today();
    revFrom.value = from.getFullYear() + '-' + String(from.getMonth() + 1).padStart(2, '0') + '-' + String(from.getDate()).padStart(2, '0');
  }());
  loadRevenue();

  /* ------------------------------------------------------------------
     treatments, patients, workload
  ------------------------------------------------------------------ */
  SC.api('/api/reports/treatments?limit=10').then(function (rows) {
    drawHBars(document.getElementById('treatmentBars'), rows,
      function (r) { return r.treatment; },
      function (r) { return r.bookings; },
      function (r) { return r.bookings + ' booking' + (r.bookings === 1 ? '' : 's'); });
  }).catch(function (err) {
    document.getElementById('treatmentBars').innerHTML = '<p class="hint">' + SC.escapeHtml(err.message || 'Could not load.') + '</p>';
  });

  SC.api('/api/reports/patients?limit=10').then(function (rows) {
    drawHBars(document.getElementById('patientBars'), rows,
      function (r) { return r.patientName; },
      function (r) { return Number(r.spent || 0); },
      function (r) { return SC.money(r.spent); });
  }).catch(function (err) {
    document.getElementById('patientBars').innerHTML = '<p class="hint">' + SC.escapeHtml(err.message || 'Could not load.') + '</p>';
  });

  SC.api('/api/reports/workload').then(function (rows) {
    drawHBars(document.getElementById('workloadBars'), rows,
      function (r) { return r.dentist; },
      function (r) { return r.appointments; },
      function (r) { return r.appointments + ' (' + r.completed + ' done)'; });
  }).catch(function (err) {
    document.getElementById('workloadBars').innerHTML = '<p class="hint">' + SC.escapeHtml(err.message || 'Could not load.') + '</p>';
  });

  document.getElementById('printBtn').addEventListener('click', function () {
    window.print();
  });

}());

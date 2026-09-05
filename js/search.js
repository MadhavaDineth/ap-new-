/* =====================================================================
   Find appointment
     GET /api/appointments/{no}      one record
     GET /api/appointments?date=&status=&q=   the diary
     PUT /api/appointments/{no}      status change
   ===================================================================== */
(function () {
  'use strict';

  if (!SC.requireAuth()) return;

  var findForm = document.getElementById('findForm');
  var findNo = document.getElementById('findNo');
  var findBtn = document.getElementById('findBtn');

  var detailBox = document.getElementById('detailBox');
  var detailBody = document.getElementById('detailBody');
  var detailNo = document.getElementById('detailNo');
  var detailStatus = document.getElementById('detailStatus');
  var detailBill = document.getElementById('detailBill');

  var filterForm = document.getElementById('filterForm');
  var listWrap = document.getElementById('listWrap');
  var reminderBtn = document.getElementById('sendReminder');

  var current = null;

  /* ------------------------------------------------------------------
     one record
  ------------------------------------------------------------------ */
  function row(k, v) {
    return '<div class="kv__row"><span class="kv__k">' + k +
           '</span><span class="kv__v">' + SC.escapeHtml(v) + '</span></div>';
  }

  function statusClass(status) {
    return 'status status--' + String(status || 'pending').toLowerCase();
  }

  function render(a) {
    current = a;
    detailNo.textContent = a.appointmentNo;
    detailStatus.textContent = a.status || 'Pending';
    detailStatus.className = statusClass(a.status);
    detailBill.href = 'billing.html?no=' + encodeURIComponent(a.appointmentNo);

    var total = Number(a.treatmentCost || 0) + Number(a.consultationFee || 0);
    detailBody.innerHTML =
      row('Patient', a.patientName) +
      row('Contact', a.contactNo) +
      row('Address', a.address || '-') +
      row('Dentist', a.dentistName || '-') +
      row('Treatment', a.treatmentType || '-') +
      row('Date', SC.fmtDate(a.date)) +
      row('Time', SC.fmtTime(a.time)) +
      row('Treatment cost', SC.money(a.treatmentCost)) +
      row('Consultation fee', SC.money(a.consultationFee)) +
      row('Estimated total', SC.money(total));

    // a cancelled or completed visit cannot be confirmed again
    document.querySelectorAll('[data-set-status]').forEach(function (btn) {
      var wanted = btn.getAttribute('data-set-status');
      var done = a.status === 'Completed' || a.status === 'Cancelled';
      btn.disabled = done || a.status === wanted;
      btn.style.display = done ? 'none' : '';
    });

    // a reminder only makes sense for a visit that is still going to happen
    var finished = a.status === 'Completed' || a.status === 'Cancelled';
    reminderBtn.disabled = finished;
    reminderBtn.style.display = finished ? 'none' : '';
    document.getElementById('detailHint').textContent = (a.status === 'Completed')
      ? 'This visit is finished and has been billed.'
      : 'Update the status as the visit progresses.';

    detailBox.hidden = false;
  }

  function load(no) {
    if (!no) {
      SC.toast('Type an appointment number first.', 'bad');
      findNo.focus();
      return;
    }
    findBtn.disabled = true;
    findBtn.textContent = 'Searching...';

    SC.api('/api/appointments/' + encodeURIComponent(no.toUpperCase()))
      .then(function (a) {
        render(a);
        detailBox.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
      })
      .catch(function (err) {
        detailBox.hidden = true;
        SC.toast(err.message || 'That appointment could not be found.', 'bad');
      })
      .then(function () {
        findBtn.disabled = false;
        findBtn.textContent = 'Search';
      });
  }

  findForm.addEventListener('submit', function (e) {
    e.preventDefault();
    load(findNo.value.trim());
  });

  document.getElementById('printDetail').addEventListener('click', function () {
    window.print();
  });

  /* ------------------------------------------------------------------
     status changes
  ------------------------------------------------------------------ */
  document.querySelectorAll('[data-set-status]').forEach(function (btn) {
    btn.addEventListener('click', function () {
      if (!current) return;
      var status = btn.getAttribute('data-set-status');

      if (status === 'Cancelled' &&
          !confirm('Cancel ' + current.appointmentNo + '? The slot becomes free again.')) return;

      btn.disabled = true;
      SC.api('/api/appointments/' + encodeURIComponent(current.appointmentNo),
             { method: 'PUT', body: { status: status } })
        .then(function (a) {
          render(a);
          refreshList();
          SC.toast(a.appointmentNo + ' is now ' + status.toLowerCase() + '.', 'ok');
        })
        .catch(function (err) {
          btn.disabled = false;
          SC.toast(err.message || 'The status could not be changed.', 'bad');
        });
    });
  });

  /* ------------------------------------------------------------------
     manual reminder  ->  POST /api/notifications/remind
  ------------------------------------------------------------------ */
  reminderBtn.addEventListener('click', function () {
    if (!current) return;
    reminderBtn.disabled = true;
    reminderBtn.textContent = 'Sending...';

    SC.api('/api/notifications/remind', {
      method: 'POST',
      body: { appointmentNo: current.appointmentNo }
    }).then(function () {
      SC.toast('Reminder sent to ' + current.contactNo + '.', 'ok');
    }).catch(function (err) {
      SC.toast(err.message || 'The reminder could not be sent.', 'bad');
    }).then(function () {
      reminderBtn.disabled = false;
      reminderBtn.textContent = 'Send reminder';
    });
  });

  /* ------------------------------------------------------------------
     the diary
  ------------------------------------------------------------------ */
  function refreshList() {
    var q = [];
    var date = document.getElementById('fDate2').value;
    var status = document.getElementById('fStatus').value;
    var text = document.getElementById('fText').value.trim();

    if (date) q.push('date=' + encodeURIComponent(date));
    if (status) q.push('status=' + encodeURIComponent(status));
    if (text) q.push('q=' + encodeURIComponent(text));

    SC.api('/api/appointments' + (q.length ? '?' + q.join('&') : ''))
      .then(drawList)
      .catch(function (err) {
        listWrap.innerHTML = '<div class="empty"><strong>The diary could not be loaded</strong><p>' +
          SC.escapeHtml(err.message || 'Please try again.') + '</p></div>';
      });
  }

  function drawList(list) {
    if (!list || !list.length) {
      listWrap.innerHTML = '<div class="empty"><strong>Nothing to show</strong>' +
        '<p>No appointment matches these filters.</p></div>';
      return;
    }

    var rows = list.map(function (a) {
      return '<tr data-no="' + SC.escapeHtml(a.appointmentNo) + '" style="cursor:pointer">' +
        '<td class="mono">' + SC.escapeHtml(a.appointmentNo) + '</td>' +
        '<td>' + SC.escapeHtml(a.patientName) + '<br><span class="hint">' + SC.escapeHtml(a.contactNo || '') + '</span></td>' +
        '<td>' + SC.escapeHtml(a.dentistName || '-') + '</td>' +
        '<td>' + SC.escapeHtml(a.treatmentType || '-') + '</td>' +
        '<td>' + SC.fmtDate(a.date) + '<br><span class="hint">' + SC.fmtTime(a.time) + '</span></td>' +
        '<td><span class="' + statusClass(a.status) + '">' + SC.escapeHtml(a.status || 'Pending') + '</span></td>' +
        '</tr>';
    }).join('');

    listWrap.innerHTML =
      '<div class="table-wrap"><table class="table"><thead><tr>' +
      '<th>Number</th><th>Patient</th><th>Dentist</th><th>Treatment</th><th>When</th><th>Status</th>' +
      '</tr></thead><tbody>' + rows + '</tbody></table></div>' +
      '<p class="hint" style="margin-top:12px">' + list.length +
      ' appointment' + (list.length === 1 ? '' : 's') + ' listed. Click a row to open it.</p>';

    listWrap.querySelectorAll('tbody tr').forEach(function (tr) {
      tr.addEventListener('click', function () {
        findNo.value = tr.getAttribute('data-no');
        load(findNo.value);
      });
    });
  }

  filterForm.addEventListener('submit', function (e) {
    e.preventDefault();
    refreshList();
  });

  document.getElementById('clearFilters').addEventListener('click', function () {
    filterForm.reset();
    refreshList();
  });

  /* ------------------------------------------------------------------
     start: honour ?no=APT-1042 coming from the other screens
  ------------------------------------------------------------------ */
  var preset = SC.param('no');
  if (preset) {
    findNo.value = preset;
    load(preset);
  }
  refreshList();

}());

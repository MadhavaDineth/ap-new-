/* =====================================================================
   Booking form
     GET  /api/dentists
     GET  /api/treatments
     POST /api/appointments

   Runs on two pages: appointment.html, the front desk screen inside the
   staff shell, and book.html, the public one a patient uses without any
   account. Only the sign-in check and the follow-up links differ, so both
   pages share this file rather than keeping two copies in step.
   ===================================================================== */
(function () {
  'use strict';

  var staffScreen = document.body.classList.contains('app-body');
  if (staffScreen && !SC.requireAuth()) return;

  var form = document.getElementById('apptForm');
  var bookView = document.getElementById('bookView');
  var doneView = document.getElementById('doneView');
  var saveBtn = document.getElementById('saveBtn');
  var saveLabel = saveBtn.textContent;

  var dentistSel = document.getElementById('dentistId');
  var treatmentSel = document.getElementById('treatmentId');
  var dateInput = document.getElementById('apptDate');
  var timeSel = document.getElementById('apptTime');

  var dentists = [];
  var treatments = [];

  /* ------------------------------------------------------------------
     time slots: 08.30 to 18.30, every 15 minutes
  ------------------------------------------------------------------ */
  function buildSlots() {
    var html = '<option value="">Select a time</option>';
    for (var mins = 8 * 60 + 30; mins <= 18 * 60 + 30; mins += 15) {
      var h = Math.floor(mins / 60);
      var m = mins % 60;
      var value = (h < 10 ? '0' : '') + h + ':' + (m < 10 ? '0' : '') + m;
      html += '<option value="' + value + '">' + SC.fmtTime(value) + '</option>';
    }
    timeSel.innerHTML = html;
  }

  /* ------------------------------------------------------------------
     reference data
  ------------------------------------------------------------------ */
  function loadLists() {
    Promise.all([SC.api('/api/dentists'), SC.api('/api/treatments')])
      .then(function (res) {
        dentists = res[0] || [];
        treatments = res[1] || [];

        dentistSel.innerHTML = '<option value="">Select a dentist</option>' +
          dentists.map(function (d) {
            return '<option value="' + d.id + '">' + SC.escapeHtml(d.name) +
                   ' (' + SC.money(d.consultationFee) + ')</option>';
          }).join('');

        treatmentSel.innerHTML = '<option value="">Select a treatment</option>' +
          treatments.map(function (t) {
            return '<option value="' + t.id + '">' + SC.escapeHtml(t.treatmentType) +
                   ' (' + SC.money(t.baseCost) + ')</option>';
          }).join('');

        // pre-select from the links on the home page, e.g. ?dentist=2
        var d = SC.param('dentist');
        var t = SC.param('treatment');
        if (d) dentistSel.value = d;
        if (t) treatmentSel.value = t;
        updateSummary();
      })
      .catch(function (err) {
        dentistSel.innerHTML = '<option value="">Could not load</option>';
        treatmentSel.innerHTML = '<option value="">Could not load</option>';
        SC.toast(err.message || 'The dentist and treatment lists could not be loaded.', 'bad');
      });
  }

  /* ------------------------------------------------------------------
     running total in the side rail
  ------------------------------------------------------------------ */
  function pick(list, id) {
    return list.filter(function (x) { return String(x.id) === String(id); })[0] || null;
  }

  function updateSummary() {
    var t = pick(treatments, treatmentSel.value);
    var d = pick(dentists, dentistSel.value);
    var tc = t ? Number(t.baseCost) : 0;
    var cf = d ? Number(d.consultationFee) : 0;

    document.getElementById('sumTreatmentName').textContent = t ? t.treatmentType : 'Treatment';
    document.getElementById('sumDentistName').textContent = d ? ('Consultation, ' + d.name) : 'Consultation';
    document.getElementById('sumTreatment').textContent = SC.money(tc);
    document.getElementById('sumConsult').textContent = SC.money(cf);
    document.getElementById('sumTotal').textContent = SC.money(tc + cf);
  }

  dentistSel.addEventListener('change', updateSummary);
  treatmentSel.addEventListener('change', updateSummary);

  /* ------------------------------------------------------------------
     validation
  ------------------------------------------------------------------ */
  function mark(id, bad, message) {
    var field = document.getElementById(id);
    field.classList.toggle('field--bad', bad);
    if (bad && message) field.querySelector('.err').textContent = message;
    return !bad;
  }

  form.addEventListener('input', function (e) {
    var field = e.target.closest('.field');
    if (field) field.classList.remove('field--bad');
  });

  function validate() {
    var name = document.getElementById('patientName').value.trim();
    var contact = document.getElementById('contactNo').value.replace(/\s|-/g, '');
    var address = document.getElementById('address').value.trim();
    var ok = true;

    if (!mark('fName', name.length < 3, 'Enter the full name of the patient.')) ok = false;
    if (!mark('fContact', !/^0\d{9}$/.test(contact), 'Enter a 10 digit number starting with 0.')) ok = false;
    if (!mark('fAddress', address.length < 5, 'Please enter the address of the patient.')) ok = false;
    if (!mark('fDentist', !dentistSel.value, 'Please choose a dentist.')) ok = false;
    if (!mark('fTreatment', !treatmentSel.value, 'Please choose the treatment.')) ok = false;
    if (!mark('fDate', !dateInput.value || dateInput.value < SC.today(), 'Choose today or a later date.')) ok = false;
    if (!mark('fTime', !timeSel.value, 'Please choose a time slot.')) ok = false;

    if (!ok) {
      var first = form.querySelector('.field--bad input, .field--bad select, .field--bad textarea');
      if (first) first.focus();
    }
    return ok;
  }

  /* ------------------------------------------------------------------
     save
  ------------------------------------------------------------------ */
  form.addEventListener('submit', function (e) {
    e.preventDefault();
    if (!validate()) return;

    var payload = {
      patientName: document.getElementById('patientName').value.trim(),
      contactNo: document.getElementById('contactNo').value.replace(/\s|-/g, ''),
      address: document.getElementById('address').value.trim(),
      dentistId: Number(dentistSel.value),
      treatmentId: Number(treatmentSel.value),
      date: dateInput.value,
      time: timeSel.value
    };

    saveBtn.disabled = true;
    saveBtn.textContent = 'Saving...';

    SC.api('/api/appointments', { method: 'POST', body: payload })
      .then(function (appt) {
        showDone(appt);
        SC.toast('Appointment ' + appt.appointmentNo + ' created.', 'ok');
      })
      .catch(function (err) {
        if (err.status === 409) {
          mark('fTime', true, err.message || 'That slot is already taken. Please pick another time.');
          timeSel.focus();
        }
        SC.toast(err.message || 'The appointment could not be saved.', 'bad');
      })
      .then(function () {
        saveBtn.disabled = false;
        saveBtn.textContent = saveLabel;
      });
  });

  /* ------------------------------------------------------------------
     confirmation screen
  ------------------------------------------------------------------ */
  function row(k, v) {
    return '<div class="kv__row"><span class="kv__k">' + k +
           '</span><span class="kv__v">' + SC.escapeHtml(v) + '</span></div>';
  }

  function showDone(a) {
    document.getElementById('doneNo').textContent = a.appointmentNo;

    // the billing and diary links only exist on the staff screen
    var billLink = document.getElementById('doneBill');
    var recordLink = document.getElementById('doneView2');
    if (billLink) billLink.href = 'billing.html?no=' + encodeURIComponent(a.appointmentNo);
    if (recordLink) recordLink.href = 'search.html?no=' + encodeURIComponent(a.appointmentNo);

    var total = Number(a.treatmentCost || 0) + Number(a.consultationFee || 0);
    document.getElementById('doneRecap').innerHTML =
      row('Patient', a.patientName) +
      row('Contact', a.contactNo) +
      row('Dentist', a.dentistName || '-') +
      row('Treatment', a.treatmentType || '-') +
      row('Date', SC.fmtDate(a.date)) +
      row('Time', SC.fmtTime(a.time)) +
      row('Status', a.status || 'Pending') +
      row('Estimated total', SC.money(total));

    bookView.hidden = true;
    doneView.hidden = false;
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  document.getElementById('doneAgain').addEventListener('click', function () {
    form.reset();
    dateInput.value = SC.today();
    updateSummary();
    doneView.hidden = true;
    bookView.hidden = false;
    document.getElementById('patientName').focus();
  });

  /* ------------------------------------------------------------------
     start
  ------------------------------------------------------------------ */
  buildSlots();
  dateInput.min = SC.today();
  dateInput.value = SC.today();
  loadLists();

}());

/* =====================================================================
   Bills and receipts
     GET  /api/appointments/{no}   the visit being billed
     GET  /api/bills/{no}          a receipt that already exists
     POST /api/bills               issue a new receipt
   ===================================================================== */
(function () {
  'use strict';

  var user = SC.requireAuth();
  if (!user) return;

  var billForm = document.getElementById('billForm');
  var billNo = document.getElementById('billNo');
  var billBtn = document.getElementById('billBtn');

  var chargeBox = document.getElementById('chargeBox');
  var chargeBody = document.getElementById('chargeBody');
  var chargeStatus = document.getElementById('chargeStatus');
  var issueBtn = document.getElementById('issueBtn');

  var receiptWrap = document.getElementById('receiptWrap');
  var pricingChips = document.getElementById('pricingChips');

  var appt = null;

  /* The same four rules the server applies, so the preview matches the
     receipt exactly. Server side these live in the PricingStrategy
     classes; the amounts are worked out again there before anything is
     written, so the browser can never talk the clinic into a discount. */
  var PRICING = {
    standard:  { label: 'Standard',          treatment: 1,   consultation: 1 },
    senior:    { label: 'Senior citizen',    treatment: 1,   consultation: 0.85 },
    insurance: { label: 'Insurance',         treatment: 0.8, consultation: 0.8 },
    loyalty:   { label: 'Loyalty discount',  treatment: 0.9, consultation: 1 }
  };

  function chosenPricing() {
    var picked = pricingChips.querySelector('input[name="pricing"]:checked');
    return picked ? picked.value : 'standard';
  }

  function round2(n) { return Math.round(n * 100) / 100; }

  function row(k, v) {
    return '<div class="kv__row"><span class="kv__k">' + k +
           '</span><span class="kv__v">' + SC.escapeHtml(v) + '</span></div>';
  }

  function set(id, text) { document.getElementById(id).textContent = text; }

  /* ------------------------------------------------------------------
     load a visit, and its receipt if one was already issued
  ------------------------------------------------------------------ */
  function load(no) {
    if (!no) {
      SC.toast('Type an appointment number first.', 'bad');
      billNo.focus();
      return;
    }

    billBtn.disabled = true;
    billBtn.textContent = 'Loading...';
    receiptWrap.hidden = true;
    chargeBox.hidden = true;

    SC.api('/api/appointments/' + encodeURIComponent(no.toUpperCase()))
      .then(function (a) {
        appt = a;
        showCharges(a);
        // an already issued receipt is shown instead of the preview
        return SC.api('/api/bills/' + encodeURIComponent(a.appointmentNo))
          .then(function (bill) {
            showReceipt(bill);
            chargeBox.hidden = true;
            SC.toast('This visit was already billed. Showing the receipt.');
          })
          .catch(function () { /* no bill yet, the preview stays on screen */ });
      })
      .catch(function (err) {
        SC.toast(err.message || 'That appointment could not be found.', 'bad');
      })
      .then(function () {
        billBtn.disabled = false;
        billBtn.textContent = 'Load';
      });
  }

  function showCharges(a) {
    chargeStatus.textContent = a.status || 'Pending';
    chargeStatus.className = 'status status--' + String(a.status || 'pending').toLowerCase();

    chargeBody.innerHTML =
      row('Appointment', a.appointmentNo) +
      row('Patient', a.patientName) +
      row('Contact', a.contactNo) +
      row('Dentist', a.dentistName || '-') +
      row('Treatment', a.treatmentType || '-') +
      row('Visit', SC.fmtDate(a.date) + ', ' + SC.fmtTime(a.time));

    set('cTreatName', a.treatmentType || 'Treatment');
    set('cConsultName', 'Consultation, ' + (a.dentistName || 'dentist'));
    updatePreview();

    issueBtn.disabled = a.status === 'Cancelled';
    chargeBox.hidden = false;
  }

  /* recalculates the preview for whichever pricing rule is selected */
  function updatePreview() {
    if (!appt) return;
    var rule = PRICING[chosenPricing()] || PRICING.standard;
    var tc = round2(Number(appt.treatmentCost || 0) * rule.treatment);
    var cf = round2(Number(appt.consultationFee || 0) * rule.consultation);

    set('cTreat', SC.money(tc));
    set('cConsult', SC.money(cf));
    set('cTotal', SC.money(tc + cf));
  }

  pricingChips.addEventListener('change', updatePreview);

  /* ------------------------------------------------------------------
     issue
  ------------------------------------------------------------------ */
  issueBtn.addEventListener('click', function () {
    if (!appt) return;
    issueBtn.disabled = true;
    issueBtn.textContent = 'Issuing...';

    SC.api('/api/bills', {
      method: 'POST',
      body: { appointmentNo: appt.appointmentNo, pricingStrategy: chosenPricing() }
    })
      .then(function (bill) {
        showReceipt(bill);
        chargeBox.hidden = true;
        SC.toast('Receipt ' + bill.billNo + ' issued.', 'ok');
      })
      .catch(function (err) {
        SC.toast(err.message || 'The bill could not be issued.', 'bad');
      })
      .then(function () {
        issueBtn.disabled = false;
        issueBtn.textContent = 'Issue the bill';
      });
  });

  /* ------------------------------------------------------------------
     receipt
  ------------------------------------------------------------------ */
  function showReceipt(b) {
    set('rBillNo', b.billNo);
    set('rPatient', b.patientName);
    set('rAppt', b.appointmentNo);
    set('rDentist', b.dentistName || '-');
    set('rIssued', SC.fmtDate(b.issuedAt));
    set('rTreatName', b.treatmentType || 'Treatment');
    set('rTreat', SC.money(b.treatmentCost).replace('Rs. ', ''));
    set('rConsult', SC.money(b.consultationFee).replace('Rs. ', ''));
    set('rTotal', SC.money(b.total));
    set('rPricing', (PRICING[b.pricingStrategy] || {}).label || b.pricingStrategy || 'Standard');
    set('rBy', (user && user.fullName) ? user.fullName : 'front desk');

    receiptWrap.hidden = false;
    receiptWrap.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
  }

  document.getElementById('printBtn').addEventListener('click', function () {
    window.print();
  });

  document.getElementById('newBillBtn').addEventListener('click', function () {
    receiptWrap.hidden = true;
    chargeBox.hidden = true;
    appt = null;
    billNo.value = '';
    pricingChips.querySelector('input[value="standard"]').checked = true;
    billNo.focus();
  });

  billForm.addEventListener('submit', function (e) {
    e.preventDefault();
    load(billNo.value.trim());
  });

  /* start: ?no=APT-1042 from the booking or search screen */
  var preset = SC.param('no');
  if (preset) {
    billNo.value = preset;
    load(preset);
  }

}());

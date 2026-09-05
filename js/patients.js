/* =====================================================================
   Patients
     GET    /api/patients?q=          list / search
     GET    /api/patients/{id}/history  treatment history
     POST   /api/patients             add
     PUT    /api/patients/{id}        edit
     DELETE /api/patients/{id}        remove (blocked if they have visits)
   ===================================================================== */
(function () {
  'use strict';

  if (!SC.requireAuth()) return;

  var form = document.getElementById('patientForm');
  var idField = document.getElementById('patientId');
  var nameField = document.getElementById('pName');
  var contactField = document.getElementById('pContact');
  var addressField = document.getElementById('pAddress');
  var formTitle = document.getElementById('formTitle');
  var formHint = document.getElementById('formHint');
  var saveBtn = document.getElementById('saveBtn');
  var cancelEdit = document.getElementById('cancelEdit');

  var searchForm = document.getElementById('searchForm');
  var searchText = document.getElementById('searchText');
  var listWrap = document.getElementById('listWrap');

  var historyBox = document.getElementById('historyBox');
  var historyTitle = document.getElementById('historyTitle');
  var historyWrap = document.getElementById('historyWrap');

  function setBad(field, bad, message) {
    field.classList.toggle('field--bad', bad);
    if (bad && message) field.querySelector('.err').textContent = message;
  }

  /* ------------------------------------------------------------------
     add / edit form
  ------------------------------------------------------------------ */
  function startEdit(p) {
    idField.value = p.id;
    nameField.value = p.name;
    contactField.value = p.contactNo;
    addressField.value = p.address;
    formTitle.textContent = 'Edit ' + p.name;
    formHint.innerHTML = 'Fields marked <span class="req">*</span> are required.';
    saveBtn.textContent = 'Save changes';
    cancelEdit.hidden = false;
    form.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  function resetForm() {
    form.reset();
    idField.value = '';
    formTitle.textContent = 'Add a patient';
    saveBtn.textContent = 'Add patient';
    cancelEdit.hidden = true;
    [nameField, contactField, addressField].forEach(function (f) {
      setBad(f.closest('.field'), false);
    });
  }

  cancelEdit.addEventListener('click', resetForm);

  form.addEventListener('submit', function (e) {
    e.preventDefault();

    var name = nameField.value.trim();
    var contact = contactField.value.trim();
    var address = addressField.value.trim();
    var ok = true;

    if (name.length < 3) { setBad(nameField.closest('.field'), true); ok = false; }
    else setBad(nameField.closest('.field'), false);

    if (!/^0\d{9}$/.test(contact)) { setBad(contactField.closest('.field'), true); ok = false; }
    else setBad(contactField.closest('.field'), false);

    if (address.length < 5) { setBad(addressField.closest('.field'), true); ok = false; }
    else setBad(addressField.closest('.field'), false);

    if (!ok) return;

    var body = { name: name, contactNo: contact, address: address };
    var editingId = idField.value;
    var request = editingId
      ? SC.api('/api/patients/' + editingId, { method: 'PUT', body: body })
      : SC.api('/api/patients', { method: 'POST', body: body });

    saveBtn.disabled = true;
    request.then(function (p) {
      SC.toast((editingId ? 'Updated ' : 'Added ') + p.name + '.', 'ok');
      resetForm();
      refreshList();
    }).catch(function (err) {
      SC.toast(err.message || 'The patient could not be saved.', 'bad');
    }).then(function () {
      saveBtn.disabled = false;
    });
  });

  /* ------------------------------------------------------------------
     list + search
  ------------------------------------------------------------------ */
  function refreshList() {
    var q = searchText.value.trim();
    SC.api('/api/patients' + (q ? '?q=' + encodeURIComponent(q) : ''))
      .then(drawList)
      .catch(function (err) {
        listWrap.innerHTML = '<div class="empty"><strong>The patient list could not be loaded</strong><p>' +
          SC.escapeHtml(err.message || 'Please try again.') + '</p></div>';
      });
  }

  function drawList(list) {
    if (!list || !list.length) {
      listWrap.innerHTML = '<div class="empty"><strong>No patients found</strong><p>Try a different search, or add a new patient above.</p></div>';
      return;
    }

    var rows = list.map(function (p) {
      return '<tr>' +
        '<td>' + SC.escapeHtml(p.name) + '</td>' +
        '<td class="mono">' + SC.escapeHtml(p.contactNo) + '</td>' +
        '<td>' + SC.escapeHtml(p.address) + '</td>' +
        '<td class="num">' +
          '<button class="btn btn--quiet" type="button" data-history="' + p.id + '" data-name="' + SC.escapeHtml(p.name) + '">History</button> ' +
          '<button class="btn btn--ghost" type="button" data-edit="' + p.id + '">Edit</button> ' +
          '<button class="btn btn--ghost" type="button" data-delete="' + p.id + '">Delete</button>' +
        '</td>' +
        '</tr>';
    }).join('');

    listWrap.innerHTML =
      '<div class="table-wrap"><table class="table"><thead><tr>' +
      '<th>Name</th><th>Contact</th><th>Address</th><th class="num">Actions</th>' +
      '</tr></thead><tbody>' + rows + '</tbody></table></div>' +
      '<p class="hint" style="margin-top:12px">' + list.length + ' patient' + (list.length === 1 ? '' : 's') + ' listed.</p>';

    listWrap.querySelectorAll('[data-edit]').forEach(function (btn) {
      btn.addEventListener('click', function () {
        var id = btn.getAttribute('data-edit');
        SC.api('/api/patients/' + id).then(startEdit).catch(function (err) {
          SC.toast(err.message || 'That patient could not be loaded.', 'bad');
        });
      });
    });

    listWrap.querySelectorAll('[data-delete]').forEach(function (btn) {
      btn.addEventListener('click', function () {
        var id = btn.getAttribute('data-delete');
        if (!confirm('Delete this patient? This cannot be undone.')) return;
        btn.disabled = true;
        SC.api('/api/patients/' + id, { method: 'DELETE' }).then(function () {
          SC.toast('Patient deleted.', 'ok');
          refreshList();
        }).catch(function (err) {
          btn.disabled = false;
          SC.toast(err.message || 'That patient could not be deleted.', 'bad');
        });
      });
    });

    listWrap.querySelectorAll('[data-history]').forEach(function (btn) {
      btn.addEventListener('click', function () {
        showHistory(btn.getAttribute('data-history'), btn.getAttribute('data-name'));
      });
    });
  }

  /* ------------------------------------------------------------------
     treatment history
  ------------------------------------------------------------------ */
  function showHistory(id, name) {
    historyTitle.textContent = 'Treatment history: ' + name;
    historyWrap.innerHTML = '<div class="empty"><strong>Loading</strong><p>One moment please.</p></div>';
    historyBox.hidden = false;
    historyBox.scrollIntoView({ behavior: 'smooth', block: 'nearest' });

    SC.api('/api/patients/' + id + '/history').then(function (list) {
      if (!list.length) {
        historyWrap.innerHTML = '<div class="empty"><strong>No visits yet</strong><p>This patient has no appointments on file.</p></div>';
        return;
      }
      var rows = list.map(function (a) {
        return '<tr>' +
          '<td class="mono">' + SC.escapeHtml(a.appointmentNo) + '</td>' +
          '<td>' + SC.fmtDate(a.date) + '<br><span class="hint">' + SC.fmtTime(a.time) + '</span></td>' +
          '<td>' + SC.escapeHtml(a.dentistName || '-') + '</td>' +
          '<td>' + SC.escapeHtml(a.treatmentType || '-') + '</td>' +
          '<td><span class="status status--' + String(a.status || 'pending').toLowerCase() + '">' + SC.escapeHtml(a.status) + '</span></td>' +
          '</tr>';
      }).join('');
      historyWrap.innerHTML =
        '<div class="table-wrap"><table class="table"><thead><tr>' +
        '<th>Number</th><th>When</th><th>Dentist</th><th>Treatment</th><th>Status</th>' +
        '</tr></thead><tbody>' + rows + '</tbody></table></div>';
    }).catch(function (err) {
      historyWrap.innerHTML = '<div class="empty"><strong>The history could not be loaded</strong><p>' +
        SC.escapeHtml(err.message || 'Please try again.') + '</p></div>';
    });
  }

  document.getElementById('closeHistory').addEventListener('click', function () {
    historyBox.hidden = true;
  });

  searchForm.addEventListener('submit', function (e) {
    e.preventDefault();
    refreshList();
  });
  document.getElementById('clearSearch').addEventListener('click', function () {
    searchText.value = '';
    refreshList();
  });

  refreshList();

}());

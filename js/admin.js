/* =====================================================================
   Admin panel
     GET/POST/PUT /api/admin/dentists      /api/admin/treatments      /api/admin/users
     GET          /api/admin/audit?limit=
   Administrators only - AdminHandler answers everyone else with a 403.
   ===================================================================== */
(function () {
  'use strict';

  var user = SC.requireAuth();
  if (!user) return;

  var isAdmin = String(user.role).toLowerCase() === 'admin';
  document.getElementById('notAdminNotice').hidden = isAdmin;
  document.getElementById('adminContent').hidden = !isAdmin;
  if (!isAdmin) return;

  function setBad(field, bad) { field.classList.toggle('field--bad', bad); }
  function money(v) { return SC.money(v).replace('Rs. ', ''); }

  /* ------------------------------------------------------------------
     tabs
  ------------------------------------------------------------------ */
  var tabButtons = document.querySelectorAll('[data-tab]');
  tabButtons.forEach(function (btn) {
    btn.addEventListener('click', function () {
      var name = btn.getAttribute('data-tab');
      tabButtons.forEach(function (b) {
        b.classList.toggle('btn--primary', b === btn);
        b.classList.toggle('btn--ghost', b !== btn);
      });
      document.querySelectorAll('[data-panel]').forEach(function (p) {
        p.hidden = p.getAttribute('data-panel') !== name;
      });
    });
  });

  /* ==================================================================
     DENTISTS
  ================================================================== */
  var dentistForm = document.getElementById('dentistForm');
  var dentistId = document.getElementById('dentistId');
  var dName = document.getElementById('dName');
  var dQual = document.getElementById('dQual');
  var dSpec = document.getElementById('dSpec');
  var dFee = document.getElementById('dFee');
  var dActive = document.getElementById('dActive');
  var dActiveField = document.getElementById('dActiveField');
  var dentistFormTitle = document.getElementById('dentistFormTitle');
  var dentistSave = document.getElementById('dentistSave');
  var dentistCancel = document.getElementById('dentistCancel');
  var dentistList = document.getElementById('dentistList');

  function resetDentistForm() {
    dentistForm.reset();
    dentistId.value = '';
    dActiveField.hidden = true;
    dentistFormTitle.textContent = 'Add a dentist';
    dentistSave.textContent = 'Add dentist';
    dentistCancel.hidden = true;
  }

  dentistCancel.addEventListener('click', resetDentistForm);

  function loadDentists() {
    SC.api('/api/admin/dentists').then(function (list) {
      if (!list.length) {
        dentistList.innerHTML = '<div class="empty"><strong>No dentists yet</strong></div>';
        return;
      }
      dentistList.innerHTML =
        '<div class="table-wrap"><table class="table"><thead><tr>' +
        '<th>Name</th><th>Qualification</th><th>Speciality</th><th class="num">Fee</th><th>Status</th><th class="num">Actions</th>' +
        '</tr></thead><tbody>' + list.map(function (d) {
          return '<tr>' +
            '<td>' + SC.escapeHtml(d.name) + '</td>' +
            '<td>' + SC.escapeHtml(d.qualification || '-') + '</td>' +
            '<td>' + SC.escapeHtml(d.speciality || '-') + '</td>' +
            '<td class="num">' + money(d.consultationFee) + '</td>' +
            '<td><span class="status status--' + (d.active ? 'confirmed' : 'cancelled') + '">' + (d.active ? 'Active' : 'Retired') + '</span></td>' +
            '<td class="num"><button class="btn btn--ghost" type="button" data-edit-dentist="' + d.id + '">Edit</button></td>' +
            '</tr>';
        }).join('') + '</tbody></table></div>';

      dentistList.querySelectorAll('[data-edit-dentist]').forEach(function (btn) {
        btn.addEventListener('click', function () {
          var d = list.filter(function (x) { return String(x.id) === btn.getAttribute('data-edit-dentist'); })[0];
          if (!d) return;
          dentistId.value = d.id;
          dName.value = d.name;
          dQual.value = d.qualification || '';
          dSpec.value = d.speciality || '';
          dFee.value = d.consultationFee;
          dActive.checked = !!d.active;
          dActiveField.hidden = false;
          dentistFormTitle.textContent = 'Edit ' + d.name;
          dentistSave.textContent = 'Save changes';
          dentistCancel.hidden = false;
          dentistForm.scrollIntoView({ behavior: 'smooth', block: 'start' });
        });
      });
    }).catch(function (err) {
      dentistList.innerHTML = '<div class="empty"><strong>Could not load</strong><p>' + SC.escapeHtml(err.message || '') + '</p></div>';
    });
  }

  dentistForm.addEventListener('submit', function (e) {
    e.preventDefault();
    var name = dName.value.trim();
    var fee = dFee.value;
    var ok = true;
    if (name.length < 2) { setBad(dName.closest('.field'), true); ok = false; } else setBad(dName.closest('.field'), false);
    if (fee === '' || Number(fee) < 0) { setBad(dFee.closest('.field'), true); ok = false; } else setBad(dFee.closest('.field'), false);
    if (!ok) return;

    var body = { name: name, qualification: dQual.value.trim(), speciality: dSpec.value.trim(), consultationFee: fee };
    var editingId = dentistId.value;
    if (editingId) body.active = dActive.checked;

    var req = editingId
      ? SC.api('/api/admin/dentists/' + editingId, { method: 'PUT', body: body })
      : SC.api('/api/admin/dentists', { method: 'POST', body: body });

    dentistSave.disabled = true;
    req.then(function () {
      SC.toast('Dentist saved.', 'ok');
      resetDentistForm();
      loadDentists();
    }).catch(function (err) {
      SC.toast(err.message || 'The dentist could not be saved.', 'bad');
    }).then(function () { dentistSave.disabled = false; });
  });

  /* ==================================================================
     TREATMENTS
  ================================================================== */
  var treatmentForm = document.getElementById('treatmentForm');
  var treatmentId = document.getElementById('treatmentId');
  var tType = document.getElementById('tType');
  var tCost = document.getElementById('tCost');
  var tMins = document.getElementById('tMins');
  var tActive = document.getElementById('tActive');
  var tActiveField = document.getElementById('tActiveField');
  var treatmentFormTitle = document.getElementById('treatmentFormTitle');
  var treatmentSave = document.getElementById('treatmentSave');
  var treatmentCancel = document.getElementById('treatmentCancel');
  var treatmentList = document.getElementById('treatmentList');

  function resetTreatmentForm() {
    treatmentForm.reset();
    treatmentId.value = '';
    tMins.value = 30;
    tActiveField.hidden = true;
    treatmentFormTitle.textContent = 'Add a treatment';
    treatmentSave.textContent = 'Add treatment';
    treatmentCancel.hidden = true;
  }

  treatmentCancel.addEventListener('click', resetTreatmentForm);

  function loadTreatments() {
    SC.api('/api/admin/treatments').then(function (list) {
      if (!list.length) {
        treatmentList.innerHTML = '<div class="empty"><strong>No treatments yet</strong></div>';
        return;
      }
      treatmentList.innerHTML =
        '<div class="table-wrap"><table class="table"><thead><tr>' +
        '<th>Treatment</th><th class="num">Base cost</th><th class="num">Minutes</th><th>Status</th><th class="num">Actions</th>' +
        '</tr></thead><tbody>' + list.map(function (t) {
          return '<tr>' +
            '<td>' + SC.escapeHtml(t.treatmentType) + '</td>' +
            '<td class="num">' + money(t.baseCost) + '</td>' +
            '<td class="num">' + t.durationMins + '</td>' +
            '<td><span class="status status--' + (t.active ? 'confirmed' : 'cancelled') + '">' + (t.active ? 'Offered' : 'Withdrawn') + '</span></td>' +
            '<td class="num"><button class="btn btn--ghost" type="button" data-edit-treatment="' + t.id + '">Edit</button></td>' +
            '</tr>';
        }).join('') + '</tbody></table></div>';

      treatmentList.querySelectorAll('[data-edit-treatment]').forEach(function (btn) {
        btn.addEventListener('click', function () {
          var t = list.filter(function (x) { return String(x.id) === btn.getAttribute('data-edit-treatment'); })[0];
          if (!t) return;
          treatmentId.value = t.id;
          tType.value = t.treatmentType;
          tCost.value = t.baseCost;
          tMins.value = t.durationMins;
          tActive.checked = !!t.active;
          tActiveField.hidden = false;
          treatmentFormTitle.textContent = 'Edit ' + t.treatmentType;
          treatmentSave.textContent = 'Save changes';
          treatmentCancel.hidden = false;
          treatmentForm.scrollIntoView({ behavior: 'smooth', block: 'start' });
        });
      });
    }).catch(function (err) {
      treatmentList.innerHTML = '<div class="empty"><strong>Could not load</strong><p>' + SC.escapeHtml(err.message || '') + '</p></div>';
    });
  }

  treatmentForm.addEventListener('submit', function (e) {
    e.preventDefault();
    var type = tType.value.trim();
    var cost = tCost.value;
    var mins = tMins.value;
    var ok = true;
    if (type.length < 2) { setBad(tType.closest('.field'), true); ok = false; } else setBad(tType.closest('.field'), false);
    if (cost === '' || Number(cost) < 0) { setBad(tCost.closest('.field'), true); ok = false; } else setBad(tCost.closest('.field'), false);
    if (mins === '' || Number(mins) < 5 || Number(mins) > 480) { setBad(tMins.closest('.field'), true); ok = false; } else setBad(tMins.closest('.field'), false);
    if (!ok) return;

    var body = { treatmentType: type, baseCost: cost, durationMins: Number(mins) };
    var editingId = treatmentId.value;
    if (editingId) body.active = tActive.checked;

    var req = editingId
      ? SC.api('/api/admin/treatments/' + editingId, { method: 'PUT', body: body })
      : SC.api('/api/admin/treatments', { method: 'POST', body: body });

    treatmentSave.disabled = true;
    req.then(function () {
      SC.toast('Treatment saved.', 'ok');
      resetTreatmentForm();
      loadTreatments();
    }).catch(function (err) {
      SC.toast(err.message || 'The treatment could not be saved.', 'bad');
    }).then(function () { treatmentSave.disabled = false; });
  });

  /* ==================================================================
     STAFF USERS
  ================================================================== */
  var userForm = document.getElementById('userForm');
  var userId = document.getElementById('userId');
  var uUsername = document.getElementById('uUsername');
  var uUserField = document.getElementById('uUserField');
  var uFullName = document.getElementById('uFullName');
  var uRole = document.getElementById('uRole');
  var uPassword = document.getElementById('uPassword');
  var uPasswordLabel = document.getElementById('uPasswordLabel');
  var uActive = document.getElementById('uActive');
  var uActiveField = document.getElementById('uActiveField');
  var userFormTitle = document.getElementById('userFormTitle');
  var userHint = document.getElementById('userHint');
  var userSave = document.getElementById('userSave');
  var userCancel = document.getElementById('userCancel');
  var userList = document.getElementById('userList');

  function resetUserForm() {
    userForm.reset();
    userId.value = '';
    uUsername.disabled = false;
    uUserField.hidden = false;
    uPasswordLabel.innerHTML = 'Password <span class="req">*</span>';
    uPassword.placeholder = 'At least 6 characters';
    uActiveField.hidden = true;
    userFormTitle.textContent = 'Add a staff account';
    userHint.innerHTML = 'Fields marked <span class="req">*</span> are required.';
    userSave.textContent = 'Add account';
    userCancel.hidden = true;
  }

  userCancel.addEventListener('click', resetUserForm);

  function loadUsers() {
    SC.api('/api/admin/users').then(function (list) {
      if (!list.length) {
        userList.innerHTML = '<div class="empty"><strong>No accounts yet</strong></div>';
        return;
      }
      userList.innerHTML =
        '<div class="table-wrap"><table class="table"><thead><tr>' +
        '<th>Username</th><th>Full name</th><th>Role</th><th>Status</th><th class="num">Actions</th>' +
        '</tr></thead><tbody>' + list.map(function (u) {
          return '<tr>' +
            '<td class="mono">' + SC.escapeHtml(u.username) + '</td>' +
            '<td>' + SC.escapeHtml(u.fullName) + '</td>' +
            '<td style="text-transform:capitalize">' + SC.escapeHtml(u.role) + '</td>' +
            '<td><span class="status status--' + (u.active ? 'confirmed' : 'cancelled') + '">' + (u.active ? 'Active' : 'Suspended') + '</span></td>' +
            '<td class="num"><button class="btn btn--ghost" type="button" data-edit-user="' + u.id + '">Edit</button></td>' +
            '</tr>';
        }).join('') + '</tbody></table></div>';

      userList.querySelectorAll('[data-edit-user]').forEach(function (btn) {
        btn.addEventListener('click', function () {
          var u = list.filter(function (x) { return String(x.id) === btn.getAttribute('data-edit-user'); })[0];
          if (!u) return;
          userId.value = u.id;
          uUsername.value = u.username;
          uUsername.disabled = true;
          uFullName.value = u.fullName;
          uRole.value = u.role;
          uPassword.value = '';
          uPasswordLabel.textContent = 'New password';
          uPassword.placeholder = 'Leave blank to keep the current password';
          uActive.checked = !!u.active;
          uActiveField.hidden = false;
          userFormTitle.textContent = 'Edit ' + u.fullName;
          userHint.textContent = 'Leave the password blank to keep it unchanged.';
          userSave.textContent = 'Save changes';
          userCancel.hidden = false;
          userForm.scrollIntoView({ behavior: 'smooth', block: 'start' });
        });
      });
    }).catch(function (err) {
      userList.innerHTML = '<div class="empty"><strong>Could not load</strong><p>' + SC.escapeHtml(err.message || '') + '</p></div>';
    });
  }

  userForm.addEventListener('submit', function (e) {
    e.preventDefault();
    var editingId = userId.value;
    var fullName = uFullName.value.trim();
    var password = uPassword.value;
    var ok = true;

    if (fullName.length < 2) { setBad(uFullName.closest('.field'), true); ok = false; } else setBad(uFullName.closest('.field'), false);
    if (!editingId) {
      var username = uUsername.value.trim();
      if (!/^[a-z0-9._-]{3,40}$/i.test(username)) { setBad(uUserField, true); ok = false; } else setBad(uUserField, false);
      if (password.length < 6) { setBad(uPassword.closest('.field'), true); ok = false; } else setBad(uPassword.closest('.field'), false);
    } else if (password && password.length < 6) {
      setBad(uPassword.closest('.field'), true); ok = false;
    } else {
      setBad(uPassword.closest('.field'), false);
    }
    if (!ok) return;

    var req;
    userSave.disabled = true;
    if (editingId) {
      req = SC.api('/api/admin/users/' + editingId, {
        method: 'PUT',
        body: { fullName: fullName, role: uRole.value, active: uActive.checked, password: password }
      });
    } else {
      req = SC.api('/api/admin/users', {
        method: 'POST',
        body: { username: uUsername.value.trim(), fullName: fullName, role: uRole.value, password: password }
      });
    }

    req.then(function () {
      SC.toast('Staff account saved.', 'ok');
      resetUserForm();
      loadUsers();
    }).catch(function (err) {
      SC.toast(err.message || 'The account could not be saved.', 'bad');
    }).then(function () { userSave.disabled = false; });
  });

  /* ==================================================================
     AUDIT LOG
  ================================================================== */
  var auditList = document.getElementById('auditList');

  function loadAudit() {
    auditList.innerHTML = '<div class="empty"><strong>Loading</strong></div>';
    SC.api('/api/admin/audit?limit=100').then(function (list) {
      if (!list.length) {
        auditList.innerHTML = '<div class="empty"><strong>Nothing logged yet</strong></div>';
        return;
      }
      auditList.innerHTML =
        '<div class="table-wrap"><table class="table"><thead><tr>' +
        '<th>When</th><th>Who</th><th>Action</th><th>Reference</th><th>Details</th>' +
        '</tr></thead><tbody>' + list.map(function (a) {
          return '<tr>' +
            '<td>' + SC.fmtDate(a.loggedAt) + '</td>' +
            '<td>' + SC.escapeHtml(a.who) + '</td>' +
            '<td class="mono">' + SC.escapeHtml(a.action) + '</td>' +
            '<td class="mono">' + SC.escapeHtml(a.reference) + '</td>' +
            '<td>' + SC.escapeHtml(a.details) + '</td>' +
            '</tr>';
        }).join('') + '</tbody></table></div>';
    }).catch(function (err) {
      auditList.innerHTML = '<div class="empty"><strong>Could not load</strong><p>' + SC.escapeHtml(err.message || '') + '</p></div>';
    });
  }

  document.getElementById('auditRefresh').addEventListener('click', loadAudit);

  loadDentists();
  loadTreatments();
  loadUsers();
  loadAudit();

}());

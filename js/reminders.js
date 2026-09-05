/* =====================================================================
   Reminders  ->  GET /api/notifications?limit=100
   ===================================================================== */
(function () {
  'use strict';

  if (!SC.requireAuth()) return;

  var listWrap = document.getElementById('listWrap');

  function load() {
    listWrap.innerHTML = '<div class="empty"><strong>Loading</strong><p>One moment please.</p></div>';

    SC.api('/api/notifications?limit=100').then(function (list) {
      if (!list.length) {
        listWrap.innerHTML = '<div class="empty"><strong>Nothing sent yet</strong>' +
          '<p>Confirmations, reminders and receipts will appear here as they go out.</p></div>';
        return;
      }

      var rows = list.map(function (n) {
        return '<tr>' +
          '<td><span class="status status--' + String(n.type || '').toLowerCase() + '">' + SC.escapeHtml(n.type) + '</span></td>' +
          '<td class="mono">' + SC.escapeHtml(n.appointmentNo) + '</td>' +
          '<td>' + SC.escapeHtml(n.recipient) + '</td>' +
          '<td>' + SC.escapeHtml(n.subject) + '</td>' +
          '<td>' + SC.fmtDate(n.sentAt) + '</td>' +
          '</tr>';
      }).join('');

      listWrap.innerHTML =
        '<div class="table-wrap"><table class="table"><thead><tr>' +
        '<th>Type</th><th>Appointment</th><th>Recipient</th><th>Subject</th><th>Sent</th>' +
        '</tr></thead><tbody>' + rows + '</tbody></table></div>' +
        '<p class="hint" style="margin-top:12px">' + list.length + ' notification' + (list.length === 1 ? '' : 's') + ' listed.</p>';
    }).catch(function (err) {
      listWrap.innerHTML = '<div class="empty"><strong>The reminders log could not be loaded</strong><p>' +
        SC.escapeHtml(err.message || 'Please try again.') + '</p></div>';
    });
  }

  document.getElementById('refreshBtn').addEventListener('click', load);
  load();

}());

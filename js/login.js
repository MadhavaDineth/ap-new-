/* =====================================================================
   Staff login  ->  POST /api/auth/login
   ===================================================================== */
(function () {
  'use strict';

  var form = document.getElementById('loginForm');
  var userField = document.getElementById('fUser');
  var passField = document.getElementById('fPass');
  var username = document.getElementById('username');
  var password = document.getElementById('password');
  var remember = document.getElementById('remember');
  var button = document.getElementById('loginBtn');
  var toggle = document.getElementById('pwToggle');

  var REMEMBER_KEY = 'sc_last_user';

  /* already signed in? go straight through */
  if (SC.session.get()) {
    location.replace(nextPage());
  }

  /* pre-fill the remembered username */
  try {
    var saved = localStorage.getItem(REMEMBER_KEY);
    if (saved) {
      username.value = saved;
      remember.checked = true;
      password.focus();
    }
  } catch (e) {}

  /* messages carried in the query string */
  if (SC.param('bye')) SC.toast('You have been signed out.', 'ok');
  if (SC.param('next')) SC.toast('Please sign in to open that page.');

  /* show / hide password */
  toggle.addEventListener('click', function () {
    var shown = password.type === 'text';
    password.type = shown ? 'password' : 'text';
    toggle.setAttribute('aria-label', shown ? 'Show password' : 'Hide password');
    password.focus();
  });

  function nextPage() {
    var next = SC.param('next');
    // only allow local pages, never an outside URL
    return (next && /^[a-z0-9_\-]+\.html$/i.test(next)) ? next : 'dashboard.html';
  }

  function setBad(field, bad, message) {
    field.classList.toggle('field--bad', bad);
    if (bad && message) field.querySelector('.err').textContent = message;
  }

  [username, password].forEach(function (el) {
    el.addEventListener('input', function () {
      setBad(el === username ? userField : passField, false);
    });
  });

  form.addEventListener('submit', function (e) {
    e.preventDefault();

    var u = username.value.trim();
    var p = password.value;
    var ok = true;

    if (!u) { setBad(userField, true, 'Please enter your username.'); ok = false; }
    if (!p) { setBad(passField, true, 'Please enter your password.'); ok = false; }
    if (!ok) return;

    button.disabled = true;
    button.textContent = 'Signing in...';

    SC.api('/api/auth/login', { method: 'POST', body: { username: u, password: p } })
      .then(function (user) {
        SC.session.set(user);
        try {
          if (remember.checked) localStorage.setItem(REMEMBER_KEY, u);
          else localStorage.removeItem(REMEMBER_KEY);
        } catch (err) {}
        location.href = nextPage();
      })
      .catch(function (err) {
        button.disabled = false;
        button.textContent = 'Sign in';
        password.value = '';
        setBad(passField, true, err.message || 'Username or password is incorrect.');
        password.focus();
        SC.toast(err.message || 'Sign in failed.', 'bad');
      });
  });

}());

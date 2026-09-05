/* =====================================================================
  Sunrise Dental Clinic - public site behaviour
   Shared by index.html and book.html: sticky header, mobile navigation,
   reveal-on-scroll, and the appointment lookup where the page has one.
   Plain ES6, no libraries. Talks to the Java HttpServer REST API.
   ===================================================================== */
(function () {
  'use strict';

  /* Requests go through SC.api() in js/app.js, which knows where the
     Java server lives and falls back to sample data when it is down. */

  /* ---------- sticky header shadow ---------- */
  var header = document.getElementById('siteHeader');

  function onScroll() {
    header.classList.toggle('is-stuck', window.scrollY > 8);
  }
  window.addEventListener('scroll', onScroll, { passive: true });
  onScroll();

  /* ---------- mobile navigation ---------- */
  var toggle = document.getElementById('navToggle');
  var nav = document.getElementById('primaryNav');

  function setNav(open) {
    nav.classList.toggle('is-open', open);
    toggle.setAttribute('aria-expanded', String(open));
    toggle.setAttribute('aria-label', open ? 'Close menu' : 'Open menu');
  }

  toggle.addEventListener('click', function () {
    setNav(toggle.getAttribute('aria-expanded') !== 'true');
  });

  // close after tapping a link, and on Escape
  nav.addEventListener('click', function (e) {
    if (e.target.closest('a')) setNav(false);
  });
  document.addEventListener('keydown', function (e) {
    if (e.key === 'Escape') setNav(false);
  });
  window.addEventListener('resize', function () {
    if (window.innerWidth > 820) setNav(false);
  });

  /* ---------- reveal blocks as they scroll in ---------- */
  var blocks = document.querySelectorAll('.reveal');
  var reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

  if (reduced || !('IntersectionObserver' in window)) {
    blocks.forEach(function (el) { el.classList.add('is-visible'); });
  } else {
    var io = new IntersectionObserver(function (entries) {
      entries.forEach(function (entry, i) {
        if (!entry.isIntersecting) return;
        // small stagger so a row of cards does not pop in all at once
        setTimeout(function () {
          entry.target.classList.add('is-visible');
        }, i * 70);
        io.unobserve(entry.target);
      });
    }, { threshold: 0.12, rootMargin: '0px 0px -40px 0px' });

    blocks.forEach(function (el) { io.observe(el); });
  }

  /* ---------- appointment lookup (home page only) ---------- */
  var form = document.getElementById('lookupForm');
  var input = document.getElementById('lookupNo');
  var out = document.getElementById('lookupResult');

  function say(message, state) {
    out.textContent = message;
    out.className = 'lookup__result' + (state ? ' is-' + state : '');
  }

  if (form) form.addEventListener('submit', function (e) {
    e.preventDefault();

    var no = input.value.trim().toUpperCase();
    if (!no) {
      say('Please type your appointment number, for example APT-1042.', 'error');
      input.focus();
      return;
    }

    say('Checking...');

    SC.api('/api/appointments/' + encodeURIComponent(no))
      .then(function (data) {
        if (!data) return;

        var parts = [];
        if (data.patientName) parts.push(data.patientName);
        if (data.dentistName) parts.push('with ' + data.dentistName);
        if (data.treatmentType) parts.push('for ' + data.treatmentType);
        if (data.date) parts.push('on ' + SC.fmtDate(data.date));
        if (data.time) parts.push('at ' + data.time);

        var status = data.status ? ' (' + data.status + ')' : '';
        say(parts.join(' ') + status + '.', 'ok');
      })
      .catch(function (err) {
        if (err.status === 404) {
          say('No appointment found under ' + no + '. Please check the number, or call the clinic on 011 234 5678.', 'error');
        } else {
          say(err.message || 'The appointment could not be checked just now.', 'error');
        }
      });
  });

  /* ---------- footer year ---------- */
  var year = document.getElementById('year');
  if (year) year.textContent = new Date().getFullYear();

}());

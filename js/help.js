/* =====================================================================
   Help page: highlights the section you are reading in the contents list.
   The accordion itself is wired up in app.js.
   No login is required to read this page.
   ===================================================================== */
(function () {
  'use strict';

  var links = Array.prototype.slice.call(document.querySelectorAll('.guide__toc a'));
  var sections = links
    .map(function (a) { return document.querySelector(a.getAttribute('href')); })
    .filter(Boolean);

  if (!sections.length || !('IntersectionObserver' in window)) return;

  function highlight(id) {
    links.forEach(function (a) {
      var on = a.getAttribute('href') === '#' + id;
      a.style.color = on ? 'var(--brand-dark)' : '';
      a.style.borderLeftColor = on ? 'var(--brand)' : '';
      a.style.background = on ? '#fff' : '';
    });
  }

  var io = new IntersectionObserver(function (entries) {
    entries.forEach(function (entry) {
      if (entry.isIntersecting) highlight(entry.target.id);
    });
  }, { rootMargin: '-90px 0px -65% 0px' });

  sections.forEach(function (s) { io.observe(s); });

  // opening the page on a link such as help.html#login expands nothing,
  // so make sure the target section is scrolled into view under the app bar
  if (location.hash) {
    var target = document.querySelector(location.hash);
    if (target) setTimeout(function () {
      target.scrollIntoView({ block: 'start' });
      window.scrollBy(0, -90);
    }, 60);
  }

}());

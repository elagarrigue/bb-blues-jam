(() => {
  'use strict';
  const slides = [...document.querySelectorAll('.slide')];
  const dots = document.querySelector('.slide-dots');
  const overview = document.querySelector('#overview');
  const grid = document.querySelector('#overview-grid');
  const previous = document.querySelector('#previous');
  const next = document.querySelector('#next');
  const notes = document.querySelector('#notes-button');
  const fullscreen = document.querySelector('#fullscreen-button');
  let current = -1;
  let toastTimeout;
  const pad = number => String(number).padStart(2, '0');
  const hashIndex = () => /^#\d+$/.test(location.hash) ? Math.min(slides.length, Number(location.hash.slice(1))) - 1 : 0;
  document.querySelector('#slide-total').textContent = pad(slides.length);
  const reducedMotion = matchMedia('(prefers-reduced-motion: reduce)');
  const revealSelector = [
    '.eyebrow', 'h1', 'h2', '.heading-row>p', '.lead', '.cover-meta', '.cover-art', '.cover-bottom', '.takeaway',
    '.problem-lines>div', '.phone-figure', '.discovery-track>article', '.bottom-statement', '.harness-center', '.harness-piece',
    '.shared-line', '.workflow>*', '.workflow-return', '.role-bottom', '.lesson-line', '.terminal', '.test-metrics>div',
    '.proof-loop>*', '.verification-copy>p', '.error-table thead', '.error-table tbody tr', '.assistant-flow>*', '.assistant-fact',
    '.proof-panel', '.review-summary>*', '.finding-grid>div', '.closing-lessons>p', '.result-graphic', '.closing-stats>div',
    '.closing-signoff>p', '.closing-signoff>.next-up'
  ].join(',');
  const countSelector = '.big-number, .test-metrics strong, .role-bottom strong, .review-summary strong, .finding-grid strong, .closing-stats strong, .donut-label strong';
  const counters = new Map();
  let countFrame;
  slides.forEach(slide => {
    slide.querySelectorAll('.slide-content').forEach(content => content.querySelectorAll(revealSelector).forEach((element, index) => {
      element.classList.add('reveal');
      element.style.setProperty('--i', Math.min(index, 16));
    }));
    slide.querySelectorAll(countSelector).forEach(element => {
      const node = [...element.childNodes].find(child => child.nodeType === Node.TEXT_NODE && /\d/.test(child.textContent));
      if (node) counters.set(node, { slide, element, text: node.textContent });
    });
  });

  slides.forEach((slide, index) => {
    const button = document.createElement('button');
    button.type = 'button';
    button.setAttribute('aria-label', `${index + 1}. ${slide.dataset.title}`);
    button.addEventListener('click', () => go(index));
    dots.append(button);
    const card = document.createElement('button');
    card.type = 'button';
    card.style.setProperty('--i', index);
    const number = document.createElement('span');
    number.textContent = pad(index + 1);
    const title = document.createElement('b');
    title.textContent = slide.dataset.title;
    card.append(number, title);
    card.addEventListener('click', () => { overview.close(); go(index, true); });
    grid.append(card);
  });

  function go(index, focus = false) {
    index = Math.max(0, Math.min(slides.length - 1, index));
    if (index === current) return;
    const old = slides[current];
    document.body.dataset.direction = index < current ? 'back' : 'forward';
    document.body.style.setProperty('--glow-x', `${index % 2 ? 35 : -25}vmax`);
    document.body.style.setProperty('--glow-y', `${-40 + index % 3 * 14}vmax`);
    const wasInOld = old?.contains(document.activeElement);
    slides.forEach((slide, position) => {
      const active = position === index;
      slide.hidden = !active;
      slide.inert = !active;
      slide.classList.toggle('active', active);
      dots.children[position].setAttribute('aria-current', String(active));
      grid.children[position].setAttribute('aria-current', String(active));
    });
    current = index;
    slides[index].scrollTop = 0;
    history.replaceState(null, '', `#${index + 1}`);
    const slideNumber = document.querySelector('#slide-number');
    slideNumber.textContent = pad(index + 1);
    slideNumber.classList.remove('flip');
    void slideNumber.offsetWidth;
    slideNumber.classList.add('flip');
    countUp(slides[index]);
    document.querySelector('#slide-label').textContent = slides[index].querySelector('.eyebrow').textContent.split('/').slice(1).join('/').trim();
    document.querySelector('#progress-fill').style.width = `${(index + 1) / slides.length * 100}%`;
    previous.disabled = index === 0;
    next.disabled = index === slides.length - 1;
    document.querySelector('#announcement').textContent = `Diapositiva ${index + 1} de ${slides.length}. ${slides[index].dataset.title}`;
    document.title = `${index + 1}/${slides.length} · ${slides[index].dataset.title} · BB Blues Jam`;
    if (focus || wasInOld) document.querySelector('#deck').focus({ preventScroll: true });
  }

  const formatNumber = (value, grouped) => grouped ? String(value).replace(/\B(?=(\d{3})+(?!\d))/g, '.') : String(value);
  const numberPattern = /\d{1,3}(?:\.\d{3})+|\d+/g;
  const parseNumber = value => Number(value.replace(/\./g, ''));

  // Arrow figures ("122 → 14") count from the left value to the right one; others count up from zero.
  function interpolate(text, progress) {
    if (progress >= 1) return text;
    const arrow = text.indexOf('→');
    if (arrow >= 0) {
      const from = parseNumber(text.slice(0, arrow).match(numberPattern)[0]);
      return text.slice(0, arrow + 1) + text.slice(arrow + 1).replace(numberPattern, match => formatNumber(Math.round(from + (parseNumber(match) - from) * progress), match.includes('.')));
    }
    return text.replace(numberPattern, match => formatNumber(Math.round(parseNumber(match) * progress), match.includes('.')));
  }

  function resetCounters() {
    cancelAnimationFrame(countFrame);
    counters.forEach(({ element, text }, node) => { node.textContent = text; element.classList.remove('counting'); });
  }

  function countUp(slide) {
    resetCounters();
    if (reducedMotion.matches) return;
    const start = performance.now();
    const active = [...counters].filter(([, counter]) => counter.slide === slide).map(([node, counter]) => {
      const order = Number(counter.element.closest('.reveal')?.style.getPropertyValue('--i')) || 0;
      return { node, ...counter, delay: order * 75 + 320 };
    });
    if (!active.length) return;
    const tick = now => {
      let running = false;
      active.forEach(({ node, element, text, delay }) => {
        const progress = Math.min(1, Math.max(0, (now - start - delay) / 1300));
        node.textContent = interpolate(text, 1 - Math.pow(1 - progress, 3));
        element.classList.toggle('counting', progress < 1);
        running ||= progress < 1;
      });
      if (running) countFrame = requestAnimationFrame(tick);
    };
    countFrame = requestAnimationFrame(tick);
  }

  function toggleNotes() {
    const visible = document.body.classList.toggle('notes-visible');
    notes.setAttribute('aria-expanded', String(visible));
    notes.setAttribute('aria-label', `${visible ? 'Ocultar' : 'Mostrar'} notas para presentar (N)`);
    if (visible) slides[current].querySelector('.speaker-notes').scrollIntoView({ block: 'start', behavior: 'auto' });
    else slides[current].scrollTop = 0;
  }

  async function toggleFullscreen() {
    if (!document.fullscreenEnabled) return showToast('Usá la opción de pantalla completa de tu navegador.');
    try {
      if (document.fullscreenElement) await document.exitFullscreen();
      else await document.documentElement.requestFullscreen();
    } catch { showToast('Usá la opción de pantalla completa de tu navegador.'); }
  }

  function showToast(message) {
    const toast = document.querySelector('#toast');
    toast.textContent = message;
    toast.hidden = false;
    clearTimeout(toastTimeout);
    toastTimeout = setTimeout(() => { toast.hidden = true; }, 4500);
  }

  function toggleOverview() {
    if (overview.open) overview.close();
    else { overview.showModal(); grid.children[current].focus(); }
  }

  previous.addEventListener('click', () => go(current - 1));
  next.addEventListener('click', () => go(current + 1));
  notes.addEventListener('click', toggleNotes);
  fullscreen.addEventListener('click', toggleFullscreen);
  document.querySelector('#overview-button').addEventListener('click', toggleOverview);
  document.querySelector('#close-overview').addEventListener('click', () => overview.close());
  document.querySelector('#restart').addEventListener('click', () => go(0, true));
  overview.addEventListener('click', event => { if (event.target === overview) { const box = overview.getBoundingClientRect(); if (event.clientX < box.left || event.clientX > box.right || event.clientY < box.top || event.clientY > box.bottom) overview.close(); } });
  window.addEventListener('beforeprint', resetCounters);
  window.addEventListener('hashchange', () => go(hashIndex()));
  document.addEventListener('fullscreenchange', () => {
    const active = !!document.fullscreenElement;
    fullscreen.setAttribute('aria-label', active ? 'Salir de pantalla completa (F)' : 'Pantalla completa (F)');
    fullscreen.title = active ? 'Salir de pantalla completa (F)' : 'Pantalla completa (F)';
  });
  document.addEventListener('keydown', event => {
    if (event.ctrlKey || event.altKey || event.metaKey || event.target.matches('input, textarea, select, [contenteditable=true]')) return;
    if (overview.open) {
      if (event.key.toLowerCase() === 'g') { event.preventDefault(); overview.close(); }
      return;
    }
    if (event.key === ' ' && event.target.closest('button, a')) return;
    switch (event.key.toLowerCase()) {
      case 'arrowright': case 'pagedown': case ' ': event.preventDefault(); go(current + 1); break;
      case 'arrowleft': case 'pageup': event.preventDefault(); go(current - 1); break;
      case 'home': event.preventDefault(); go(0); break;
      case 'end': event.preventDefault(); go(slides.length - 1); break;
      case 'f': event.preventDefault(); toggleFullscreen(); break;
      case 'n': event.preventDefault(); toggleNotes(); break;
      case 'g': event.preventDefault(); toggleOverview(); break;
      default: if (/^[1-9]$/.test(event.key) && Number(event.key) <= slides.length) { event.preventDefault(); go(Number(event.key) - 1); }
    }
  });
  let touchStart;
  document.querySelector('#deck').addEventListener('touchstart', event => {
    if (event.touches.length !== 1 || event.target.closest('button,a')) { touchStart = null; return; }
    touchStart = { x: event.touches[0].clientX, y: event.touches[0].clientY };
  }, { passive: true });
  document.querySelector('#deck').addEventListener('touchend', event => {
    if (!touchStart) return;
    const dx = event.changedTouches[0].clientX - touchStart.x;
    const dy = event.changedTouches[0].clientY - touchStart.y;
    if (Math.abs(dx) > 65 && Math.abs(dx) > Math.abs(dy) * 1.5) go(current + (dx < 0 ? 1 : -1));
    touchStart = null;
  }, { passive: true });
  go(hashIndex());
})();

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

  slides.forEach((slide, index) => {
    const button = document.createElement('button');
    button.type = 'button';
    button.setAttribute('aria-label', `${index + 1}. ${slide.dataset.title}`);
    button.addEventListener('click', () => go(index));
    dots.append(button);
    const card = document.createElement('button');
    card.type = 'button';
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
    document.querySelector('#slide-number').textContent = pad(index + 1);
    document.querySelector('#slide-label').textContent = slides[index].querySelector('.eyebrow').textContent.split('/').slice(1).join('/').trim();
    document.querySelector('#progress-fill').style.width = `${(index + 1) / slides.length * 100}%`;
    previous.disabled = index === 0;
    next.disabled = index === slides.length - 1;
    document.querySelector('#announcement').textContent = `Diapositiva ${index + 1} de ${slides.length}. ${slides[index].dataset.title}`;
    document.title = `${index + 1}/${slides.length} · ${slides[index].dataset.title} · BB Blues Jam`;
    if (focus || wasInOld) document.querySelector('#deck').focus({ preventScroll: true });
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

// Browser QA using an installed Chromium browser and Node's built-in CDP transport.
// Usage on Windows: node verify.mjs
import { spawn } from 'node:child_process';
import { mkdir, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { pathToFileURL } from 'node:url';

const root = path.dirname(new URL(import.meta.url).pathname.replace(/^\/(\w:)/, '$1'));
const output = path.join(root, '.qa');
await mkdir(output, { recursive: true });
const browser = process.env.PRESENTATION_BROWSER || 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe';
const child = spawn(browser, ['--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check', '--remote-debugging-port=9338', `--user-data-dir=${path.join(output, 'browser')}`, 'about:blank'], { windowsHide: true, stdio: 'ignore' });
let ws;
let id = 0;
const pending = new Map();
const exceptions = [];
const pause = ms => new Promise(resolve => setTimeout(resolve, ms));
let checks = 0;
const SLIDES = 11;
function assert(value, message) { if (!value) throw new Error(message); checks++; }
function send(method, params = {}) {
  return new Promise((resolve, reject) => {
    const number = ++id;
    const timer = setTimeout(() => { pending.delete(number); reject(new Error(`CDP timeout: ${method}`)); }, 15000);
    pending.set(number, { resolve, reject, timer });
    ws.send(JSON.stringify({ id: number, method, params }));
  });
}
async function evaluate(expression) {
  const result = await send('Runtime.evaluate', { expression, returnByValue: true, awaitPromise: true });
  if (result.exceptionDetails) throw new Error(JSON.stringify(result.exceptionDetails));
  return result.result.value;
}
async function key(key, code, keyCode) {
  await send('Input.dispatchKeyEvent', { type: 'keyDown', key, code, windowsVirtualKeyCode: keyCode });
  await send('Input.dispatchKeyEvent', { type: 'keyUp', key, code, windowsVirtualKeyCode: keyCode });
}
async function screenshot(name) {
  await pause(400);
  const { data } = await send('Page.captureScreenshot', { format: 'png' });
  await writeFile(path.join(output, name), Buffer.from(data, 'base64'));
}

try {
  let targets;
  for (let attempt = 0; attempt < 40; attempt++) {
    try { targets = await (await fetch('http://127.0.0.1:9338/json')).json(); if (targets.some(t => t.type === 'page')) break; } catch {}
    await pause(250);
  }
  const target = targets?.find(t => t.type === 'page');
  if (!target) throw new Error('Headless browser did not start.');
  ws = new WebSocket(target.webSocketDebuggerUrl);
  await new Promise((resolve, reject) => { ws.addEventListener('open', resolve, { once: true }); ws.addEventListener('error', reject, { once: true }); });
  ws.addEventListener('message', ({ data }) => {
    const message = JSON.parse(data);
    if (message.method === 'Runtime.exceptionThrown') exceptions.push(message.params);
    if (!pending.has(message.id)) return;
    const request = pending.get(message.id);
    clearTimeout(request.timer);
    pending.delete(message.id);
    if (message.error) request.reject(new Error(message.error.message));
    else request.resolve(message.result);
  });
  await send('Page.enable');
  await send('Runtime.enable');
  await send('Emulation.setDeviceMetricsOverride', { width: 1440, height: 900, deviceScaleFactor: 1, mobile: false });
  await send('Page.navigate', { url: pathToFileURL(path.join(root, 'dist/index.html')).href });
  await pause(600);
  await evaluate('document.fonts.ready.then(() => true)');
  assert(await evaluate(`document.querySelectorAll(".slide").length === ${SLIDES}`), `Exactly ${SLIDES} slides`);
  assert(await evaluate('[...document.querySelectorAll(".speaker-notes")].every(n => n.querySelector("h3") && n.textContent.length > 200)'), 'Every slide has speaker notes');
  assert(await evaluate('!/\bD-\d\d\b/.test([...document.querySelectorAll(".slide-content")].map(c => c.textContent).join(" "))'), 'No internal decision codes on slides');
  assert(await evaluate('document.fonts.check("700 30px Barlow") && document.fonts.check("16px Chivo")'), 'Local fonts loaded');
  assert(await evaluate('[...document.images].every(image => image.complete && image.naturalWidth > 0)'), 'All images loaded');
  assert(await evaluate('document.querySelector("#slide-6").textContent.includes("1.156") && document.querySelector("#slide-6").textContent.includes("22")'), 'Current verification snapshot');
  assert(await evaluate('document.querySelector(".donut").getAttribute("aria-label").includes("75 rebanadas: 74 aceptadas") && document.querySelectorAll(".result-legend span").length === 2'), 'Current feature status chart');
  assert(await evaluate('document.querySelector("#slide-9 .proof-zero") && document.querySelector(".architecture-link").getAttribute("href") === "assets/app-architecture.svg"'), 'Phase 2 proof and architecture link');
  assert(await evaluate('document.querySelector("#slide-10").textContent.includes("122 → 14") && document.querySelectorAll(".error-table tbody tr").length === 5'), 'Final review and error table');

  const geometries = [];
  for (const [width, height] of [[1440,900], [1366,768], [1920,1080], [390,844]]) {
    await send('Emulation.setDeviceMetricsOverride', { width, height, deviceScaleFactor: 1, mobile: width < 600 });
    for (let slide = 1; slide <= SLIDES; slide++) {
      await evaluate(`location.hash = '#${slide}'`);
      await pause(80);
      const geometry = await evaluate(`(() => { const slide = document.querySelector('.slide.active'); return { active: document.querySelectorAll('.slide:not([hidden])').length, width: slide.clientWidth, scrollWidth: slide.scrollWidth, height: slide.clientHeight, scrollHeight: slide.scrollHeight, horizontal: document.documentElement.scrollWidth > innerWidth, notesHidden: getComputedStyle(slide.querySelector('.speaker-notes')).display === 'none' }; })()`);
      geometries.push({ viewport: `${width}x${height}`, slide, ...geometry });
      assert(geometry.active === 1 && geometry.notesHidden, 'Only active slide and no notes');
      assert(!geometry.horizontal && geometry.scrollWidth <= geometry.width + 1, `Horizontal overflow ${width}, slide ${slide}`);
    }
  }
  const overflow = geometries.filter(g => Number(g.viewport.split('x')[0]) >= 1000 && g.scrollHeight > g.height + 2);
  console.log(JSON.stringify({ desktopOverflow: overflow }));
  await send('Emulation.setDeviceMetricsOverride', { width: 1440, height: 900, deviceScaleFactor: 1, mobile: false });
  await key('Home', 'Home', 36);
  assert(await evaluate('location.hash === "#1" && document.querySelector("#previous").disabled'), 'Home and previous boundary');
  await key('ArrowRight', 'ArrowRight', 39);
  assert(await evaluate('location.hash === "#2"'), 'Arrow navigation');
  await key('g', 'KeyG', 71);
  assert(await evaluate('document.querySelector("#overview").open'), 'Overview shortcut');
  await evaluate('document.querySelector("#overview-grid").children[3].click()');
  assert(await evaluate('location.hash === "#4" && !document.querySelector("#overview").open'), 'Overview jump');
  await key('n', 'KeyN', 78);
  assert(await evaluate('document.body.classList.contains("notes-visible") && document.querySelector("#notes-button").getAttribute("aria-expanded") === "true"'), 'Speaker notes');
  await key('n', 'KeyN', 78);
  await key('End', 'End', 35);
  assert(await evaluate(`location.hash === "#${SLIDES}" && document.querySelector("#next").disabled`), 'End and next boundary');
  await screenshot(`slide-${SLIDES}-desktop.png`);
  await evaluate('document.querySelector("#restart").click()');
  assert(await evaluate('location.hash === "#1"'), 'Restart button');
  await screenshot('slide-1-desktop.png');
  for (const [width, height] of [[1440,900], [1366,768]]) {
    await send('Emulation.setDeviceMetricsOverride', { width, height, deviceScaleFactor: 1, mobile: false });
    for (let slide = 1; slide <= SLIDES; slide++) {
      await evaluate(`location.hash = '#${slide}'`);
      await screenshot(`slide-${slide}-${width}x${height}.png`);
    }
  }
  await send('Emulation.setDeviceMetricsOverride', { width: 390, height: 844, deviceScaleFactor: 1, mobile: true });
  await evaluate('location.hash = "#2"');
  await screenshot('slide-2-mobile.png');
  await send('Emulation.setDeviceMetricsOverride', { width: 1440, height: 900, deviceScaleFactor: 1, mobile: false });
  const pdf = await send('Page.printToPDF', { printBackground: true, preferCSSPageSize: true });
  const bytes = Buffer.from(pdf.data, 'base64');
  await writeFile(path.join(output, 'presentation.pdf'), bytes);
  const pages = (bytes.toString('latin1').match(/\/Type\s*\/Page\b/g) || []).length;
  assert(pages === SLIDES, `Print output has ${pages} pages, expected ${SLIDES}`);
  assert(exceptions.length === 0, 'No runtime exceptions');
  assert(overflow.length === 0, 'No desktop vertical overflow');
  const report = { result: 'passed', checks, slides: SLIDES, printPages: pages, geometries, runtimeExceptions: exceptions };
  await writeFile(path.join(output, 'verification.json'), JSON.stringify(report, null, 2));
  console.log(JSON.stringify({ result: report.result, checks, slides: SLIDES, printPages: pages, artifacts: '.qa/' }));
} finally {
  if (ws?.readyState === WebSocket.OPEN) {
    try { await send('Browser.close'); } catch {}
    ws.close();
  }
  child.kill();
}

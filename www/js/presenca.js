/* Contador anônimo de uso. Informa ao painel (admin.html) apenas que "um aparelho está ativo",
   para saber quantas pessoas usam o NTPlay. O identificador TROCA a cada dia e NÃO é enviado nome,
   lista, o que está sendo assistido, usuário, senha nem endereço de servidor — nada que identifique
   a pessoa. Vai criptografado só para o painel. */
const PRES = { pub: 'BHI9Apeb-q1NfFyzK1M50pJL7IWEGH0pJbwT0FXFtTD6B4986Gp-qjGP0IeKLIEMFH_UfcvuDGTL3GqAs3k8g4A', topic: 'ntplay-pres-381affd473dafb5cac', t: null };
const pB64 = (a) => btoa(String.fromCharCode(...new Uint8Array(a))).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
const pUnb64 = (s) => { s = s.replace(/-/g, '+').replace(/_/g, '/'); while (s.length % 4) s += '='; return Uint8Array.from(atob(s), (c) => c.charCodeAt(0)); };
// id que muda a cada dia: uma semente aleatória local (nunca enviada) + a data, com hash. Não dá para ligar um dia ao outro nem a um aparelho.
async function presId() {
  let salt = LS.ntSalt; if (!salt) { salt = [...crypto.getRandomValues(new Uint8Array(16))].map((x) => x.toString(16).padStart(2, '0')).join(''); LS.ntSalt = salt; }
  const day = new Date().toISOString().slice(0, 10);
  const h = new Uint8Array(await crypto.subtle.digest('SHA-256', new TextEncoder().encode(salt + '|' + day)));
  return 'a' + [...h.slice(0, 6)].map((x) => x.toString(16).padStart(2, '0')).join('');
}
function presPlat() {
  if (window.bobNative) return 'pc';
  try { if (window.Capacitor && Capacitor.getPlatform) { const p = Capacitor.getPlatform(); if (p != 'web') return document.documentElement.classList.contains('tv') ? 'androidtv' : p; } } catch (e) {}
  return /Android|iPhone|iPad/i.test(navigator.userAgent) ? 'web-celular' : 'web';
}
async function presSeal(obj) {
  const C = crypto.subtle, pub = await C.importKey('raw', pUnb64(PRES.pub), { name: 'ECDH', namedCurve: 'P-256' }, false, []);
  const eph = await C.generateKey({ name: 'ECDH', namedCurve: 'P-256' }, true, ['deriveBits']);
  const sh = await C.deriveBits({ name: 'ECDH', public: pub }, eph.privateKey, 256);
  const key = await C.importKey('raw', await C.digest('SHA-256', sh), 'AES-GCM', false, ['encrypt']);
  const iv = crypto.getRandomValues(new Uint8Array(12));
  const ct = await C.encrypt({ name: 'AES-GCM', iv }, key, new TextEncoder().encode(JSON.stringify(obj)));
  return 'ntp1.' + pB64(await C.exportKey('raw', eph.publicKey)) + '.' + pB64(iv) + '.' + pB64(ct);
}
async function presSend() {
  if (LS.ntPres == '0' || !window.crypto || !crypto.subtle) return;
  try {
    const msg = await presSeal({ id: await presId(), p: presPlat(), v: window.NT_VER || '', t: Date.now() });
    await fetch('https://ntfy.sh/' + PRES.topic, { method: 'POST', body: msg, headers: { 'X-Firebase': 'no' } });
  } catch (e) {}
}
function presStart() { clearInterval(PRES.t); if (LS.ntPres == '0') return; setTimeout(() => presSend(), 6000); PRES.t = setInterval(() => presSend(), 5 * 60000); }
if (window.bobNative && bobNative.version) bobNative.version().then((x) => { window.NT_VER = x; }).catch(() => {});
presStart();

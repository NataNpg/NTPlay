/* ⚽ Futebol: jogos do dia (placar da ESPN, sem chave) + canais da sua lista que passam os jogos.
   Carregado antes do app.js; só usa as funções do app dentro das funções abaixo. */
const FUT = { off: 0, data: {}, busy: {}, timer: null };
const FUT_L = [
  ['bra.1', 'Brasileirão Série A'], ['bra.2', 'Brasileirão Série B'], ['bra.3', 'Brasileirão Série C'], ['bra.copa_do_brazil', 'Copa do Brasil'],
  ['conmebol.libertadores', 'Libertadores'], ['conmebol.sudamericana', 'Sul-Americana'], ['conmebol.recopa', 'Recopa'],
  ['bra.camp.paulista', 'Paulistão'], ['bra.camp.carioca', 'Carioca'], ['bra.camp.mineiro', 'Mineiro'], ['bra.camp.gaucho', 'Gauchão'],
  ['fifa.world', 'Copa do Mundo'], ['fifa.worldq.conmebol', 'Eliminatórias'], ['fifa.cwc', 'Mundial de Clubes'], ['conmebol.america', 'Copa América'], ['fifa.friendly', 'Amistosos'],
  ['uefa.champions', 'Champions League'], ['uefa.europa', 'Europa League'], ['uefa.europa.conf', 'Conference League'], ['uefa.nations', 'Liga das Nações'],
  ['eng.1', 'Premier League'], ['esp.1', 'La Liga'], ['ita.1', 'Serie A (Itália)'], ['ger.1', 'Bundesliga'], ['fra.1', 'Ligue 1'], ['por.1', 'Liga Portugal'],
  ['ned.1', 'Eredivisie'], ['eng.fa', 'Copa da Inglaterra'], ['esp.copa_del_rey', 'Copa do Rei'], ['arg.1', 'Argentina'], ['ksa.1', 'Liga Saudita'], ['usa.1', 'MLS'], ['mex.1', 'Liga MX']
];
// canais de esporte na lista (pelo nome do canal ou do grupo)
const FUT_SP = /esporte|sport|futebol|futbol|soccer|premiere|\bespn|tnt sports|band ?sports|sportv|combate|caz[eé]|dazn|paramount|\bgoat\b|\bppv\b|jogos|campeonato|brasileir|libertadores|champions|copa do|nosso futebol|onefootball|conmebol|\beleven|bein|sky sport|tyc|fox sports|xsports|nsports|\bge\b.?tv/i;
const FUT_EV = /jogos|eventos?|ppv|futebol|campeonato|brasileir|premiere|copa|ao vivo agora/i;
const futDay = (off) => { const d = new Date(Date.now() + off * 864e5); return d.getFullYear() + String(d.getMonth() + 1).padStart(2, '0') + String(d.getDate()).padStart(2, '0'); };
const futSportCh = () => S.live.filter((e) => FUT_SP.test(e.title + ' ' + e.group));
function futTeam(c) {
  const t = c.team || {};
  return { n: t.shortDisplayName || t.displayName || t.name || '?', full: t.displayName || t.name || '', logo: t.logo || (t.logos && t.logos[0] && t.logos[0].href) || '', sc: c.score != null ? String(c.score) : '', win: !!c.winner, home: c.homeAway == 'home' };
}
async function futLoad(off, force) {
  const d = futDay(off), old = FUT.data[d];
  const live = old && old.games.some((g) => g.st == 'in');
  if (!force && old && Date.now() - old.ts < (live ? 60000 : 10 * 60000)) return old;
  if (FUT.busy[d]) return FUT.busy[d];
  FUT.busy[d] = (async () => {
    const res = await Promise.all(FUT_L.map(([id, nm], i) =>
      fetch('https://site.api.espn.com/apis/site/v2/sports/soccer/' + id + '/scoreboard?dates=' + d + '&lang=pt&region=br')
        .then((r) => (r.ok ? r.json() : null)).catch(() => null)
        .then((j) => ((j && j.events) || []).map((ev) => {
          const cp = (ev.competitions || [])[0] || {}, st = (ev.status || cp.status || {}), ty = st.type || {};
          const cs = (cp.competitors || []).map(futTeam), h = cs.find((x) => x.home) || cs[0] || {}, a = cs.find((x) => !x.home) || cs[1] || {};
          const tv = [].concat(...(cp.broadcasts || []).map((b) => b.names || []), ...(cp.geoBroadcasts || []).map((b) => [b.media && b.media.shortName].filter(Boolean)));
          const lg = (j.leagues || [])[0] || {};
          return { id: ev.id, li: i, lg: nm, lgLogo: (lg.logos && lg.logos[0] && lg.logos[0].href) || '', t: new Date(ev.date), st: ty.state || 'pre', sn: ty.name || '', clock: st.displayClock || '', h, a, venue: (cp.venue && cp.venue.fullName) || '', city: (cp.venue && cp.venue.address && cp.venue.address.city) || '', tv: [...new Set(tv)] };
        }))));
    const seen = new Set(), games = [];
    res.flat().forEach((g) => { if (!seen.has(g.id)) { seen.add(g.id); games.push(g); } });
    games.sort((x, y) => x.li - y.li || x.t - y.t);
    const out = { ts: Date.now(), games, err: res.every((r) => !r.length) && !games.length ? 'sem jogos ou sem conexão' : '' };
    FUT.data[d] = out; delete FUT.busy[d];
    return out;
  })();
  return FUT.busy[d];
}
function futStatus(g) {
  if (g.st == 'in') return '<span class="fl1">● AO VIVO' + (g.clock && g.clock != "0'" ? ' ' + esc(g.clock) : '') + '</span>';
  if (/POSTPONED/.test(g.sn)) return 'Adiado';
  if (/CANCELED|CANCELLED/.test(g.sn)) return 'Cancelado';
  if (g.st == 'post') return /PEN|SHOOTOUT/.test(g.sn) ? 'Encerrado (pênaltis)' : 'Encerrado';
  return g.t.toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' });
}
// ---- programação (EPG) do servidor Xtream: o que cada canal de esporte está passando agora
FUT.epg = new Map(); FUT.epgList = new Map(); FUT.epgTs = 0; FUT.epgBusy = null; FUT.epgOk = null; FUT.all = false;
const futB64 = (x) => { try { return decodeURIComponent(escape(atob(String(x || '')))); } catch (e) { return String(x || ''); } };
const futBase = (t) => norm(t).replace(/\b(fhd|hd|sd|uhd|4k|h265|hevc|fullhd|alt|backup|[0-9]{3,4}p)\b/g, ' ').replace(/\s+/g, ' ').trim();
const futSid = (u) => { const m = String(u).split('?')[0].match(/\/(\d+)(?:\.\w+)?$/); return m ? m[1] : ''; };
function futEpgLoad(force) {
  if (FUT.epgBusy) return FUT.epgBusy;
  if (!force && FUT.epgTs && Date.now() - FUT.epgTs < 5 * 60000) return Promise.resolve();
  const sp = futSportCh(), groups = new Map();
  sp.forEach((e) => { const k = futBase(e.title); if (!groups.has(k)) groups.set(k, []); groups.get(k).push(e); });
  const jobs = [...groups.values()].slice(0, 160);
  let ok = 0, tried = 0;
  FUT.epgBusy = (async () => {
    let i = 0;
    const work = async () => {
      while (i < jobs.length) {
        const vs = jobs[i++], e = vs[0], c = credsOf(e.ch[0].url), sid = futSid(e.ch[0].url);
        if (!c || !sid) continue;
        tried++;
        try {
          const j = await (await fetch(api(c, 'get_short_epg', '&stream_id=' + sid + '&limit=8'))).json();
          const now = Date.now() / 1000, L = (j && j.epg_listings) || [];
          const cur = L.find((x) => +x.start_timestamp <= now && +x.stop_timestamp > now) || L.find((x) => x.now_playing == 1);
          if (L.length) ok++;
          const v = cur ? { t: futB64(cur.title).trim(), d: futB64(cur.description).trim(), end: +cur.stop_timestamp * 1000 } : null;
          const lst = L.map((x) => ({ t: futB64(x.title).trim(), d: futB64(x.description).trim(), s: +x.start_timestamp * 1000, e: +x.stop_timestamp * 1000 })).filter((x) => x.e > Date.now() - 36e5);
          vs.forEach((x) => { FUT.epg.set(x.key, v); FUT.epgList.set(x.key, lst); });
        } catch (er) {}
      }
    };
    await Promise.all([work(), work(), work(), work()]);
    FUT.epgOk = tried ? ok > 0 : false; FUT.epgTs = Date.now(); FUT.epgBusy = null;
  })();
  return FUT.epgBusy;
}
const FUT_GAME = /\s(x|vs\.?|versus)\s|futebol|campeonato|brasileir|libertadores|sul.?americana|champions|copa |liga |premier|la liga|serie a|bundesliga|ligue 1|eliminat|amistoso|\bjogo\b|rodada|final|semifinal|cl[aá]ssico/i;
const FUT_NOT = /reprise|compacto|melhores momentos|gols da rodada|resenha|debate|programa|mesa redonda|jornal|not[ií]cias|tal(k|e) show|pr[eé].?jogo|p[oó]s.?jogo|replay|vt\b/i;
// o canal está passando jogo agora? (programa atual com cara de jogo, sem ser reprise/debate)
function futOnAir(e) { const p = FUT.epg.get(e.key); if (!p || !p.t) return null; const t = p.t + ' ' + p.d; return FUT_GAME.test(t) && !FUT_NOT.test(p.t) ? p : null; }
// canais da lista para este jogo: programação com os dois times, canais de evento com os nomes, depois só um time
const futKey = (s) => norm(s).replace(/\b(fc|sc|ec|cf|ac|afc|club|clube|de|da|do|futebol|football|esporte|sport)\b/g, ' ').replace(/\s+/g, ' ').trim();
function futChannels(g, sp) {
  const ks = (t) => [...new Set([futKey(t.n), futKey(t.full)].filter((k) => k.length >= 3))];
  const H = ks(g.h), A = ks(g.a), has = (n, K) => K.some((k) => (' ' + n + ' ').includes(' ' + k + ' '));
  const now = Date.now(), kick = g.t.getTime();
  // casa o jogo com a PROGRAMAÇÃO do canal: agora = está passando; perto do horário = vai passar
  const epgMatch = (e) => { const L = FUT.epgList.get(e.key); if (!L) return undefined;
    for (const x of L) { const pn = futKey(x.t + ' ' + x.d); if (has(pn, H) && has(pn, A)) {
      if (x.s <= now && x.e > now) return 'live';
      if (x.e > now && x.s - now < 6 * 36e5 && Math.abs(x.s - kick) < 3 * 36e5) return x.s; } }
    return undefined; };
  const prog = [], both = [], one = [], seen = new Set();
  const push = (arr, e, w) => { const k = futBase(e.title); if (seen.has(k)) return; seen.add(k); arr.push({ e, when: w }); };
  for (const e of sp) {
    const m = epgMatch(e); if (m !== undefined) { push(prog, e, m); continue; }
    const n = futKey(e.title), h = has(n, H), a = has(n, A);
    if (h && a) push(both, e, futOnAir(e) ? 'live' : null);
    else if ((h || a) && FUT_EV.test(e.group + ' ' + e.title)) push(one, e, futOnAir(e) ? 'live' : null);
  }
  return [...prog, ...both, ...one].slice(0, 6);
}
const futChEls = (g, sp) => futChannels(g, sp).map((o) => o.e);
function futCard(g, sp) {
  const ch = g.st == 'post' ? [] : futChannels(g, sp);
  const tm = (t) => `<div class="ftm${t.win ? ' w' : ''}">${t.logo ? `<img loading="lazy" referrerpolicy="no-referrer" src="${esc(t.logo)}" onerror="this.remove()">` : '<i></i>'}<span>${esc(t.n)}</span><b>${g.st == 'pre' ? '' : esc(t.sc)}</b></div>`;
  return `<div class="fc${g.st == 'in' ? ' on' : ''}"><div class="fst">${futStatus(g)}</div>${tm(g.h)}${tm(g.a)}
    <div class="fw">${esc([g.venue, g.city].filter(Boolean).join(' • '))}</div>
    <div class="fch">${ch.length ? ch.map((o) => { const e = o.e, p = FUT.epg.get(e.key), lb = o.when == 'live' ? ' <b class="fl1">• ● AGORA</b>' : o.when ? ' <span class="fgt">• ' + new Date(o.when).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' }) + '</span>' : ''; return `<button class="chip fk" data-k="${esc(e.key)}" title="${esc(p && p.t ? p.t : e.group)}">▶ ${esc(e.title)}${lb}</button>`; }).join('') : g.st == 'post' ? '' : `<small>${g.st == 'in' ? 'Nenhum canal da sua lista está passando este jogo' : g.st == 'pre' ? 'O canal aparece quando a programação confirmar' : ''}</small>`}</div></div>`;
}
// cartão de canal com o programa atual
const futLive = (e, p) => `<div class="pc wc" data-k="${esc(e.key)}" data-u="${esc(e.ch[0].url)}" tabindex="0"><div class="im"><span class="ini">${ini(e)}</span>${imgTag(e.logo)}<div class="grad"></div><span class="tl">● AO VIVO</span><div class="ov"><i>${ic('play', 1)}</i></div></div><div class="nm"><div class="t">${esc(e.title)}</div><div class="g">${esc(p ? '⚽ ' + p.t : e.group)}</div></div>${star(e)}</div>`;
function futPage() {
  const d = futDay(FUT.off), D = FUT.data[d], sp = futSportCh();
  const days = [[-1, 'Ontem'], [0, 'Hoje'], [1, 'Amanhã'], [2, new Date(Date.now() + 2 * 864e5).toLocaleDateString('pt-BR', { weekday: 'short', day: '2-digit' })]];
  let h = `<div class="fut"><div class="fh"><h2 class="pgt">⚽ Jogos ${FUT.off == 0 ? 'de hoje' : FUT.off == -1 ? 'de ontem' : FUT.off == 1 ? 'de amanhã' : ''}<small>${((x) => x[0].toUpperCase() + x.slice(1))(new Date(Date.now() + FUT.off * 864e5).toLocaleDateString('pt-BR', { weekday: 'long', day: '2-digit', month: 'long' }))}</small></h2>
    <div class="fd">${days.map(([o, n]) => `<button class="ch2${FUT.off == o ? ' on' : ''}" data-fo="${o}">${esc(n)}</button>`).join('')}<button class="ch2" data-fr="1" title="Atualizar placares e programação">↻</button></div></div>`;
  const redraw = () => { if (S.view == 'browse' && S.tab == 'FUTEBOL' && !S.q.trim()) renderBrowse(); };
  if (!D) futLoad(FUT.off).then(redraw);
  if (FUT.off == 0 && !FUT.epgBusy && (!FUT.epgTs || Date.now() - FUT.epgTs > 5 * 60000)) futEpgLoad().then(redraw);
  // 1) canais passando jogo agora (programação do servidor + canais de evento com os times de jogos ao vivo)
  if (FUT.off == 0) {
    const liveG = D ? D.games.filter((g) => g.st == 'in') : [];
    const fromGames = new Set(liveG.flatMap((g) => futChEls(g, sp)));
    const seen = new Set(), on = [];
    for (const e of sp) { const p = futOnAir(e); if ((p || fromGames.has(e)) && !seen.has(futBase(e.title))) { seen.add(futBase(e.title)); on.push([e, p]); } }
    on.sort((x, y) => (y[1] ? 1 : 0) - (x[1] ? 1 : 0));
    const st = FUT.epgBusy && !FUT.epgTs ? 'lendo a programação dos canais…' : FUT.epgOk === false ? 'seu servidor não informa a programação (EPG): mostrando pelos nomes dos canais' : on.length + ' canais';
    h += `<section class="rs"><div class="sh"><h3><span class="fl1">●</span> Passando jogo agora</h3><span>${esc(st)}</span></div>` +
      (on.length ? `<div class="rw2"><button class="ar l" data-ar="-1">‹</button><div class="hs">${on.map(([e, p]) => futLive(e, p)).join('')}</div><button class="ar r" data-ar="1">›</button></div>` : `<div class="empty" style="padding:24px">${FUT.epgBusy ? 'Verificando os canais…' : 'Nenhum canal da sua lista está passando jogo neste momento.'}</div>`) + '</section>';
  }
  // 2) jogos do dia
  if (!D) h += '<div class="hint">Buscando os jogos na internet…</div>';
  else if (!D.games.length) h += `<div class="empty">Nenhum jogo encontrado ${FUT.off == 0 ? 'hoje' : 'neste dia'} nos campeonatos acompanhados${D.err ? ' (' + esc(D.err) + ')' : ''}.</div>`;
  else {
    const lv = D.games.filter((g) => g.st == 'in').length;
    h += `<div class="fsum">${D.games.length} jogos${lv ? ` • <b class="fl1">● ${lv} ao vivo agora</b>` : ''} • atualizado às ${new Date(D.ts).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' })} • fonte: ESPN</div>`;
    const by = new Map(), ord = (g) => (g.st == 'in' ? 0 : g.st == 'pre' ? 1 : 2);
    [...D.games].sort((x, y) => x.li - y.li || ord(x) - ord(y) || x.t - y.t).forEach((g) => { if (!by.has(g.lg)) by.set(g.lg, []); by.get(g.lg).push(g); });
    for (const [lg, a] of by) h += `<section class="flg"><div class="flh">${a[0].lgLogo ? `<img src="${esc(a[0].lgLogo)}" referrerpolicy="no-referrer" onerror="this.remove()">` : ''}<b>${esc(lg)}</b><span>${a.length} jogo${a.length > 1 ? 's' : ''}</span></div><div class="fgr">${a.map((g) => futCard(g, sp)).join('')}</div></section>`;
  }
  // 3) todos os canais de esporte só quando pedir
  h += `</div><div style="padding:6px 20px"><button class="more" data-fall="1">${FUT.all ? 'Esconder' : 'Mostrar'} todos os canais de esporte (${sp.length.toLocaleString('pt-BR')})</button></div>`;
  if (FUT.all) h += row('Todos os canais de esporte', sp.slice(0, 80).map((e) => futLive(e, futOnAir(e))), sp.length + ' canais');
  if (!sp.length && S.all.length) h += '<div class="empty">Não encontrei canais de esporte na sua lista (Premiere, SporTV, ESPN, TNT Sports…).</div>';
  clearInterval(FUT.timer);
  if (FUT.off == 0) FUT.timer = setInterval(() => { if (S.view != 'browse' || S.tab != 'FUTEBOL' || S.q) return clearInterval(FUT.timer); Promise.all([futLoad(0, true), futEpgLoad()]).then(redraw); }, 60000);
  return h;
}
document.addEventListener('click', (e) => {
  const b = e.target.closest('[data-fo],[data-fr],[data-fall]');
  if (!b) return;
  if (b.dataset.fall) { FUT.all = !FUT.all; renderBrowse(); return; }
  if (b.dataset.fo != null) FUT.off = +b.dataset.fo;
  const d = futDay(FUT.off);
  if (b.dataset.fr) { delete FUT.data[d]; FUT.epgTs = 0; }
  renderBrowse();
  if (b.dataset.fr) toast('Atualizando placares e programação…');
});

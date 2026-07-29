/* =============================================================================
 * popn-sync.js
 * 북마클릿이 e-amusement 페이지에 주입하는 본체.
 * https://cdn.raredonut.com/popn-sync.js 로 호스팅.
 * 로드가 끝나면 전역 post(apiBase, startPage, token, debug) 가 정의된다.
 * ========================================================================== */
(function () {
  'use strict';

  const DEFAULT_API_BASE = 'https://api.raredonut.com';
  const PLAYDATA_ORIGIN  = 'https://p.eagate.573.jp';
  const PLAYDATA_PATH    = '/game/popn/popn29/playdata/mu_top.html';

  // 프로필(팝클래스·플레이 횟수)이 있는 페이지. mu_top 에는 없다.
  // ★ 미검증 — 실제 경로를 확인할 것 ★
  const PROFILE_PATH = '/game/popn/popn29/playdata/index.html';

  // 버전 필터를 ALL(-1)로 고정한다. 기본값은 최신 버전만 보여주므로 반드시 명시할 것.
  const PAGE_PARAMS = {
    version: '-1',      // ALL
    bemani: '0',
    category: '0',
    keyword: '',
    sort: 'music',
    sort_type: 'up',    // 정렬 고정 → 페이지 간 곡 순서가 안정적
  };

  const pageUrl = (page) =>
    `${PLAYDATA_ORIGIN}${PLAYDATA_PATH}?` +
    new URLSearchParams({ page: String(page), ...PAGE_PARAMS });

  const PAGE_DELAY_MS = 700;   // 낮추지 말 것. 80페이지 × 0.7초 ≈ 1분
  const MAX_PAGES     = 120;   // 실제 마지막은 79페이지. 여유분 + 무한루프 안전핀

  // ---------------------------------------------------------------------------
  // ★ 확인이 필요한 유일한 두 줄 ★
  // ---------------------------------------------------------------------------

  // 곡 1개 = <li> 1개. 실제 ul 클래스를 확인해 좁힐 것 (예: 'ul.mu_list > li')
  const ROW_SELECTOR = 'li';

  // 곡 정보 div 다음에 오는 점수 div 들의 순서.
  // 5개인데 난이도는 4개 → 첫 열은 5ボタン 모드로 추정. null 이면 건너뛴다.
  // 페이지 헤더에서 컬럼명을 확인하고 확정할 것.
  const SCORE_COLUMNS = [null, 'EASY', 'NORMAL', 'HYPER', 'EX'];

  // ---------------------------------------------------------------------------
  // 파싱
  // ---------------------------------------------------------------------------
  const text = (el) => (el ? el.textContent.trim() : '');

  /** href 의 no= 파라미터. 값에 / + = 가 들어있어 URL 인코딩되어 있다. */
  function parseSongNo(href) {
    const m = /[?&]no=([^&]+)/.exec(href || '');
    return m ? decodeURIComponent(m[1]) : null;
  }

  /** ".../medal/meda_b.png" -> "meda_b". 미플레이("meda_none")는 null. */
  function parseMedal(div) {
    const img = div.querySelector('img[src*="/medal/meda_"]');
    if (!img) return null;
    const code = (img.getAttribute('src') || '').split('/').pop().replace(/\.\w+$/, '');
    return !code || code === 'meda_none' ? null : code;
  }

  /**
   * "-"  = 해당 보면이 존재하지 않음
   * "0"  = 보면은 있으나 미플레이
   * 그 외 = 실제 점수
   */
  function parseScore(div) {
    const raw = text(div.querySelector('p'));
    if (!raw || raw === '-') return null;
    const n = Number(raw.replace(/[^\d]/g, ''));
    return Number.isInteger(n) && n > 0 && n <= 100000 ? n : null;
  }

  function parseRow(li) {
    const link = li.querySelector('a[href*="mu_detail"]');
    if (!link) return [];                                  // 헤더 li 등

    const eagateSongNo = parseSongNo(link.getAttribute('href'));
    if (!eagateSongNo) return [];

    const divs = Array.from(li.children).filter((el) => el.tagName === 'DIV');
    const infoDiv = divs[0];
    const scoreDivs = divs.slice(1);

    // 곡 정보: a = 장르명(팝픈의 대표 곡명), p[0] = 타이틀, p[1] = 아티스트
    const ps = infoDiv ? infoDiv.querySelectorAll('p') : [];
    const genreName = text(link);
    const title  = text(ps[0]) || genreName;
    const artist = text(ps[1]) || null;

    const records = [];
    scoreDivs.forEach((div, i) => {
      const difficulty = SCORE_COLUMNS[i];
      if (!difficulty) return;                             // 5ボタン 등 대상 외 컬럼

      const score = parseScore(div);
      if (score === null) return;                          // 미수록 / 미플레이

      records.push({
        eagateSongNo,
        genreName,
        title,
        artist,
        difficulty,
        level: null,          // mu_top 에 레벨 정보 없음. 마스터 시딩으로 별도 확보.
        score,
        medalCode: parseMedal(div),
      });
    });
    return records;
  }

  // ---------------------------------------------------------------------------
  // 프로필 파싱
  //
  // 위치(nth-child)가 아니라 **일본어 라벨**을 찾아 그 옆 값을 읽는다. 페이지가 table 이든
  // dl 이든, 항목 순서가 바뀌든 라벨만 그대로면 계속 동작한다. 곡 목록 파서가 위치 기반이라
  // 셀렉터 검증이 필요했던 것과 대비되는 선택이다.
  //
  // ★ 라벨 문구는 미검증이다. 실제 페이지에서 확인하고 고칠 것 ★
  //   확인 방법: 프로필 페이지에서 콘솔에 __popnParseProfile(document) 입력
  // ---------------------------------------------------------------------------
  //
  // 교대(|)는 **긴 문구를 앞에** 둔다. 정규식은 좌→우로 먼저 맞는 것을 쓰므로,
  // /ガイド|ガイドライン/ 는 "ガイドライン" 에서 앞 세 글자만 먹고 "ライン" 을 값으로
  // 착각하게 만든다. 라벨이 서로 접두사 관계면 항상 이 함정이 있다.
  const LABELS = {
    playerName:    /プレーヤーネーム|プレイヤーネーム|プレイヤー名|ﾌﾟﾚｰﾔｰﾈｰﾑ/,
    characterName: /使用キャラクター|キャラクター|使用キャラ/,
    poptomoId:     /ポップともID|ポップとも|ポップトモ|popn?とも/i,
    popClass:      /ポップクラス|ポップ ?クラス/,
    superExtraRank:/スーパーエキストラランク|スーパーエキストラ|ｽｰﾊﾟｰｴｸｽﾄﾗ/,
    playCountNormal:   /ノーマルモード/,
    playCountExtra:    /エキストラモード/,
    playCountTime10min:/タイムフリー.*10|10 ?分/,
    playCountTime16min:/タイムフリー.*16|16 ?分/,
    brightness:    /明るさ/,
    keyBeam:       /キービーム|ビーム/,
    guideLine:     /ガイドライン|ガイド/,
    popKun:        /ポップ君|ポップくん|ﾎﾟｯﾌﾟ君/,
    lastPlayedAt:  /最終プレー日時|最終プレイ日時|最終プレー|最終プレイ|最終遊技/,
  };

  /** 전각 숫자·기호를 반각으로. 이게이트는 둘을 섞어 쓴다. */
  function toHalfWidth(s) {
    return (s || '').replace(/[０-９．－，]/g, (c) =>
      String.fromCharCode(c.charCodeAt(0) - 0xfee0));
  }

  /**
   * 라벨 문구를 가진 요소를 찾아 '값'을 돌려준다.
   * 값의 위치는 페이지마다 달라서 세 가지를 순서대로 시도한다:
   *   1. 다음 형제 요소            (dt → dd, th → td 가 형제인 경우)
   *   2. 부모의 다음 형제 요소      (tr > th, tr > td 처럼 한 겹 감싸인 경우)
   *   3. 라벨을 뺀 자기 자신의 텍스트 ("ポップクラス 170.59" 처럼 한 덩어리인 경우)
   */
  function valueByLabel(doc, re) {
    const nodes = Array.from(doc.querySelectorAll('dt,th,td,li,p,span,div,strong,b'));

    // 라벨을 감싼 컨테이너도 textContent 에 라벨을 포함해 함께 매칭된다. 그대로 첫 매치를
    // 쓰면 컨테이너의 다음 형제(= 보통 다음 항목)를 값으로 읽는 사고가 난다.
    // 그래서 '가장 구체적인' 후보를 고른다 — 자식 요소가 없는 잎, 그중 텍스트가 짧은 것.
    const candidates = nodes
      .filter((el) => {
        const t = el.textContent.trim();
        return t && t.length <= 40 && re.test(t);
      })
      .sort((a, b) => {
        const leaf = (el) => (el.children && el.children.length ? 1 : 0);
        return leaf(a) - leaf(b) || a.textContent.trim().length - b.textContent.trim().length;
      });

    for (const el of candidates) {
      const own = el.textContent.trim();
      const rest = own.replace(re, '').replace(/^[\s:：、]+/, '').trim();

      // 1. 라벨과 값이 한 덩어리인 경우("ポップクラス : 170.59")를 형제보다 먼저 본다.
      //    단 라벨을 부분적으로만 걷어낸 찌꺼기를 값으로 착각하면 안 된다 —
      //    /最終プレー/ 가 "最終プレー日時" 를 먹고 남긴 "日時" 같은 것.
      //    숫자나 두 글자 이상의 가타카나·라틴 문자가 있어야 값으로 인정한다.
      //    숫자 판정 전에 반각으로 바꾼다 — 이게이트는 전각 숫자를 쓰는 곳이 있고,
      //    \d 는 ASCII 만 잡아서 '１７０．５９' 를 값이 아니라고 판정해 버린다.
      if (rest && (/\d/.test(toHalfWidth(rest)) || /[ァ-ヶA-Za-z]{2,}/.test(rest))) return rest;

      const sib = el.nextElementSibling;
      if (sib && sib.textContent.trim()) return sib.textContent.trim();

      const parentSib = el.parentElement && el.parentElement.nextElementSibling;
      if (parentSib && parentSib.textContent.trim()) return parentSib.textContent.trim();

      if (rest) return rest;   // 형제가 없으면 찌꺼기라도 넘긴다. 서버가 범위 검증한다.
    }
    return null;
  }

  const num = (s) => {
    if (s == null) return null;
    const m = /-?\d[\d,]*(\.\d+)?/.exec(toHalfWidth(s).replace(/\s/g, ''));
    if (!m) return null;
    const n = Number(m[0].replace(/,/g, ''));
    return Number.isFinite(n) ? n : null;
  };

  const int = (s) => {
    const n = num(s);
    return n === null ? null : Math.trunc(n);
  };

  /** '26/06/27 14時頃' → '2026-06-27T14:00:00+09:00'. JST 를 명시해 브라우저 시간대와 무관하게 한다. */
  function parseLastPlayed(s) {
    if (!s) return null;
    const t = toHalfWidth(s);
    const m = /(\d{2,4})[\/\-年](\d{1,2})[\/\-月](\d{1,2})/.exec(t);
    if (!m) return null;

    // 시각은 날짜와 따로 찾는다. 한 정규식에 붙이면 날짜 뒤 공백을 어느 쪽이 먹느냐에 따라
    // 시각이 조용히 0 이 된다.
    const hm = /(\d{1,2})\s*時/.exec(t.slice(m.index + m[0].length));
    const hour = hm ? Number(hm[1]) : 0;
    if (hour > 23) return null;

    const [, y, mo, d] = m;
    const year = y.length === 4 ? Number(y) : 2000 + Number(y);
    const pad = (v) => String(v).padStart(2, '0');
    return `${year}-${pad(mo)}-${pad(d)}T${pad(hour)}:00:00+09:00`;
  }

  function parseProfile(doc, debug) {
    const raw = {};
    for (const [key, re] of Object.entries(LABELS)) raw[key] = valueByLabel(doc, re);

    const guide = raw.guideLine;
    const profile = {
      playerName:    raw.playerName    || null,
      characterName: raw.characterName || null,
      poptomoId:     raw.poptomoId ? (toHalfWidth(raw.poptomoId).match(/[\d-]{9,}/) || [null])[0] : null,

      popClass:       num(raw.popClass),
      superExtraRank: int(raw.superExtraRank),

      playCountNormal:    int(raw.playCountNormal),
      playCountExtra:     int(raw.playCountExtra),
      playCountTime10min: int(raw.playCountTime10min),
      playCountTime16min: int(raw.playCountTime16min),

      brightness: int(raw.brightness),
      keyBeam:    int(raw.keyBeam),
      guideLine:  guide == null ? null : /ON|オン|表示|あり/i.test(guide),
      popKun:     raw.popKun == null ? null
                  : (/スリム|ｽﾘﾑ|SLIM/i.test(raw.popKun) ? 'SLIM' : 'NORMAL'),

      lastPlayedAt: parseLastPlayed(raw.lastPlayedAt),
    };

    if (debug) {
      console.log('[popn-sync] 프로필 라벨 원문:', raw);
      console.log('[popn-sync] 프로필 파싱 결과:', profile);
      const missed = Object.keys(LABELS).filter((k) => raw[k] == null);
      if (missed.length) console.warn('[popn-sync] 못 찾은 라벨:', missed.join(', '));
    }
    return profile;
  }

  /** 프로필은 있으면 좋은 값이다. 실패해도 스코어 수집은 계속되어야 한다. */
  async function fetchProfile(debug) {
    try {
      const res = await fetch(`${PLAYDATA_ORIGIN}${PROFILE_PATH}`, { credentials: 'include' });
      if (!res.ok) return null;
      const doc = new DOMParser().parseFromString(await res.text(), 'text/html');
      const p = parseProfile(doc, debug);
      return Object.values(p).some((v) => v !== null) ? p : null;
    } catch (err) {
      console.warn('[popn-sync] 프로필 파싱 실패 (스코어 수집은 계속합니다):', err);
      return null;
    }
  }

  function parseDocument(doc, debug) {
    const rows = Array.from(doc.querySelectorAll(ROW_SELECTOR))
      .filter((li) => li.querySelector('a[href*="mu_detail"]'));

    if (debug && rows.length) {
      console.log('[popn-sync] 곡 행 수:', rows.length);
      console.log('[popn-sync] 첫 행 파싱 결과:', parseRow(rows[0]));
      console.table(rows.slice(0, 5).flatMap(parseRow));
    }

    const records = [];
    for (const li of rows) records.push(...parseRow(li));
    return { records, rowCount: rows.length };
  }

  // ---------------------------------------------------------------------------
  // 진행률 오버레이
  // ---------------------------------------------------------------------------
  function createOverlay() {
    const box = document.createElement('div');
    box.style.cssText = [
      'position:fixed', 'right:16px', 'bottom:16px', 'z-index:2147483647',
      'min-width:280px', 'padding:14px 16px', 'border-radius:10px',
      'background:#1b1b1f', 'color:#f4f4f5', 'box-shadow:0 6px 24px rgba(0,0,0,.35)',
      'font:13px/1.5 -apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif',
    ].join(';');
    box.innerHTML =
      '<div style="font-weight:600;margin-bottom:8px">스코어 동기화</div>' +
      '<div data-msg style="opacity:.85">준비 중…</div>' +
      '<div style="margin-top:8px;height:4px;border-radius:2px;background:#3f3f46">' +
      '<div data-bar style="height:4px;width:0;border-radius:2px;background:#22c55e;transition:width .2s"></div></div>';
    document.body.appendChild(box);

    const msg = box.querySelector('[data-msg]');
    const bar = box.querySelector('[data-bar]');
    return {
      update: (t, ratio) => {
        msg.textContent = t;
        if (typeof ratio === 'number') bar.style.width = `${Math.min(100, ratio * 100)}%`;
      },
      done: (t, ok = true) => {
        msg.textContent = t;
        bar.style.width = '100%';
        bar.style.background = ok ? '#22c55e' : '#ef4444';
        box.style.background = ok ? '#14532d' : '#7f1d1d';
        setTimeout(() => box.remove(), ok ? 6000 : 12000);
      },
    };
  }

  const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

  // ---------------------------------------------------------------------------
  // 업로드
  //   text/plain + 표준 헤더 = CORS simple request → 프리플라이트 없음.
  //   토큰은 헤더가 아니라 바디에 담는다(Authorization 헤더를 쓰면 프리플라이트가 뜬다).
  // ---------------------------------------------------------------------------
  async function upload(apiBase, token, records, profile) {
    const res = await fetch(`${apiBase}/api/imports`, {
      method: 'POST',
      headers: { 'Content-Type': 'text/plain;charset=UTF-8' },
      body: JSON.stringify({ token, source: 'BOOKMARKLET', records, profile }),
      // credentials 는 켜지 않는다. 토큰이 곧 신원이고 쿠키는 불필요하다.
    });
    if (res.status === 401) throw new Error('AUTH');
    if (!res.ok) throw new Error(`서버 오류 (${res.status})`);
    return res.json(); // { batchId, parsed, changed, duplicate }
  }

  // ---------------------------------------------------------------------------
  // 메인
  // ---------------------------------------------------------------------------
  async function post(apiBase, startPage, token, debug) {
    const base = apiBase || DEFAULT_API_BASE;
    const ui = createOverlay();

    try {
      if (!location.href.startsWith(PLAYDATA_ORIGIN)) {
        ui.done('e-amusement 플레이 데이터 페이지에서 실행해 주세요.', false);
        return;
      }
      if (!token) {
        ui.done('토큰이 없습니다. 사이트에서 북마클릿을 다시 복사해 주세요.', false);
        return;
      }

      const all = [];
      const seen = new Set();
      const first = startPage || 0;

      for (let page = first; page < MAX_PAGES; page++) {
        ui.update(`곡 목록 읽는 중… ${page + 1}페이지 · ${all.length}개`, (page - first) / 80);

        // 같은 오리진이라 세션 쿠키가 자동으로 실린다.
        const res = await fetch(pageUrl(page), { credentials: 'include' });
        if (!res.ok) throw new Error(`이게이트 응답 오류 (${res.status})`);

        const html = await res.text();
        const doc = new DOMParser().parseFromString(html, 'text/html');
        const { records, rowCount } = parseDocument(doc, debug && page === first);

        if (rowCount === 0) break;                       // 빈 페이지 = 끝

        let added = 0;
        for (const r of records) {
          const key = `${r.eagateSongNo}:${r.difficulty}`;
          if (seen.has(key)) continue;                   // 마지막 페이지 반복 방어
          seen.add(key);
          all.push(r);
          added++;
        }
        if (added === 0) break;                          // 새 곡 없음 = 끝

        await sleep(PAGE_DELAY_MS);
      }

      if (all.length === 0) {
        ui.done('읽어온 스코어가 없습니다. 로그인 상태와 셀렉터를 확인해 주세요.', false);
        return;
      }

      ui.update('프로필 읽는 중…', 0.9);
      const profile = await fetchProfile(debug);

      ui.update(`업로드 중… ${all.length}개`, 0.95);
      const result = await upload(base, token, all, profile);

      if (result.duplicate) {
        ui.done(`변경 없음 — ${all.length}개 확인 완료.`);
      } else {
        ui.done(`완료! ${result.changed}개 갱신 (전체 ${result.parsed}개)`);
      }
    } catch (err) {
      console.error('[popn-sync]', err);
      ui.done(
        err.message === 'AUTH'
          ? '인증 실패. 사이트에서 북마클릿을 다시 복사해 주세요.'
          : `실패: ${err.message}`,
        false
      );
    }
  }

  window.post = post;
  window.__popnParse = parseDocument;          // 개발용. 배포 시 제거.
  window.__popnParseProfile = (doc) => parseProfile(doc || document, true);
})();

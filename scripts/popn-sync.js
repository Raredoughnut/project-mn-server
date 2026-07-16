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
  async function upload(apiBase, token, records) {
    const res = await fetch(`${apiBase}/api/imports`, {
      method: 'POST',
      headers: { 'Content-Type': 'text/plain;charset=UTF-8' },
      body: JSON.stringify({ token, source: 'BOOKMARKLET', records }),
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

      ui.update(`업로드 중… ${all.length}개`, 0.95);
      const result = await upload(base, token, all);

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
  window.__popnParse = parseDocument;   // 개발용. 배포 시 제거.
})();

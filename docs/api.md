# mnArchive API — 프론트엔드 연동 문서

프론트엔드가 붙을 때 알아야 할 것을 모았다. 엔드포인트별 상세 스키마는 백엔드를 띄운 뒤
Swagger UI(`/swagger-ui.html`)에서도 볼 수 있지만, 인증 흐름과 값이 비어 있는 필드처럼
Swagger가 보여주지 못하는 내용이 여기 있다.

| | |
|---|---|
| 로컬 | `http://localhost:8080` |
| 운영 | `https://api.raredonut.com` |
| Swagger UI | `/swagger-ui.html` — 로컬만. 운영은 꺼져 있다 |

---

## 진행 상황

| 상태 | 항목 |
|---|---|
| 완료 | 구글 로그인 · 세션 · 로그아웃 |
| 완료 | 북마클릿 발급 · 폐기, 스코어 임포트 |
| 완료 | 곡 목록 + 내 점수 조회 (`/api/songs`) |
| 완료 | 플레이어 랭킹 + 공개 설정 (`/api/rankings/players`) |
| 일부 | 프로필 값 — 팝클래스·플레이 횟수는 사용자가 임포트해야 채워진다 |
| 미구현 | 레벨 · 노트 수 · 수록 버전 · BPM — 시딩 전이라 전부 `null` |
| 미구현 | 곡별 점수 랭킹 (지금은 플레이어 랭킹만) |

### ⚠ 최근 바뀐 것 — 기존 코드가 깨질 수 있다

- 쓰기 요청(`PUT`/`POST`/`DELETE`)에 **CSRF 토큰이 필요**해졌다. 없으면 `403`. → [CSRF](#csrf)
- `GET /api/me` 응답에 `rankingVisible` 필드가 추가됐다.

---

## 인증

JWT가 아니라 **세션 쿠키**를 쓴다. 토큰을 저장하거나 헤더에 실을 일이 없는 대신, 모든 요청에
`credentials: 'include'`가 필요하다. 빠뜨리면 쿠키가 실리지 않아 `401`이 난다.

### 로그인은 fetch로 할 수 없다

OAuth는 구글 도메인으로 리다이렉트되는 방식이라 AJAX로 처리가 불가능하다. 반드시 페이지를
이동시켜야 한다.

```js
// 로그인 시작 — fetch 아님
window.location.href = `${API}/oauth2/authorization/google`;

// 로그아웃
window.location.href = `${API}/logout`;
```

로그인이 끝나면 백엔드가 프론트 주소(`app.frontend-url`)로 되돌려 보낸다. 돌아온 뒤
`GET /api/me`를 호출해 상태를 확인하는 흐름이다.

### X-Requested-With 헤더

미인증 응답이 `Accept` 헤더에 따라 갈린다. 이 헤더를 붙이면 그 협상과 무관하게 `401`로 고정된다.

| 요청 | 미인증 시 응답 |
|---|---|
| `Accept: */*` (fetch 기본값) | 401 |
| `Accept: text/html` | 302 → 구글 로그인 페이지 |
| `X-Requested-With` 있음 | 401 (Accept 무관) |

fetch 기본값이면 헤더 없이도 `401`이 나오지만, 라이브러리나 SSR 경유로 `Accept`에 `text/html`이
섞이면 리다이렉트로 바뀌어 fetch가 구글 로그인 HTML을 받고 CORS 오류처럼 보인다. 그냥 붙이는
편이 안전하다.

---

## CSRF

세션 쿠키 인증이라 **상태를 바꾸는 요청에는 CSRF 토큰이 필요**하다. 조회(`GET`)는 해당 없다.

1. 아무 응답에서나 `XSRF-TOKEN` 쿠키가 내려온다 (HttpOnly가 꺼져 있어 JS로 읽을 수 있다)
2. 그 값을 `X-XSRF-TOKEN` 헤더에 실어 보낸다

> **axios를 쓴다면 자동이다.** `withCredentials: true`만 켜면 `XSRF-TOKEN` → `X-XSRF-TOKEN`
> 변환이 기본 동작이다. fetch는 직접 읽어야 한다.

> **⚠ 첫 쓰기 요청 전에 GET을 한 번 보내야 한다.** 쿠키가 아직 없는 상태에서 바로 `PUT`을
> 호출하면 읽을 토큰이 없다. 페이지 로드 시 `GET /api/me`를 부르는 흐름이면 자연스럽게 해결된다.

---

## 클라이언트 준비

위 규칙을 한 곳에 모아두면 이후 호출에서 신경 쓸 일이 없다.

```js
const API = process.env.NEXT_PUBLIC_API_BASE;   // 로컬: http://localhost:8080

const csrf = () => document.cookie
  .split('; ').find(c => c.startsWith('XSRF-TOKEN='))?.split('=')[1];

export const api = (path, init = {}) => fetch(`${API}${path}`, {
  ...init,
  credentials: 'include',                       // 세션 쿠키
  headers: {
    'X-Requested-With': 'XMLHttpRequest',       // 401 고정
    ...(init.method && init.method !== 'GET'
      ? { 'X-XSRF-TOKEN': csrf() }              // 쓰기에만 필요
      : {}),
    ...init.headers,
  },
});
```

---

## 엔드포인트

### `GET /api/me`

페이지 로드 시 호출해 로그인 여부를 판단한다. `200`이면 로그인, `401`이면 로그인 버튼을 띄운다.

| 필드 | 타입 | 설명 |
|---|---|---|
| `userId` | number | 내부 사용자 ID |
| `email` | string | 구글 계정 이메일 |
| `playerName` | string \| null | 이게이트 플레이어명. **null이면 아직 한 번도 임포트하지 않은 사용자** — 북마클릿 설치 안내를 띄울 판단 근거 |
| `popClass` | number \| null | 팝픈 클래스 (0~200, 소수 2자리) |
| `lastPlayedAt` | string \| null | 마지막 플레이 일시. JST 기준 **시각 단위**이므로 분·초는 신뢰하지 말 것 |
| `rankingVisible` | boolean | 랭킹 노출 동의 여부. 설정 토글의 현재 상태 |

### `PUT /api/me/ranking-visibility`

내 기록을 플레이어 랭킹에 보일지 정한다. **기본값은 꺼짐**이다.

```js
await api('/api/me/ranking-visibility', {
  method: 'PUT',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ visible: true }),
});
// → 200  { "visible": true }
```

> **⚠ 켜도 임포트 전이면 랭킹에 나타나지 않는다.** 순위를 매길 팝클래스가 없기 때문이다.
> API는 `200`으로 성공하지만 목록에는 안 보이는 상태가 되므로, `playerName`이 `null`인
> 사용자에게는 임포트 안내를 함께 보여줄 것.

`visible`을 빠뜨리면 `400`이다. 조용히 `false`로 처리하지 않는다.

### `GET /api/songs?page=0&size=50`

곡 단위로 난이도별 내 최고 기록을 묶어 반환한다. **아직 플레이하지 않은 보면도 포함**되므로
"레벨은 있는데 점수가 빈 칸"이 나오고, 이걸로 다음에 칠 곡 화면을 만들 수 있다.

```json
{
  "songs": [
    {
      "songId": 42,
      "eagateSongNo": "Hk+3PjqHbOHlRzL46XN6Cw==",
      "title": "Daisuke",
      "genre": "ユーロビート",
      "version": null,
      "lightLevel": null,  "lightScore": 87210,  "lightMedal": "silver_star",
      "normalLevel": null, "normalScore": null,  "normalMedal": null,
      "hyperLevel": null,  "hyperScore": 95120,  "hyperMedal": "gold_star",
      "exLevel": null,     "exScore": 99319,     "exMedal": "gold_star"
    }
  ],
  "page": 0, "size": 50, "total": 940
}
```

**null이 두 가지 뜻이다.**

- `{난이도}Level`이 null → 레벨 정보가 아직 시딩되지 않음 *(보면은 존재할 수 있음)*
- `{난이도}Score`가 null → 그 난이도 보면이 없거나, 있어도 아직 플레이하지 않음

`size`는 **200으로 잘린다.** 요청보다 작은 값이 올 수 있으니 응답의 `size`를 기준으로 다음
페이지를 계산할 것.

### `GET /api/rankings/players?page=0&size=50`

팝클래스 내림차순 전체 플레이어 순위.

```json
{
  "entries": [
    { "rank": 1, "playerName": "CONST", "characterName": "パラボー",
      "popClass": 170.59, "me": false },
    { "rank": 2, "playerName": "MIMI",  "characterName": "ミミ",
      "popClass": 168.20, "me": true }
  ],
  "page": 0, "size": 50, "total": 42,
  "myRank": 2
}
```

> **⚠ 공개에 동의한 사용자만 나온다.** 기본값이 꺼짐이라 **초기에는 목록이 비어 있는 게
> 정상**이다. 빈 상태 화면을 반드시 준비할 것 — "아직 아무도 공개하지 않았습니다 /
> 내 기록을 공개하시겠어요?" 같은 안내가 자연스럽다.

| 필드 | 타입 | 설명 |
|---|---|---|
| `rank` | number | 순위. **동점이면 공유하고 다음이 건너뛴다** (170.59가 둘이면 1, 1, 3) |
| `playerName` | string | 이게이트 플레이어명. 목록에 나온다면 항상 값이 있다 |
| `characterName` | string \| null | 사용 캐릭터 |
| `popClass` | number | 팝픈 클래스 |
| `me` | boolean | 이 줄이 나 자신인지. 강조 표시에 쓸 것 |
| `myRank` | number \| null | 내 순위. **공개 안 했거나 임포트 전이면 null** |

목록에 나타나는 조건은 **공개 동의 + 임포트 완료** 둘 다이다. `total`도 같은 조건으로 세므로
전체 가입자 수가 아니다.

### `POST` / `DELETE /api/me/bookmarklet`

`POST`는 토큰이 박힌 완성된 북마클릿 문자열을 반환한다. 그대로 `<a href="{bookmarklet}">`로
렌더해서 즐겨찾기 바에 드래그하게 하면 된다. 사용자가 토큰을 보거나 복사할 일은 없다.

```json
{ "bookmarklet": "javascript:(function(){...})();" }
```

> **⚠ 재발급하면 이전 북마클릿이 즉시 무효가 된다.** 사용자가 여러 브라우저에 설치해 뒀다면
> 나머지가 모두 끊긴다. 재발급 버튼 옆에 이 사실을 반드시 표시할 것.

`DELETE`는 토큰을 폐기한다. 본문 없이 `200`.

참고 — `POST /api/imports`는 북마클릿이 이게이트 페이지에서 직접 호출하는 엔드포인트다.
프론트가 부를 일은 없다.

---

## 비어 있는 필드

스키마에는 있지만 아직 데이터가 채워지지 않은 것들. 전부 null 처리가 필요하다.

| 필드 | 언제 채워지나 |
|---|---|
| `level`, `notes` | 별도 시딩 필요 — 이게이트 목록 페이지에 없는 값이다. **일정 미정** |
| `version`, `bpm`, `duration` | 동일 |
| `playerName`, `popClass`, `characterName`, 플레이 횟수 | 사용자가 북마클릿으로 **임포트하면** 채워진다 |

레벨이 없으면 난이도별 정렬·필터 UI를 만들 수 없다. 그 기능이 필요하다면 시딩 일정을 먼저
맞추는 게 좋다.

---

## 에러 코드

| 코드 | 원인 | 대응 |
|---|---|---|
| 400 | 본문이 잘못됨 (예: `visible` 누락) | 요청 확인 |
| 401 | 미로그인, 또는 세션 만료 | 로그인 버튼 표시 |
| 403 | **CSRF 토큰 없음/불일치** | `X-XSRF-TOKEN` 헤더 확인. GET을 먼저 호출해 쿠키를 받았는지 확인 |

> **CORS 오류처럼 보이는데 실제로는 인증 문제인 경우가 많다.** `credentials: 'include'`나
> `X-Requested-With`가 빠지면 브라우저 콘솔에는 CORS 메시지가 뜨지만, 원인은 리다이렉트된
> 구글 로그인 HTML을 fetch가 받은 것이다. CORS를 의심하기 전에 이 둘을 먼저 확인할 것.

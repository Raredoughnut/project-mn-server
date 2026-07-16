-- ============================================================
-- Pop'n Music score tracker — 확정 스키마 (PostgreSQL)
--
-- 확정된 결정
--   1. 보면(charts) 단위 행           — 난이도를 컬럼으로 펼치지 않는다
--   2. score_records 는 append-only    — 이력 없이는 '이전과 비교'가 불가능
--   3. clear_medals 룩업 테이블        — 오타 방지 + 메달 우열 비교
--   4. eagate_code 매핑 확정           — meda_a(금별) ~ meda_l(미상)
--   5. 프로필 이력 테이블 분리         — 팝클래스 추이 그래프의 원천
--   6. 타입 정리                       — pop_class 는 numeric, double 아님
--
--   mu_detail(판정/횟수/옵션)은 저장하지 않는다.
-- ============================================================

CREATE TYPE chart_difficulty AS ENUM ('LIGHT', 'NORMAL', 'HYPER', 'EX');
CREATE TYPE import_source    AS ENUM ('BOOKMARKLET', 'MANUAL');
CREATE TYPE import_status    AS ENUM ('RECEIVED', 'PARSED', 'FAILED');
CREATE TYPE pop_kun_type     AS ENUM ('SLIM', 'NORMAL');


-- ------------------------------------------------------------
-- users : 신원 + 이게이트 프로필의 '현재값' 캐시
--   변하는 통계의 이력은 user_profile_snapshots 가 보관한다.
--   여기 있는 프로필 값들은 조회 편의를 위한 캐시일 뿐 진실의 원천이 아니다.
-- ------------------------------------------------------------
CREATE TABLE users (
    id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    google_id   text NOT NULL UNIQUE,        -- Google ID 토큰의 sub
    email       text UNIQUE,
    api_token   text UNIQUE,                 -- 북마클릿 토큰의 SHA-256 hex. 평문 저장 금지
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now(),

    -- 이게이트에서 긁어온 값 (최신 스냅샷)
    player_name    text,                     -- 'ＣＯＮＳＴ'
    poptomo_id     text,                     -- '2990-6013-2577'
    character_name text,                     -- 'パラボー'
    pop_class      numeric(5,2) CHECK (pop_class BETWEEN 0 AND 200),  -- 170.59. double 금지
    super_extra_rank smallint CHECK (super_extra_rank BETWEEN 0 AND 5),

    play_count_normal     integer CHECK (play_count_normal     >= 0),
    play_count_extra      integer CHECK (play_count_extra      >= 0),
    play_count_time_10min integer CHECK (play_count_time_10min >= 0),
    play_count_time_16min integer CHECK (play_count_time_16min >= 0),

    -- 環境設定項目 : 사용자가 게임에서 설정하는 값. 추이를 그릴 대상은 아니라 현재값만 둔다.
    brightness smallint CHECK (brightness BETWEEN 0 AND 100),
    key_beam   smallint CHECK (key_beam  BETWEEN -50 AND 300),
    guide_line boolean,
    pop_kun    pop_kun_type,

    -- '26/06/27 14時頃' — JST, 시각 단위. 분·초는 신뢰하지 말 것.
    last_played_at timestamptz
);


-- ------------------------------------------------------------
-- user_profile_snapshots : 팝클래스·플레이 횟수 추이 (append-only)
--   users 에 덮어쓰기만 하면 '팝클래스 성장 그래프'를 영영 만들 수 없다.
-- ------------------------------------------------------------
CREATE TABLE user_profile_snapshots (
    id              bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id         bigint NOT NULL,
    import_batch_id bigint NOT NULL,

    pop_class             numeric(5,2) CHECK (pop_class BETWEEN 0 AND 200),
    super_extra_rank      smallint CHECK (super_extra_rank BETWEEN 0 AND 5),
    play_count_normal     integer CHECK (play_count_normal     >= 0),
    play_count_extra      integer CHECK (play_count_extra      >= 0),
    play_count_time_10min integer CHECK (play_count_time_10min >= 0),
    play_count_time_16min integer CHECK (play_count_time_16min >= 0),
    last_played_at        timestamptz,

    recorded_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX user_profile_snapshots_user_idx
    ON user_profile_snapshots (user_id, recorded_at DESC);


-- ------------------------------------------------------------
-- songs : 곡 마스터
-- ------------------------------------------------------------
CREATE TABLE songs (
    id             bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    eagate_song_no text NOT NULL UNIQUE,     -- mu_detail?no=... 의 값. 임포트 매칭 키
    title          text NOT NULL,            -- 曲名     (<a> 텍스트)  ← 임포트가 채운다
    genre          text,                     -- ジャンル名 (p[0])       ← 임포트가 채운다
    artist         text,                     --                        ← 임포트가 채운다

    -- ↓ 아래는 mu_top 에 없다. 나중에 직접 시딩할 컬럼들 (전부 nullable)
    version        smallint CHECK (version > 0),   -- 수록 버전 번호
    duration_seconds integer CHECK (duration_seconds > 0),  -- 곡 길이. '2:14' → 134
    min_bpm        smallint CHECK (min_bpm BETWEEN 1 AND 999),
    avg_bpm        smallint CHECK (avg_bpm BETWEEN 1 AND 999),
    max_bpm        smallint CHECK (max_bpm BETWEEN 1 AND 999),

    created_at     timestamptz NOT NULL DEFAULT now(),
    updated_at     timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT songs_bpm_order CHECK (
        min_bpm IS NULL OR max_bpm IS NULL OR min_bpm <= max_bpm
    )
);


-- ------------------------------------------------------------
-- charts : 보면. 곡당 최대 4행.
--   존재하지 않는 보면(mu_top 의 '-')은 행 자체가 없다.
--   level 은 mu_top 에 없으므로 별도 시딩 전까지 NULL 이다.
-- ------------------------------------------------------------
CREATE TABLE charts (
    id         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    song_id    bigint NOT NULL REFERENCES songs (id) ON DELETE CASCADE,
    difficulty chart_difficulty NOT NULL,

    -- ↓ mu_top 에 없다. 나중에 직접 시딩할 컬럼들 (전부 nullable)
    level      smallint CHECK (level BETWEEN 1 AND 50),
    notes      integer  CHECK (notes > 0),   -- 노트 수. 보면마다 다르므로 songs 가 아니라 여기

    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT charts_song_difficulty_key UNIQUE (song_id, difficulty)
);

-- '레벨 45 이상 중 아직 95000 미만' 류 조회의 주 인덱스
CREATE INDEX charts_level_idx ON charts (level) WHERE level IS NOT NULL;


-- ------------------------------------------------------------
-- clear_medals : 룩업
--   rank_order 가 있어야 '점수는 그대로인데 메달이 올랐다'를 감지할 수 있다.
--   문자열 비교로는 안 된다 — 알파벳순은 'gold' < 'silver' 라 우열과 반대다.
-- ------------------------------------------------------------
CREATE TABLE clear_medals (
    code        text PRIMARY KEY,
    label       text NOT NULL,
    rank_order  smallint UNIQUE,   -- 클수록 상위. 미상이면 NULL 허용
    eagate_code text UNIQUE        -- 파서가 보는 이미지 파일명
);

INSERT INTO clear_medals (code, label, rank_order, eagate_code) VALUES
    ('gold_star',       '금별',       12, 'meda_a'),
    ('silver_star',     '은별',       11, 'meda_b'),
    ('silver_diamond',  '은다이아',   10, 'meda_c'),
    ('silver_circle',   '은원',        9, 'meda_d'),
    ('bronze_star',     '동별',        8, 'meda_e'),
    ('bronze_diamond',  '동다이아',    7, 'meda_f'),
    ('bronze_circle',   '동원',        6, 'meda_g'),
    ('black_star',      '흑별',        5, 'meda_h'),
    ('black_diamond',   '흑다이아',    4, 'meda_i'),
    ('black_circle',    '흑동그라미',  3, 'meda_j'),
    ('easy_clear',      '이지 클리어', 2, 'meda_k'),
    -- ⚠ meda_l 은 정체 미상. 알파벳 규칙에 따라 최하위로 임시 배치했다.
    --    실제 메달이 확인되면 label 과 rank_order 를 반드시 재검토할 것.
    ('unknown_l',       '(미상)',      1, 'meda_l'),
    ('none',            '미플레이',    0, 'meda_none');


-- ------------------------------------------------------------
-- import_batches : 업로드 1회 = 1행
--   중복 업로드 차단 / '이번에 뭐가 올랐나' 화면 / 잘못된 임포트 롤백
-- ------------------------------------------------------------
CREATE TABLE import_batches (
    id                  bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id             bigint NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    source              import_source NOT NULL,
    status              import_status NOT NULL DEFAULT 'RECEIVED',
    payload_checksum    text NOT NULL,   -- 북마클릿 재클릭 방어
    parsed_chart_count  integer NOT NULL DEFAULT 0 CHECK (parsed_chart_count  >= 0),
    changed_chart_count integer NOT NULL DEFAULT 0 CHECK (changed_chart_count >= 0),
    error_message       text,
    created_at          timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT import_batches_user_checksum_key UNIQUE (user_id, payload_checksum),
    -- 아래 복합 FK 들이 참조할 대상
    CONSTRAINT import_batches_id_user_key       UNIQUE (id, user_id),
    CONSTRAINT import_batches_changed_le_parsed CHECK (changed_chart_count <= parsed_chart_count),
    CONSTRAINT import_batches_failed_has_reason
        CHECK (status <> 'FAILED' OR error_message IS NOT NULL)
);

CREATE INDEX import_batches_user_created_idx
    ON import_batches (user_id, created_at DESC);

ALTER TABLE user_profile_snapshots
    ADD CONSTRAINT user_profile_snapshots_user_fk
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    ADD CONSTRAINT user_profile_snapshots_batch_fk
        FOREIGN KEY (import_batch_id, user_id)
        REFERENCES import_batches (id, user_id) ON DELETE RESTRICT;


-- ------------------------------------------------------------
-- score_records : APPEND-ONLY
--   점수 또는 메달 등급이 나아졌을 때만 행을 추가한다.
--   (user_id, chart_id) 의 최신 행 = 현재 기록.
--   미플레이(score 0)는 행을 만들지 않는다 — charts 와의 차집합으로 구한다.
-- ------------------------------------------------------------
CREATE TABLE score_records (
    id              bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id         bigint NOT NULL,
    chart_id        bigint NOT NULL REFERENCES charts (id) ON DELETE RESTRICT,
    import_batch_id bigint NOT NULL,
    score           integer NOT NULL CHECK (score BETWEEN 0 AND 100000),
    medal_code      text REFERENCES clear_medals (code) ON DELETE RESTRICT,
    recorded_at     timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT score_records_user_fk
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    -- 남의 배치가 내 스코어를 만들 수 없게 DB 가 막는다
    CONSTRAINT score_records_batch_fk
        FOREIGN KEY (import_batch_id, user_id)
        REFERENCES import_batches (id, user_id) ON DELETE RESTRICT
);

CREATE INDEX score_records_user_chart_recorded_idx
    ON score_records (user_id, chart_id, recorded_at DESC, id DESC);
CREATE INDEX score_records_batch_idx ON score_records (import_batch_id);
CREATE INDEX score_records_chart_idx ON score_records (chart_id);


-- ------------------------------------------------------------
-- 현재 기록 = 원안의 records 가 하려던 것
--   느려지면 그때 캐시 테이블로 교체한다. 지금 넣으면 동기화 버그만 생긴다.
-- ------------------------------------------------------------
CREATE VIEW user_chart_current AS
SELECT DISTINCT ON (user_id, chart_id)
       user_id, chart_id, score, medal_code, recorded_at, import_batch_id
FROM   score_records
ORDER  BY user_id, chart_id, recorded_at DESC, id DESC;

-- ------------------------------------------------------------
-- 랭킹 공개 설정 (opt-in)
--
-- 기본값은 false 다. 서비스는 전체 공개지만, 자기 기록을 남에게 보일지는
-- 사용자가 직접 켜야 한다. 기존 행도 전부 false 로 들어가므로 이 마이그레이션
-- 만으로 갑자기 노출되는 사용자는 없다.
-- ------------------------------------------------------------
ALTER TABLE users
    ADD COLUMN ranking_visible boolean NOT NULL DEFAULT false;

COMMENT ON COLUMN users.ranking_visible IS
    '랭킹 노출 동의 여부. false 면 어떤 공개 목록에도 나타나지 않는다.';

-- 랭킹 조회는 "공개 동의 + 팝클래스 있음"을 팝클래스 내림차순으로 훑는다.
-- 부분 인덱스로 잡으면 비공개·미임포트 사용자가 인덱스에서 아예 빠진다.
-- player_name 조건은 넣지 않는다 — 인덱스 크기를 키우는 것에 비해 걸러지는
-- 행이 거의 없고(pop_class 가 있으면 player_name 도 있다), 조건이 바뀔 여지가 있다.
CREATE INDEX users_ranking_idx
    ON users (pop_class DESC, id)
    WHERE ranking_visible AND pop_class IS NOT NULL;

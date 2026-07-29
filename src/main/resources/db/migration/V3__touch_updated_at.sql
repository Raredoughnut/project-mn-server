-- ------------------------------------------------------------
-- updated_at 을 DB 가 책임지게 한다.
--
-- 지금까지 이 컬럼은 INSERT 시 DEFAULT now() 로만 채워지고, UPDATE 때는 쓰는 쪽이
-- 직접 `updated_at = now()` 를 적어 줬다(UserTokenService, ImportService).
-- JPA 로 수정하는 경로에는 그 규칙이 적용되지 않아, Hibernate 가 updated_at 에 NULL 을
-- 실어 보내고 NOT NULL 제약에 걸렸다. 엔티티 쪽에서 이 컬럼을 쓰지 않도록 막았고
-- (User.updatedAt 은 insertable=false, updatable=false), 여기서 트리거로 값을 채운다.
--
-- 이렇게 하면 누가 어떤 경로로 고치든 시계가 하나(DB) 로 통일된다.
-- ------------------------------------------------------------
CREATE OR REPLACE FUNCTION touch_updated_at() RETURNS trigger AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- updated_at 을 가진 세 테이블 모두에 건다. songs/charts 는 지금은 JDBC 로만 수정해서
-- 문제가 없지만, 나중에 엔티티가 생겼을 때 같은 함정에 다시 빠지지 않도록 규칙을 맞춰 둔다.
CREATE TRIGGER users_touch_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION touch_updated_at();

CREATE TRIGGER songs_touch_updated_at
    BEFORE UPDATE ON songs
    FOR EACH ROW EXECUTE FUNCTION touch_updated_at();

CREATE TRIGGER charts_touch_updated_at
    BEFORE UPDATE ON charts
    FOR EACH ROW EXECUTE FUNCTION touch_updated_at();

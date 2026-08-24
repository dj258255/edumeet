-- refresh token 을 Redis 로 옮긴다. (#70)
--
-- 결함을 고치는 게 아니라 설계 판단이다.
-- refresh token 은 도메인 데이터가 아니라 세션 상태다 -
--   TTL 이 본질이고, JOIN 이 없고, 잃으면 재로그인하면 된다.
-- 관계형 저장소에 있을 이유가 없다.
--
-- 이 테이블을 지우면 기존 사용자는 한 번 재로그인해야 한다.
-- refresh token 은 재생성 가능한 상태라 데이터 손실이 아니다.

DROP TABLE IF EXISTS refresh_token;

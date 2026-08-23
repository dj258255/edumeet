-- HLS egress 상태를 세션에 기록한다. (#75)
--
-- hls_egress_id    진행 중인 egress. 끝나면 NULL 로 돌린다.
--                  두 번 시작하는 것을 막는 데 쓴다 - egress 하나가 코어 1~4개를 먹는다.
-- hls_playlist_url 플레이어가 여는 주소. 방송이 끝나도 남긴다.
--                  같은 디렉터리의 index.m3u8 이 다시보기로 남기 때문이다.

ALTER TABLE meeting
    ADD COLUMN hls_egress_id VARCHAR(100) NULL,
    ADD COLUMN hls_playlist_url VARCHAR(500) NULL;

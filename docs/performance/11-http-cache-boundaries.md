# HTTP 캐시 경계 — 빠르게 줄 것과 오래 주면 안 되는 것

## 문제

캐시는 성능 기능이지만, 같은 `Cache-Control` 을 전부에 걸면 장애가 된다.

EduMeet 운영 경로에는 성격이 다른 파일이 같이 있다.

| 대상 | 바뀌는 시점 | 잘못 캐시하면 |
|---|---|---|
| `index.html` | 매 배포 | 새 JS 번들이 배포돼도 옛 HTML 이 옛 번들을 가리킨다 |
| `/assets/*.js`, `/assets/*.css` | 파일 내용 변경 시 URL 변경 | 짧게 캐시하면 매 방문마다 정적 파일을 다시 받는다 |
| `live.m3u8` | HLS 세그먼트마다 재작성 | 방송이 멈춘 것처럼 보인다 |
| `seg_00000.ts` | 한 번 쓰이면 불변, 방송 재시작 시 이름 재사용 | 새 방송 초반에 이전 방송 조각을 물 수 있다 |

그래서 "정적 파일 캐시를 켰다"가 아니라, URL 안정성과 신선도 요구에 따라 경계를 나눴다.

## 적용

`deploy/nginx/edumeet.conf` 에서 네 구간을 분리했다.

```nginx
location = /index.html {
    add_header Cache-Control "no-cache, no-store, must-revalidate" always;
    expires -1;
}

location /assets/ {
    try_files $uri =404;
    add_header Cache-Control "public, max-age=31536000, immutable" always;
}

location ~ \.m3u8$ {
    add_header Cache-Control "no-cache, no-store, must-revalidate" always;
    expires -1;
}

location ~ \.ts$ {
    add_header Cache-Control "public, max-age=4" always;
}
```

## 왜 HLS 세그먼트는 길게 주지 않았나

Vite asset 은 파일명에 해시가 들어간다. 내용이 바뀌면 URL 이 바뀌므로 1년 캐시가 안전하다.

HLS 세그먼트는 다르다.

현재 ffmpeg 는 `seg_%05d.ts` 로 파일을 만든다. 방송을 다시 시작하면 `seg_00000.ts` 부터 이름이 재사용된다.
따라서 세그먼트를 `immutable` 이나 긴 `max-age` 로 두면, 재시작 직후 플레이어가 이전 방송 조각을 받을 수 있다.

세그먼트는 여러 시청자가 같은 몇 초 안에 가져가므로 짧은 캐시의 이득은 있다.
하지만 재시작 안전성이 더 중요해서 세그먼트 길이 2초의 두 배인 4초만 허용했다.

## 포트폴리오에 쓸 문장

> HLS 도입 후 정적 파일과 라이브 조각을 같은 캐시 정책으로 다루면 안 된다고 판단했습니다.
> `index.html` 과 `live.m3u8` 은 즉시 최신성이 필요해 `no-store` 로 두고,
> 해시가 붙은 Vite asset 은 1년 `immutable` 로 캐시했습니다.
> 반면 HLS `.ts` 세그먼트는 방송 재시작 시 파일명이 재사용되므로 장기 캐시가 아니라 4초 짧은 캐시만 허용했습니다.
> CDN 을 붙이기 전에도 브라우저·프록시 캐시 경계를 명확히 나눠, 성능과 신선도 요구를 같이 맞췄습니다.

## 하지 않은 것

- HLS 세그먼트 장기 캐시: generation id 를 URL 에 넣기 전에는 안전하지 않다.
- CDN 도입: 지금은 트래픽보다 구조 검증이 우선이다. CDN 은 같은 헤더를 원본 기준으로 그대로 태울 수 있게 만든 뒤 붙인다.
- LL-HLS: 부분 세그먼트와 blocking reload 까지 들어가며, 현재 병목은 그 전 단계의 송출·운영 경계다.

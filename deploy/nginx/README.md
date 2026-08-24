# nginx 배치 (#88)

Cloudflare 프록시 뒤에서 도는 리버스 프록시 설정.

```
브라우저 --https--> Cloudflare --https(443)--> nginx --http(8080)--> Spring
```

## 파일

| | |
|---|---|
| `edumeet.conf` | apex(프론트) · www(301) · api(백엔드) 세 vhost |
| `02-cloudflare-visitor.conf` | `CF-Visitor` 에서 원래 프로토콜을 꺼내는 map |

## 배치

```bash
sudo cp 02-cloudflare-visitor.conf /etc/nginx/conf.d/
sudo cp edumeet.conf              /etc/nginx/conf.d/
sudo nginx -t && sudo systemctl reload nginx
```

**`nginx -t` 를 먼저 돌린다.** 실패하면 reload 가 거부되고 기존 프로세스가 그대로 산다 —
실제로 `http2 on;`(1.25.1+ 문법)을 1.20.1 서버에 넣었다가 여기서 걸렸고,
덕분에 사이트가 안 죽었다.

## 인증서 — Let's Encrypt

```
/etc/letsencrypt/live/studywithtymee.com/{fullchain,privkey}.pem
```

### 프록시 뒤인데 Let's Encrypt 가 되는가 — 된다

처음에는 자체서명을 쓰고 *"Cloudflare 뒤라 Let's Encrypt 는 어렵다"* 고 적었다.
닭-달걀이라고 봤다 — 암호화 모드가 `전체` 면 CF 가 원본 **443** 에 붙으므로
원본에 인증서가 없으면 HTTP-01 검증이 도달하지 못한다고.

**확인해 보니 아니었다.** Cloudflare 는 프록시가 켜져 있어도
`http://` 요청을 원본 **80** 으로 넘기고 `/.well-known/acme-challenge/` 를 그대로 통과시킨다.
프로브 파일을 놓고 밖에서 읽어 확인한 뒤 발급했다.

그래서 **Origin Certificate(대시보드 작업) 없이** 발급할 수 있고,
공개 CA 인증서라 암호화 모드를 **`전체(엄격)`** 으로 올릴 수 있다.

### ★ 브라우저가 보는 인증서는 이게 아니다

프록시를 켜면 TLS 가 **두 구간**으로 끊긴다. 각 구간이 다른 인증서를 쓴다.

```
브라우저 ──TLS①──▶ Cloudflare ──TLS②──▶ 원본 nginx
          CF 엣지 인증서          Let's Encrypt
          (Google Trust Services)
```

실제로 확인한 값:

```
$ echo | openssl s_client -connect studywithtymee.com:443 \
        -servername studywithtymee.com | openssl x509 -noout -issuer
issuer= /C=US/O=Google Trust Services/CN=WE1        <- Let's Encrypt 가 아니다
```

**원본 인증서는 밖에서 볼 수 없다.** 원본 443 은 CF 대역만 허용하므로
`openssl s_client` 로 직접 붙는 것 자체가 막힌다 — 그게 의도한 상태다.

그래서 *"우리 사이트는 Let's Encrypt 를 쓴다"* 는 절반만 맞다.
정확히는 **원본 구간이** Let's Encrypt 다. 이 구분이 필요한 이유:

| 착각 | 실제 |
|---|---|
| 원본 인증서가 만료되면 브라우저에 경고가 뜬다 | **526** 이 뜬다. 브라우저 인증서 창은 멀쩡하다 |
| 브라우저에 자물쇠가 보이니 원본까지 암호화됐다 | 모드가 `유연` 이면 원본 구간은 평문이다 |
| 원본 인증서를 갱신하면 방문자가 새 인증서를 본다 | 방문자가 보는 건 CF 가 자기 주기로 갱신한다 |

두 번째 줄이 `전체(엄격)` 을 쓰는 이유다. `유연`·`전체` 는 자물쇠가 똑같이 보이면서
원본 구간이 각각 평문·무검증이다. **눈으로는 셋을 구분할 수 없다.**

### 갱신

```
certbot-renew.timer          매일. is-enabled 만으로는 안 돈다 - start 해야 한다
renewal-hooks/deploy/        갱신 후 nginx -t && systemctl reload nginx
```

**deploy 훅이 핵심이다.** 없으면 certbot 은 새 인증서를 받는데 nginx 는
메모리에 올린 옛 파일을 계속 쓴다. 90일 뒤 **디스크에는 유효한 파일이 있는데
nginx 는 만료된 것을 내미는** 상태가 된다.

이때 증상은 브라우저 경고가 **아니다.** 브라우저는 원본 인증서를 볼 일이 없다(아래).
`전체(엄격)` 에서 CF 가 원본 검증에 실패하므로 **526 Invalid SSL Certificate** 이 뜬다.
브라우저 인증서 창을 봐도 멀쩡해 보이니 원인을 엉뚱한 데서 찾게 된다.

`certbot renew --dry-run` 으로 훅까지 확인했다.

### 80 포트를 계속 열어 둔다

`/.well-known/acme-challenge/` 갱신 경로 때문이다. 막으면 90일 뒤 갱신이 실패한다.
나머지는 전부 https 로 301 한다.

## 겪은 것

| 증상 | 원인 |
|---|---|
| **521** Web Server Is Down | 모드가 `전체` 라 CF 가 원본 **443** 에 붙는데 원본이 80만 듣고 있었다 |
| `unknown directive "http2"` | `http2 on;` 은 1.25.1+ 문법. 서버는 1.20.1 |
| **403** Permission denied | SELinux `Enforcing`. `/var/www/edumeet` 이 `var_t` 라 nginx 가 못 읽는다. 파일 권한은 멀쩡해서 `ls -l` 로는 안 보인다 |

SELinux 는 `chcon` 이 아니라 규칙을 등록해야 재라벨링 후에도 남는다.

```bash
sudo semanage fcontext -a -t httpd_sys_content_t "/var/www/edumeet(/.*)?"
sudo restorecon -Rv /var/www/edumeet
```

## 8080 을 닫았다

nginx 가 서기 전에는 `0.0.0.0/0` 으로 8080 이 열려 있었다(보안 목록 규칙 `kokjip`).
**원본 IP 로 백엔드에 직접 붙는 우회로**다 — nginx 의 TLS·헤더·타임아웃을 전부 건너뛴다.

```
지금  원본 IP 로는 80·443·8080 전부 닫힘. Cloudflare 대역에만 80/443 열림
```

보안 목록은 바꾸기 전에 백업했다(`edumeet-seclist-backup-*.json`).

## 아직 안 한 것

- 암호화 모드 `전체(엄격)` + Origin Certificate (지금은 자체서명 + `전체(비엄격)`)
- STOMP 하트비트. 켜면 `proxy_read_timeout` 을 60초로 되돌려도 된다

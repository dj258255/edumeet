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

## 인증서

Cloudflare Origin Certificate 를 `/etc/nginx/ssl/origin.{crt,key}` 에 둔다.

지금은 **자체서명**이 들어 있다. 암호화 모드가 `전체(비엄격)` 이라 CF 가
원본 인증서를 암호화에만 쓰고 신원 검증은 하지 않으므로 동작한다.
`전체(엄격)` 으로 올리려면 Origin Certificate 로 교체해야 한다.

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

## 아직 안 한 것

- **8080 이 `0.0.0.0/0` 으로 열려 있다** (보안 목록 규칙 `kokjip`).
  원본 IP 로 백엔드에 직접 붙는 우회로다. nginx 가 섰으니 닫는 게 맞다
- 암호화 모드 `전체(엄격)` + Origin Certificate
- WebSocket 이 프록시 뒤에서 실제로 유지되는지 **측정**

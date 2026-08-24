#!/bin/sh

# SSL 인증서 확인 및 자동 설정
if [ -f "/etc/letsencrypt/live/i13c205.p.ssafy.io/fullchain.pem" ]; then
    echo "SSL certificates found - Enabling HTTPS"
    cd /etc/nginx/conf.d
    mv default.conf default.conf.disabled 2>/dev/null || true
    mv ssl.conf.disabled ssl.conf 2>/dev/null || true
else
    echo "SSL certificates not found - Using HTTP only"
    cd /etc/nginx/conf.d
    mv ssl.conf ssl.conf.disabled 2>/dev/null || true
    mv default.conf.disabled default.conf 2>/dev/null || true
fi

nginx -g "daemon off;"
#!/bin/bash

# ==========================================
# 🔒 Chess Game SSL 자동 설치 스크립트
# ==========================================

KEY_PATH="$HOME/Desktop/chess/saram2rum.pem"
SERVER_IP="52.64.223.234"
DOMAIN="chessez.com"
EMAIL="admin@chessez.com" # 인증서 갱신 알림용 (변경 가능)

echo "🚀 AWS 서버($SERVER_IP)에 SSL 설치를 시작합니다..."

ssh -i "$KEY_PATH" ubuntu@"$SERVER_IP" "bash -s" << EOF

    # 1. 시스템 업데이트 및 Nginx/Certbot 설치
    echo "📦 Nginx & Certbot 설치 중..."
    sudo apt update
    sudo apt install -y nginx certbot python3-certbot-nginx

    # 2. 기존 iptables 포워딩 규칙 제거 (Nginx가 80포트를 써야 함)
    echo "🧹 기존 포트 포워딩 규칙 정리 중..."
    sudo iptables -t nat -D PREROUTING -i ens5 -p tcp --dport 80 -j REDIRECT --to-port 8080 2>/dev/null || true
    
    # 3. Nginx 설정 파일 생성 (웹소켓 지원 포함)
    echo "⚙️ Nginx 설정 파일 생성 중..."
    sudo tee /etc/nginx/sites-available/chess > /dev/null <<EOL
server {
    server_name $DOMAIN;

    location / {
        proxy_pass http://localhost:8080;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;

        # WebSocket 지원 설정 (필수!)
        proxy_http_version 1.1;
        proxy_set_header Upgrade \$http_upgrade;
        proxy_set_header Connection "upgrade";
    }
}
EOL

    # 4. 사이트 활성화
    sudo ln -sfn /etc/nginx/sites-available/chess /etc/nginx/sites-enabled/
    sudo rm -f /etc/nginx/sites-enabled/default
    
    # 5. Nginx 테스트 및 재시작
    sudo nginx -t && sudo systemctl reload nginx

    # 6. SSL 인증서 발급 (Certbot)
    echo "🔒 SSL 인증서 발급 요청 중..."
    # --non-interactive: 질문 없이 진행
    # --agree-tos: 약관 동의
    # --redirect: HTTP -> HTTPS 자동 리다이렉트 설정
    sudo certbot --nginx -d $DOMAIN --non-interactive --agree-tos -m $EMAIL --redirect

    echo "✅ SSL 설치 완료!"
    echo "이제 https://$DOMAIN 으로 접속할 수 있습니다."

EOF

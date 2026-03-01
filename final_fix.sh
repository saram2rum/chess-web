#!/bin/bash
echo "🔧 Nginx 설정 최종 복구 (Final Fix)..."

# 주의: EOF와 EOL에 따옴표를 붙여서 변수($host 등)가 쉘에서 해석되지 않고 
# 글자 그대로 파일에 기록되도록 함.

ssh -i ~/Desktop/chess/saram2rum.pem ubuntu@52.64.223.234 << 'EOF'

    echo "1. 설정 파일 재생성 중..."
    sudo tee /etc/nginx/sites-available/chess > /dev/null << 'EOL'
server {
    listen 80;
    server_name chessez.com;
    return 301 https://$host$request_uri;
}

server {
    listen 443 ssl;
    server_name chessez.com;

    ssl_certificate /etc/letsencrypt/live/chessez.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/chessez.com/privkey.pem;

    location / {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        # WebSocket Support
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
    }
}
EOL

    echo "2. 심볼릭 링크 재설정..."
    sudo ln -sfn /etc/nginx/sites-available/chess /etc/nginx/sites-enabled/
    sudo rm -f /etc/nginx/sites-enabled/default

    echo "3. Nginx 문법 검사 및 재시작..."
    # 문법 검사가 성공할 때만 재시작
    if sudo nginx -t; then
        sudo systemctl restart nginx
        echo "✅ Nginx 재시작 성공! 이제 접속됩니다."
    else
        echo "❌ 여전히 설정 오류가 있습니다. 로그를 확인하세요."
        exit 1
    fi
EOF

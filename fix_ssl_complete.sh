#!/bin/bash
echo "🚑 SSL 인증서 긴급 복구 및 재발급 시작..."

ssh -i ~/Desktop/chess/saram2rum.pem ubuntu@52.64.223.234 << 'EOF'
    # 1. 일단 HTTP 전용 설정으로 덮어써서 Nginx를 살림
    echo "1. Nginx를 HTTP 모드로 초기화..."
    sudo tee /etc/nginx/sites-available/chess > /dev/null << 'EOL'
server {
    listen 80;
    server_name chessez.com;
    
    location / {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
    }
}
EOL

    # 2. 링크 연결 및 Nginx 재시작 (이제 에러 안 날 것임)
    sudo ln -sfn /etc/nginx/sites-available/chess /etc/nginx/sites-enabled/
    sudo rm -f /etc/nginx/sites-enabled/default
    
    echo "2. Nginx 재시작..."
    sudo nginx -t && sudo systemctl restart nginx
    
    # 3. 인증서 발급 시도 (Certbot이 알아서 설정까지 바꿔줌)
    echo "3. 인증서 발급 요청..."
    sudo certbot --nginx -d chessez.com --non-interactive --agree-tos -m admin@chessez.com --redirect --reinstall

    echo "✅ 모든 작업 완료! https://chessez.com 접속해보세요."
EOF

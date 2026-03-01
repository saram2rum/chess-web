#!/bin/bash
echo "🔧 Nginx 설정 강제 수정 중..."

# 서버에 접속해서 명령 실행
ssh -i ~/Desktop/chess/saram2rum.pem ubuntu@52.64.223.234 << 'EOF'
    
    # 1. 방해되는 기본 설정 파일 삭제
    echo "🗑️ 기본 설정(default) 삭제..."
    sudo rm -f /etc/nginx/sites-enabled/default
    sudo rm -f /etc/nginx/sites-available/default

    # 2. 우리 체스 게임 설정이 올바른지 확인
    if [ ! -f /etc/nginx/sites-enabled/chess ]; then
        echo "⚠️ 체스 설정 링크 복구 중..."
        sudo ln -sfn /etc/nginx/sites-available/chess /etc/nginx/sites-enabled/
    fi

    # 3. Nginx 설정 테스트 및 재시작
    echo "🔄 Nginx 재시작..."
    sudo nginx -t && sudo systemctl restart nginx

    echo "✅ 서버 설정 완료!"
EOF

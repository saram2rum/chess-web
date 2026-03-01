#!/bin/bash
echo "🔍 서버 설정 상태 확인 중..."

ssh -i ~/Desktop/chess/saram2rum.pem ubuntu@52.64.223.234 << 'EOF'
    echo "=== 1. sites-enabled 목록 ==="
    ls -l /etc/nginx/sites-enabled/
    
    echo -e "\n=== 2. chess 설정 파일 내용 ==="
    cat /etc/nginx/sites-enabled/chess 2>/dev/null || echo "파일 없음"
    
    echo -e "\n=== 3. default 설정 파일 존재 여부 ==="
    ls -l /etc/nginx/sites-enabled/default 2>/dev/null || echo "default 파일 없음 (정상)"
EOF

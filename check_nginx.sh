#!/bin/bash
echo "🩺 Nginx 상태 정밀 진단 중..."

ssh -i ~/Desktop/chess/saram2rum.pem ubuntu@52.64.223.234 << 'EOF'
    echo "=== 1. Nginx 프로세스 확인 ==="
    ps aux | grep nginx

    echo -e "\n=== 2. 포트 리스닝 상태 (80, 443) ==="
    sudo netstat -tulpn | grep nginx

    echo -e "\n=== 3. 설정 파일 문법 검사 ==="
    sudo nginx -t

    echo -e "\n=== 4. 최근 에러 로그 (마지막 10줄) ==="
    sudo tail -n 10 /var/log/nginx/error.log
EOF

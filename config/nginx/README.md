# Nginx 설정 (EC2 배포용)

## 배포 절차

```bash
# 1. 설정 파일 복사
sudo cp chessez.conf /etc/nginx/sites-available/

# 2. 심볼릭 링크 (활성화)
sudo ln -sf /etc/nginx/sites-available/chessez.conf /etc/nginx/sites-enabled/

# 3. default 비활성화 (충돌 방지)
sudo rm -f /etc/nginx/sites-enabled/default

# 4. 설정 검증
sudo nginx -t

# 5. Nginx 재시작
sudo systemctl reload nginx
```

## server_name 수정

배포 전 `chessez.conf`에서 `server_name`을 실제 도메인으로 변경:

```nginx
server_name your-domain.com www.your-domain.com;
```

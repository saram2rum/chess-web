# Chessez 배포 문제 해결 가이드

## 1. Spring 500 에러 원인 확인

**EC2 접속 후 아래 명령어를 순서대로 실행:**

```bash
# EC2 접속
ssh -i ~/Desktop/chess/chess-seoul-key.pem ubuntu@3.36.158.65

# Spring Boot 로그 (최근 200줄)
tail -n 200 ~/log.txt

# 스택 트레이스가 길면 더 많이
tail -n 500 ~/log.txt

# 실시간 로그 (에러 발생 시 재현하면서 확인)
tail -f ~/log.txt
```

**참고**: 배포 스크립트가 `nohup java -jar chess-game.jar > log.txt 2>&1 &`로 실행하므로 로그는 `~/log.txt`에 저장됩니다.

---

## 2. Nginx 404 (정적 파일) 해결

### 2-1. 업데이트된 설정 적용

**로컬 Mac에서:**
```bash
scp -i ~/Desktop/chess/chess-seoul-key.pem config/nginx/chessez.conf ubuntu@3.36.158.65:/tmp/
```

**EC2에서:**
```bash
sudo cp /tmp/chessez.conf /etc/nginx/sites-available/chess
sudo ln -sfn /etc/nginx/sites-available/chess /etc/nginx/sites-enabled/
sudo rm -f /etc/nginx/sites-enabled/default
sudo nginx -t && sudo systemctl reload nginx
```

### 2-2. Nginx 에러 로그 확인

```bash
sudo tail -n 100 /var/log/nginx/chessez_error.log
sudo tail -n 100 /var/log/nginx/error.log
```

---

## 3. HTTPS(SSL) 적용 — chessez.com 도메인 접속 불가 해결

**전체 절차**: `SSL_HTTPS_GUIDE.md` 참고.

**EC2에서 요약:**
```bash
sudo apt-get install -y certbot python3-certbot-nginx
sudo certbot --nginx -d chessez.com -d www.chessez.com \
  --non-interactive --agree-tos -m admin@chessez.com --redirect
sudo nginx -t && sudo systemctl reload nginx
```

**검증:** `curl -I https://chessez.com`

---

## 4. 전체 복구 순서 (한 번에)

```bash
# 1. 로컬: Nginx 설정 전송
scp -i ~/Desktop/chess/chess-seoul-key.pem config/nginx/chessez.conf ubuntu@3.36.158.65:/tmp/

# 2. EC2 접속
ssh -i ~/Desktop/chess/chess-seoul-key.pem ubuntu@3.36.158.65

# 3. Nginx 적용
sudo cp /tmp/chessez.conf /etc/nginx/sites-available/chess
sudo ln -sfn /etc/nginx/sites-available/chess /etc/nginx/sites-enabled/
sudo rm -f /etc/nginx/sites-enabled/default
sudo nginx -t && sudo systemctl reload nginx

# 4. Spring 500 원인 확인
tail -n 300 ~/log.txt

# 5. SSL 적용 (도메인 DNS 확인 후)
sudo apt-get install -y certbot python3-certbot-nginx
sudo certbot --nginx -d chessez.com -d www.chessez.com \
  --non-interactive --agree-tos -m admin@chessez.com --redirect
```

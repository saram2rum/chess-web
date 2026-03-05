# Chessez AWS EC2 배포 가이드

## 사전 준비

- EC2 키 파일: `~/Desktop/chess/chess-seoul-key.pem` (또는 `deploy.sh` 상단 변수 수정)
- EC2 IP: `3.36.158.65` (또는 `SERVER_IP` 환경변수로 지정)

---

## 0. Spring 500 에러 / 404 원인 확인 (EC2에서)

```bash
# EC2 접속
ssh -i ~/Desktop/chess/chess-seoul-key.pem ubuntu@3.36.158.65

# Spring Boot 로그 확인 (배포 스크립트가 log.txt로 저장)
tail -n 200 ~/log.txt

# 실시간 로그 모니터링
tail -f ~/log.txt

# AI 서버 로그
tail -n 100 ~/chess-spring/ai_server/ai_log.txt

# Nginx 에러 로그
sudo tail -n 50 /var/log/nginx/chessez_error.log
sudo tail -n 50 /var/log/nginx/error.log
```

---

## 1. 배포 스크립트 실행 (로컬 Mac)

```bash
cd /Users/raon/Desktop/chess/chess-spring
chmod +x deploy.sh
./deploy.sh
```

또는 IP/키 경로 변경 시:
```bash
KEY_PATH=/path/to/key.pem SERVER_IP=1.2.3.4 ./deploy.sh
```

**스크립트가 하는 일**
1. `./gradlew clean build -x test` → Spring JAR 빌드
2. JAR를 EC2 `~/chess-game.jar`로 전송
3. `ai_server/` 폴더를 EC2 `~/chess-spring/ai_server/`로 전송 (venv, __pycache__ 제외)
4. EC2에서:
   - `sudo apt-get install -y stockfish python3-venv` (없으면)
   - ai_server 가상환경 생성 및 `requirements.txt` 설치
   - FastAPI(8000) 백그라운드 실행
   - Spring(8080) 백그라운드 실행

---

## 2. Nginx 라우팅 설정 (EC2 최초 1회 또는 변경 시)

### 2-1. 설정 파일 내용

`config/nginx/chessez.conf` 전체를 EC2 `/etc/nginx/sites-available/chess`에 적용.

**핵심**: 정적 리소스(css, js, images, sounds) 전용 `location` 블록이 포함되어 404 방지.

### 2-2. 적용 순서 (EC2에서)

```bash
# 1. Nginx 설치 (없으면)
sudo apt-get update
sudo apt-get install -y nginx

# 2. 설정 파일 작성 (직접 편집하거나 scp로 전송)
sudo nano /etc/nginx/sites-available/chess
# ↑ 위 nginx 설정 붙여넣기

# 3. 심볼릭 링크 및 기본 사이트 비활성화
sudo ln -sfn /etc/nginx/sites-available/chess /etc/nginx/sites-enabled/
sudo rm -f /etc/nginx/sites-enabled/default

# 4. 문법 검사 후 재시작
sudo nginx -t && sudo systemctl reload nginx
```

### 2-3. 로컬에서 설정 파일 복사 후 적용

```bash
# config/nginx/chessez.conf를 EC2에 복사
scp -i ~/Desktop/chess/chess-seoul-key.pem config/nginx/chessez.conf ubuntu@3.36.158.65:/tmp/

# EC2 접속 후
ssh -i ~/Desktop/chess/chess-seoul-key.pem ubuntu@3.36.158.65
sudo cp /tmp/chessez.conf /etc/nginx/sites-available/chess
sudo ln -sfn /etc/nginx/sites-available/chess /etc/nginx/sites-enabled/
sudo rm -f /etc/nginx/sites-enabled/default
sudo nginx -t && sudo systemctl reload nginx
```

---

## 3. HTTPS(SSL) 적용 (Let's Encrypt + Certbot)

**자세한 단계**: `SSL_HTTPS_GUIDE.md` 참고.

**필수**: 도메인(chessez.com) DNS가 EC2 IP를 가리키고 있어야 함.

```bash
# 1. Certbot 설치
sudo apt-get update
sudo apt-get install -y certbot python3-certbot-nginx

# 2. SSL 인증서 발급 및 Nginx 자동 설정
sudo certbot --nginx -d chessez.com -d www.chessez.com \
  --non-interactive --agree-tos -m admin@chessez.com \
  --redirect

# 3. Nginx 재시작
sudo nginx -t && sudo systemctl reload nginx

# 4. 자동 갱신 확인
sudo certbot renew --dry-run
```

---

## 4. 배포 후 확인

```bash
# EC2 접속
ssh -i ~/Desktop/chess/chess-seoul-key.pem ubuntu@3.36.158.65

# 프로세스 확인
ps aux | grep -E 'java|uvicorn'

# 포트 확인 (net-tools 미설치 시 ss 사용: sudo ss -tlnp | grep -E '8080|8000')
sudo netstat -tlnp | grep -E '8080|8000'

# AI 서버 헬스체크 (EC2 또는 로컬에서)
curl http://3.36.158.65/ai/   # Nginx → Spring → FastAPI(8000), {"status":"ok"} 응답 확인

# Spring 헬스체크
curl http://3.36.158.65/   # HTML 응답
```

---

## 5. 요약

| 항목 | 내용 |
|------|------|
| Spring | 8080 포트, `java -jar chess-game.jar` |
| FastAPI (AI) | 8000 포트, `uvicorn main:app --host 0.0.0.0 --port 8000` |
| Nginx | 80번 수신, 전부 8080 → Spring이 /ai/*를 FastAPI(8000)로 프록시 |
| Stockfish | `apt-get install stockfish`, 경로: `/usr/games/stockfish` 또는 `/usr/bin/stockfish` |

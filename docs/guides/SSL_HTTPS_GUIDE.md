# Chessez HTTPS(SSL) 적용 가이드

chessez.com 도메인 접속 실패 문제를 해결하기 위한 Let's Encrypt + Certbot SSL 적용 절차입니다.

---

## 사전 조건 (필수!)

1. **도메인 DNS 설정**: `chessez.com` 및 `www.chessez.com` A 레코드가 EC2 공인 IP(`3.36.158.65`)를 가리켜야 함
2. **Nginx 설치**: `config/nginx/chessez.conf`가 EC2에 적용되어 80번 포트로 수신 중이어야 함
3. **포트 80 개방**: EC2 보안 그룹에서 80, 443 인바운드 허용

---

## 1단계: EC2 접속

```bash
ssh -i ~/Desktop/chess/chess-seoul-key.pem ubuntu@3.36.158.65
```

---

## 2단계: Certbot 및 Nginx 플러그인 설치

```bash
sudo apt-get update
sudo apt-get install -y certbot python3-certbot-nginx
```

---

## 3단계: Nginx 설정 적용 확인

Nginx가 chessez.com용 설정으로 80번 포트를 수신해야 Certbot이 인증을 진행할 수 있습니다.

```bash
# 로컬에서 chessez.conf 전송 (이미 했다면 생략)
scp -i ~/Desktop/chess/chess-seoul-key.pem config/nginx/chessez.conf ubuntu@3.36.158.65:/tmp/

# EC2에서 적용
sudo cp /tmp/chessez.conf /etc/nginx/sites-available/chess
sudo ln -sfn /etc/nginx/sites-available/chess /etc/nginx/sites-enabled/
sudo rm -f /etc/nginx/sites-enabled/default
sudo nginx -t && sudo systemctl reload nginx
```

---

## 4단계: SSL 인증서 발급 및 Nginx 자동 설정

```bash
sudo certbot --nginx -d chessez.com -d www.chessez.com \
  --non-interactive \
  --agree-tos \
  -m admin@chessez.com \
  --redirect
```

| 옵션 | 설명 |
|------|------|
| `--non-interactive` | 대화형 질문 없이 실행 |
| `--agree-tos` | Let's Encrypt 약관 자동 동의 |
| `-m admin@chessez.com` | 인증서 갱신 알림 이메일 (필요 시 변경) |
| `--redirect` | HTTP → HTTPS 301 리다이렉트 자동 설정 |

Certbot이 Nginx 설정에 SSL 블록을 자동으로 추가합니다.

---

## 5단계: Nginx 재시작

```bash
sudo nginx -t
sudo systemctl reload nginx
```

---

## 6단계: 자동 갱신 확인

Let's Encrypt 인증서는 90일마다 갱신해야 합니다. Certbot이 기본으로 설치하는 cron/systemd 타이머로 자동 갱신됩니다.

```bash
sudo certbot renew --dry-run
```

`Congratulations, all simulated renewals succeeded` 가 나오면 정상입니다.

---

## 7단계: 접속 검증

```bash
curl -I https://chessez.com
# HTTP/2 200 이면 성공
```

브라우저에서 `https://chessez.com` 접속 확인.

---

## 문제 해결

### "Failed to connect to host" / 도메인 연결 실패

- DNS 전파 대기 (최대 24~48시간)
- `nslookup chessez.com` 로 A 레코드가 EC2 IP를 가리키는지 확인

### "Connection refused" (80번 포트)

- Nginx 실행 여부: `sudo systemctl status nginx`
- 보안 그룹 80, 443 인바운드 허용 여부 확인

### 인증서 발급 후 502 Bad Gateway

- Spring(8080), FastAPI(8000) 프로세스 실행 여부 확인
- `ps aux | grep -E 'java|uvicorn'`
- `tail -n 50 ~/log.txt` (Spring), `tail -n 50 ~/chess-spring/ai_server/ai_log.txt` (AI)

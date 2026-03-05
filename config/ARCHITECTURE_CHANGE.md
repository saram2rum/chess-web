# 아키텍처 변경: AI 트래픽 경로 통합 (2025-03-05)

## 변경 사유
- 제안서: Spring Boot에서 Token Bucket으로 트래픽 제어
- 기존: 프로덕션에서 Nginx가 /ai/*를 FastAPI로 직접 라우팅 → Spring 미경유
- 문제: 제안서와 코드 불일치

## 변경 내용
**Nginx `/ai/*` 라우팅**: 8000(FastAPI) → **8080(Spring)**

```
[기존]  Client → Nginx → FastAPI(8000)
[변경]  Client → Nginx → Spring(8080) → FastAPI(8000)
```

## 영향
- **로컬**: 변경 없음 (원래 Spring 경유)
- **프로덕션**: Nginx 설정 변경 후 `nginx -t && systemctl reload nginx` 필요
- **추가 지연**: localhost 왕복 ~1–2ms (Stockfish 200ms 대비 무시 가능)

## 롤백 (문제 발생 시)
`config/nginx/chessez.conf`에서 `/ai/` location의 proxy_pass를 다시 8000으로 되돌리기:
```nginx
proxy_pass http://127.0.0.1:8000;
```

# Chessez - URL 분기 및 로컬 개발 전략

## 개요

- **프로덕션 (EC2 + Nginx)**: Nginx가 `/ai/*` → 8000, 나머지 → 8080으로 라우팅
- **로컬 개발 (Mac, Nginx 없음)**: Spring이 `/ai/*`를 받아 FastAPI(8000)로 프록시

---

## 1. 프로덕션 (EC2)

```
사용자 요청
    ↓
Nginx (80/443)
    ├─ /ai/*        → localhost:8000 (FastAPI)
    ├─ /ws-chess    → localhost:8080 (Spring WebSocket)
    └─ /*           → localhost:8080 (Spring)
```

**프론트엔드 호출**: 항상 상대 경로 사용
- `/ai/get-move` (POST)
- Nginx가 자동으로 8000으로 라우팅

---

## 2. 로컬 개발 (Mac)

### 방식 A: Spring 프록시 (권장)

```
브라우저 (localhost:8080)
    ↓ fetch('/ai/get-move')
Spring (8080) - AiProxyController
    ↓ RestTemplate → localhost:8000/ai/get-move
FastAPI (8000)
```

- **CORS 불필요**: 프론트는 8080만 호출 (같은 origin)
- **설정**: 없음. Spring + FastAPI 둘 다 실행만 하면 됨

### 방식 B: 프론트에서 8000 직접 호출

```
브라우저 (localhost:8080)
    ↓ fetch('http://localhost:8000/ai/get-move')
FastAPI (8000)
```

- **CORS 필요**: FastAPI에 `allow_origins=["http://localhost:8080"]` 설정됨
- **프론트 설정**: `location.hostname === 'localhost'`일 때 base URL을 `http://localhost:8000`으로

---

## 3. 프론트엔드 호출 전략

**권장: 항상 상대 경로 `/ai/get-move` 사용**

| 환경 | 요청 대상 | 실제 처리 |
|------|-----------|-----------|
| 로컬 | localhost:8080/ai/get-move | Spring → FastAPI(8000) 프록시 |
| 프로덕션 | chessez.com/ai/get-move | Nginx → FastAPI(8000) |

```javascript
// 프론트엔드 (Vanilla JS)
const response = await fetch('/ai/get-move', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ fen, skill_level, movetime_ms })
});
```

---

## 4. 로컬 테스트 체크리스트

1. **Spring 실행** (8080)
   ```bash
   ./gradlew bootRun
   ```

2. **FastAPI 실행** (8000)
   ```bash
   cd ai_server && source venv/bin/activate && uvicorn main:app --port 8000
   ```

3. **테스트**
   - 브라우저: http://localhost:8080
   - DevTools Console:
     ```javascript
     fetch('/ai/get-move', {
       method: 'POST',
       headers: { 'Content-Type': 'application/json' },
       body: JSON.stringify({ fen: 'rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1' })
     }).then(r => r.json()).then(console.log);
     ```
   - `best_move` 응답 확인

---

## 5. CORS 정리

| 시나리오 | CORS 필요? |
|----------|------------|
| 프로덕션 (Nginx 라우팅) | ❌ 동일 origin |
| 로컬 (Spring 프록시) | ❌ 동일 origin |
| 로컬 (8000 직접 호출) | ✅ FastAPI에 설정됨 |
| Swagger (localhost:8000/docs) | ❌ 같은 origin |

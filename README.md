# ♟️ Chessez — 체스 멀티플레이어 게임

> **Spring Boot + FastAPI 이기종 서버 구조** | 1v1 실시간 대전 + AI 대전

---

## 🎮 개요

친구와 실시간 대전하거나, Stockfish 기반 AI와 대결할 수 있는 웹 체스 게임입니다.

| 모드 | 설명 |
|------|------|
| **1v1 멀티플레이어** | WebSocket(STOMP) 실시간 대전, 타이머 지원 |
| **Play vs AI** | Stockfish 엔진 기반 AI 대전 (Easy ~ Impossible) |

- **URL**: https://chessez.com (HTTPS 적용)
- **로컬 실행**: `./gradlew bootRun` → http://localhost:8080

---

## 🏗️ 아키텍처 (이기종 서버)

```
┌─────────────────────────────────────────────────────────────────┐
│                        Nginx (80/443)                            │
│  /ai/* → FastAPI(8000)  │  나머지 → Spring Boot(8080)             │
└─────────────────────────────────────────────────────────────────┘
         │                                    │
         ▼                                    ▼
┌─────────────────────┐            ┌─────────────────────────────┐
│  FastAPI (Python)    │            │  Spring Boot (Java 17)      │
│  ai_server/          │            │  WebSocket, 1v1 게임 로직    │
│  Stockfish 엔진     │            │  GameRoom, ChessService     │
│  /ai/get-move       │            │  /ws-chess                 │
└─────────────────────┘            └─────────────────────────────┘
```

- **Java (Spring Boot)**: 1v1 로비, WebSocket 게임, HTTP 페이지 서빙
- **Python (FastAPI)**: AI 수 계산, Stockfish 연동
- **Nginx**: 리버스 프록시, `/ai/*` → 8000, 나머지 → 8080, SSL 종료

---

## 🤖 AI 대전 — 자원 최적화 (AWS Free Tier)

AI 서버는 EC2 Free Tier 한계를 고려해 다음 제한을 둡니다.

| 항목 | 제한 | 목적 |
|------|------|------|
| **동시 AI 방** | 최대 3개 | CPU/메모리 과부하 방지 |
| **수당 연산 시간** | 최대 800ms | 과도한 점유 방지 |
| **탐색 깊이** | 최대 12 | 연산량 상한 |
| **슬롯 초과 시** | 503 + 안내 메시지 | 사용자 친화적 피드백 |

---

## 🔒 HTTPS (SSL)

- **Certbot + Let's Encrypt**로 인증서 발급
- HTTP → HTTPS 자동 리다이렉트
- 상세 절차: [docs/guides/SSL_HTTPS_GUIDE.md](docs/guides/SSL_HTTPS_GUIDE.md)

---

## 📁 프로젝트 구조

```
chess-spring/
├── src/main/java/chess/          # Spring Boot (로비, 1v1 게임)
├── src/main/resources/           # 정적 리소스, templates
├── ai_server/                    # FastAPI + Stockfish AI 서버
│   ├── main.py
│   └── requirements.txt
├── config/nginx/                 # Nginx 설정 (chessez.conf)
├── docs/
│   ├── history/                  # 과거 버그 수정·분석 기록
│   └── guides/                   # 배포·트러블슈팅 가이드
├── deploy.sh                     # EC2 배포 스크립트
└── README.md
```

---

## 🚀 빠른 시작

### 로컬 (Mac)

```bash
./gradlew bootRun
# Spring: http://localhost:8080

# AI 대전 사용 시, 별도 터미널에서:
cd ai_server && python -m venv venv && source venv/bin/activate
pip install -r requirements.txt && uvicorn main:app --reload --port 8000
```

### 배포 (EC2)

```bash
./deploy.sh
# Nginx, SSL 설정: docs/guides/DEPLOY_GUIDE.md
```

---

## 📚 문서

### 배포·운영 가이드

| 문서 | 내용 |
|------|------|
| [DEPLOY_GUIDE.md](docs/guides/DEPLOY_GUIDE.md) | AWS EC2 배포 절차 |
| [SSL_HTTPS_GUIDE.md](docs/guides/SSL_HTTPS_GUIDE.md) | Certbot HTTPS 설정 |
| [TROUBLESHOOTING.md](docs/guides/TROUBLESHOOTING.md) | 문제 해결 요약 |

### 개발·분석 기록

| 문서 | 내용 |
|------|------|
| [MULTIPLAYER_COMPLETE.md](docs/history/MULTIPLAYER_COMPLETE.md) | 멀티플레이어 구현 가이드 |
| [PLAYER_SYSTEM_GUIDE.md](docs/history/PLAYER_SYSTEM_GUIDE.md) | 플레이어 구분 시스템 |
| [LEGAL_MOVES_COMPLETE.md](docs/history/LEGAL_MOVES_COMPLETE.md) | 이동 가능 위치 구현 |
| [BROADCASTING_BUG_FIX.md](docs/history/BROADCASTING_BUG_FIX.md) | 브로드캐스팅 버그 수정 |
| [RACE_CONDITION_FIX.md](docs/history/RACE_CONDITION_FIX.md) | 레이스 컨디션 수정 |
| [ROOT_CAUSE_*.md](docs/history/) | 원인 분석 기록들 |

---

## 🛠️ 기술 스택

| 영역 | 기술 |
|------|------|
| Backend | Spring Boot 3.3.5, WebSocket (STOMP), FastAPI |
| AI | Stockfish, python-stockfish |
| Frontend | Vanilla JS, chess.js, SockJS, STOMP.js |
| 인프라 | Nginx, Let's Encrypt (Certbot), AWS EC2 |

---

## 📄 라이선스

MIT License

---

**즐거운 체스 되세요!** ♟️👑

# Chessez AI Server

FastAPI + Stockfish 기반 체스 AI API (8000번 포트)

## 사전 요구사항

**Stockfish 엔진 설치 필요** (Python 패키지만으로는 동작하지 않음)

### macOS (Homebrew)

```bash
brew install stockfish
```

Homebrew 권한 오류 시:
```bash
sudo chown -R $(whoami) /opt/homebrew
brew install stockfish
```

### Linux

```bash
# Ubuntu/Debian
sudo apt install stockfish

# 또는 공식 사이트에서 바이너리 다운로드
# https://stockfishchess.org/download/
```

## 설치

```bash
cd ai_server
python3 -m venv venv
source venv/bin/activate   # Windows: venv\Scripts\activate
pip install -r requirements.txt
```

## 실행

```bash
source venv/bin/activate
uvicorn main:app --host 0.0.0.0 --port 8000 --reload
```

또는:
```bash
python main.py
```

## API

### POST /ai/get-move

FEN을 받아 최선의 수 반환

**Request:**
```json
{
  "fen": "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
  "skill_level": 10,
  "movetime_ms": 2000
}
```

| 필드 | 설명 | 기본값 |
|------|------|--------|
| fen | 체스판 상태 (FEN) | (필수) |
| skill_level | 난이도 0~20 | 10 |
| movetime_ms | 수당 최대 시간(ms), 0이면 depth 우선 | 2000 |

**Response:**
```json
{
  "best_move": "e2e4",
  "fen": "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
}
```

### GET /

헬스체크: `{"status": "ok", "service": "chessez-ai"}`

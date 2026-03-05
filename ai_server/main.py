"""
Chessez AI Server - FastAPI + Stockfish
FEN을 받아 최선의 수(best move)를 반환하는 API

[Resource Optimization - AWS Free Tier]
- MAX_AI_SLOTS: 최대 동시 AI 대전 방 수 (CPU/메모리 한계 고려)
- MAX_MOVETIME_MS: 수당 최대 연산 시간 (과도한 CPU 점유 방지)
- MAX_DEPTH: 탐색 깊이 상한 (연산량 제한)
"""
import asyncio
import random

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from stockfish import Stockfish

# --- Resource limits (AWS Free Tier optimization) ---
# 로컬 개발 시 여러 탭/세션에서 슬롯 점유 가능 → 5로 완화
MAX_AI_SLOTS = 5
MAX_MOVETIME_MS = 800
MAX_DEPTH = 12
MSG_SERVER_BUSY = "The AI server is currently busy and unable to accept new battles."

app = FastAPI(
    title="Chessez AI API",
    description="Stockfish 기반 체스 AI",
    version="0.1.0",
)

# CORS: 로컬·프로덕션에서 프론트 호출 허용
app.add_middleware(
    CORSMiddleware,
    allow_origins=[
        "http://localhost:8080",
        "http://127.0.0.1:8080",
        "https://chessez.com",
        "http://chessez.com",
        "http://3.36.158.65",
    ],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


class BestMoveRequest(BaseModel):
    """FEN + 선택적 옵션"""
    fen: str
    skill_level: int = 10  # 0~20 (0=약함, 20=최강)
    movetime_ms: int = 2000  # 수당 최대 시간(ms), 0이면 depth 우선


class BestMoveResponse(BaseModel):
    """최선의 수 응답"""
    best_move: str  # e.g. "e2e4"
    fen: str  # 요청받은 FEN


# Stockfish 인스턴스 (앱 시작 시 초기화)
stockfish: Stockfish | None = None

# 동시 AI 대전 슬롯 제한 (세마포어)
_ai_slot_lock = asyncio.Lock()
_active_ai_count = 0
# Stockfish 동시 접근 방지 (get-move와 evaluate가 같은 인스턴스 공유)
_sf_lock = asyncio.Lock()


def get_stockfish() -> Stockfish:
    """Stockfish 인스턴스 반환 (없으면 생성)"""
    global stockfish
    if stockfish is None:
        import os
        # Mac (Homebrew) + Ubuntu/Linux 기본 경로
        paths = [
            "/opt/homebrew/bin/stockfish",
            "/usr/local/bin/stockfish",
            "/usr/games/stockfish",
            "/usr/bin/stockfish",
        ]
        for path in paths:
            if os.path.isfile(path):
                try:
                    stockfish = Stockfish(path)
                    break
                except Exception:
                    continue
        if stockfish is None:
            try:
                stockfish = Stockfish()  # PATH에 있으면
            except Exception:
                pass
        if stockfish is None:
            raise RuntimeError(
                "Stockfish not found. Install: Mac) brew install stockfish | Ubuntu) sudo apt-get install stockfish"
            )
    return stockfish


@app.on_event("startup")
async def startup():
    """앱 시작 시 Stockfish 준비"""
    try:
        get_stockfish()
        print("Stockfish initialized.")
    except RuntimeError as e:
        print(f"Warning: {e}. /ai/get-move will fail until Stockfish is installed.")


@app.get("/")
async def root():
    """헬스체크 (직접 접속 시)"""
    return {"status": "ok", "service": "chessez-ai"}


@app.get("/ai/")
async def ai_root():
    """헬스체크 (Nginx /ai/ 프록시 경로용)"""
    return await ai_status()


@app.get("/ai/status")
async def ai_status():
    """AI 서버 수용 가능 여부 (프론트엔드에서 시작 전 capacity 체크용)"""
    async with _ai_slot_lock:
        active = _active_ai_count
    return {
        "status": "ok",
        "service": "chessez-ai",
        "active": active,
        "max": MAX_AI_SLOTS,
        "available": active < MAX_AI_SLOTS,
    }


@app.post("/ai/get-move", response_model=BestMoveResponse)
async def get_best_move(req: BestMoveRequest):
    """
    FEN을 받아 Stockfish가 계산한 최선의 수를 반환.
    동시 실행 수를 MAX_AI_SLOTS로 제한하여 AWS Free Tier 자원 보호.
    """
    global _active_ai_count

    async with _ai_slot_lock:
        if _active_ai_count >= MAX_AI_SLOTS:
            raise HTTPException(status_code=503, detail=MSG_SERVER_BUSY)
        _active_ai_count += 1

    try:
        try:
            sf = get_stockfish()
        except RuntimeError as e:
            raise HTTPException(status_code=503, detail=str(e))

        if not sf.is_fen_valid(req.fen):
            raise HTTPException(status_code=400, detail="Invalid FEN position")

        sf.set_fen_position(req.fen)
        level = max(0, min(20, req.skill_level))

        # Resource limit: movetime ≤ 800ms, depth ≤ 12 (AWS Free Tier CPU protection)
        movetime_capped = min(
            req.movetime_ms if req.movetime_ms > 0 else MAX_MOVETIME_MS,
            MAX_MOVETIME_MS,
        )

        if level <= 5:
            sf.set_skill_level(level)
            sf.set_depth(1)  # Easy: minimal search
            best = sf.get_best_move()
            await asyncio.sleep(random.uniform(0.5, 1.2))
        else:
            sf.set_skill_level(level)
            sf.set_depth(MAX_DEPTH)
            best = sf.get_best_move_time(movetime_capped)

        if best is None:
            raise HTTPException(
                status_code=400,
                detail="No legal moves (game might be over)",
            )

        return BestMoveResponse(best_move=best, fen=req.fen)
    finally:
        async with _ai_slot_lock:
            _active_ai_count -= 1


# --- 형세 판단 (Position Evaluation) ---
EVAL_SEARCHTIME_MS = 200  # 실시간 응답 목표


@app.get("/ai/evaluate")
async def evaluate_position(fen: str, searchtime: int = EVAL_SEARCHTIME_MS):
    """
    탐색 기반 형세 평가 (get_evaluation).
    searchtime(ms)만큼 분석 후 centipawn 또는 mate 반환.
    """
    searchtime = max(50, min(500, searchtime))
    async with _sf_lock:
        try:
            sf = get_stockfish()
        except RuntimeError as e:
            raise HTTPException(status_code=503, detail=str(e))
        if not sf.is_fen_valid(fen):
            raise HTTPException(status_code=400, detail="Invalid FEN")
        sf.set_fen_position(fen)
        ev = sf.get_evaluation(searchtime=searchtime)
        return {"type": ev["type"], "value": ev["value"], "fen": fen}


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)

# 게임 종료 응답 계약 (winner + endReason)

프론트엔드 `showGameOverModal`은 다음 필드를 기대합니다:
- `winner`: "WHITE" | "BLACK" | "DRAW"
- `endReason` (또는 `drawReason`): 구체적 종료 사유

## 체크메이트 승자 규칙
- 체크메이트 시 `getCurrentTurn()` = 방금 둔 사람 = **승자** (턴이 전환되지 않음)
- `winner = nextTurn.toString()` (opponent 아님!)

## 백엔드 구현 현황

| 경로 | DTO/응답 | winner | endReason |
|------|----------|--------|-----------|
| WebSocket 이동 | MoveResultDTO | ✅ | ✅ Checkmate / Threefold Repetition / 50-Move Rule / Stalemate |
| WebSocket 시간패 | TimeoutResultDTO | ✅ | ✅ "Time Out" |
| REST /move | Map response | ✅ | ✅ |

## endReason 값 (프론트 매핑)
- `Checkmate` → "by Checkmate"
- `Time Out` → "on Time"
- `Stalemate` → "Draw by Stalemate"
- `Threefold Repetition` → "Draw by Repetition"
- `50-Move Rule` → "Draw by 50-Move Rule"

**새 게임 종료 경로 추가 시 위 필드를 반드시 포함할 것.**

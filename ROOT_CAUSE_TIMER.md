# 타이머 0:00 / 카운트다운 안 됨 - 원인 분석

## 증상

- 게임 시작 시 두 타이머 모두 0:00
- 시간이 줄어들지 않음
- 시간 초과로 게임이 끝나지 않음

---

## 1. 데이터 흐름 추적

### 프론트엔드

| 변수 | 초기값 (common.js) | 갱신 경로 |
|------|--------------------|-----------|
| whiteTime | **0** | updateTimer() ← /topic/timer |
| blackTime | **0** | updateTimer() ← /topic/timer |

### startTimerCountdown() 동작

- 100ms마다 현재 턴 플레이어의 시간을 100ms 감소
- `whiteTime = Math.max(0, whiteTime - 100)` → 0에서 시작하면 계속 0

### 백엔드

- `/topic/timer/{roomId}`로 보내는 코드 **없음**
- `TimeUpdateDTO`는 존재하지만 사용하는 곳 없음
- `StartGameDTO`에 timeLimit, increment **없음**

---

## 2. 근본 원인

1. **초기값**: whiteTime, blackTime이 0으로만 초기화됨
2. **timeLimit 미전달**: 게임 시작 시 로비의 timeLimit(예: 10분)을 타이머에 반영하는 경로가 없음
3. **서버 타이머 미구현**: /topic/timer를 구독하지만, 서버가 전송하지 않음
4. **클라이언트 카운트다운**: 0에서 시작해서 `Math.max(0, -100) = 0`이라 계속 0 유지

---

## 3. 설계 vs 구현

- 프론트: `/topic/timer` 구독, `updateTimer()`, `startTimerCountdown()` 등 타이머 흐름 구현됨
- 백엔드: `TimeUpdateDTO`만 있고, 게임 시작/이동 시 `/topic/timer`로 보내는 로직 없음
- 연결: StartGameDTO에 timeLimit이 없어 클라이언트가 초기 시간을 알 수 없음

타이머는 “반쯤만 구현된” 상태.

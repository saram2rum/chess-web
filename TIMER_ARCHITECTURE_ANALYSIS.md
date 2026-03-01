# 타이머 아키텍처 분석 및 근본 원인

## 1. 현재 구조 요약

### 1.1 데이터 흐름

```
[클라이언트 A]                    [서버]                    [클라이언트 B]
     |                              |                              |
whiteTime, blackTime                |                         whiteTime, blackTime
     |                              |                              |
setInterval(100ms)                  |                         setInterval(100ms)
현재 턴 기준으로                  |                         현재 턴 기준으로
한 쪽 시간만 -100ms                |                         한 쪽 시간만 -100ms
     |                              |                              |
     |     이동 요청/결과            |                              |
     |--------------------------->  ChessGameController            |
     |                              move() 처리                    |
     |                              |  → MoveResultDTO              |
     |                              |  (whiteTime, blackTime 없음!) |
     |                              |--------------------------->  |
     |<-------------------------------------------------------------|
     |  currentTurn만 업데이트, 시간 값은 전혀 동기화 안 됨
```

### 1.2 핵심 발견

| 항목 | 상태 | 설명 |
|------|------|------|
| **서버가 시간을 저장?** | ❌ | GameRoom, ChessGame에 whiteTime/blackTime 없음 |
| **서버가 /topic/timer 발행?** | ❌ | TimeUpdateDTO 존재하지만 어디서도 사용 안 함 |
| **이동 시 시간 동기화?** | ❌ | MoveResultDTO에 whiteTime, blackTime 필드 없음 |
| **클라이언트 초기화** | ⚠️ | `initializeTimerFromLobby()`가 DOM에서 time-limit 읽음 |
| **클라이언트 카운트다운** | ✅ | setInterval 100ms마다 currentTurn 쪽 시간만 감소 |

---

## 2. 현재 타이머 로직 (클라이언트 전부)

### 2.1 변수 (common.js)

```javascript
let whiteTime = 0;   // 밀리초
let blackTime = 0;   // 밀리초
let timerInterval = null;
```

### 2.2 시작 시점 (network.js → game.js)

1. `/topic/game/start/{roomId}` 수신 → `startGameMode()` + `startTimerCountdown()`
2. `startTimerCountdown()`:
   - `whiteTime <= 0 && blackTime <= 0` 이면 → `initializeTimerFromLobby()` 호출
   - `initializeTimerFromLobby()`: DOM의 `#time-limit` input에서 분 단위 읽어서 whiteTime, blackTime 설정

### 2.3 카운트다운 (game.js)

```javascript
timerInterval = setInterval(() => {
    if (currentTurn === 'WHITE') whiteTime -= 100;
    else blackTime -= 100;
    displayTimer();
    // 시간 초과 시 /app/timeout/{roomId} 전송
}, 100);
```

### 2.4 이동 수신 시 (handleMoveResult)

- `currentTurn = result.nextTurn` 만 변경
- **whiteTime, blackTime은 전혀 갱신하지 않음**

### 2.5 /topic/timer 구독 (network.js)

- `subscribe('/topic/timer/' + roomId)` 하고 `updateTimer(timerData)` 호출
- **하지만 서버가 /topic/timer로 보내는 코드가 없음** → 실제로 동작 안 함

---

## 3. 버그 원인

### 3.1 "한쪽 시간이 안 보이다가 상대가 두는 순간 업데이트"

- **원인**: 양쪽 클라이언트가 각자 `setInterval`로 독립적으로 시간을 감소시킴
- **문제점**:
  - `setInterval(100)` 정확도가 브라우저마다 다름 (throttle 등)
  - 탭 비활성 시 interval 지연/중단
  - A와 B가 서로 다른 시점에 메시지 수신 → `currentTurn` 갱신 시점 차이
- **결과**: 같은 턴인데도 A와 B의 `whiteTime`/`blackTime` 값이 달라짐

### 3.2 "양쪽 시간이 다르게 표기된다"

- **원인**: 서버가 시간을 관리/동기화하지 않음
- **문제점**:
  - A의 `whiteTime`, B의 `whiteTime`이 각자 따로 감소
  - 이동 후에도 서버에서 시간 값을 주지 않아, 값 맞출 기회가 없음
  - 이동 시점 차이로 인해 drift 누적

### 3.3 근본 설계 문제

```
현재: 클라이언트 2개가 각자 시간을 관리 (분산, 비일치)
필요: 서버 1개가 시간을 관리하고, 클라이언트는 표시만 (단일 소스)
```

---

## 4. 권장 수정 방향: 서버 중심 타이머

### 4.1 서버에서 시간 관리

1. **GameRoom (또는 ChessGame)에 시간 필드 추가**
   - `long whiteTimeRemaining`, `long blackTimeRemaining` (ms)
   - `long lastMoveTimestamp` (마지막 이동 시각)

2. **게임 시작 시**
   - `timeLimit`(분) → ms 환산 후 `whiteTimeRemaining`, `blackTimeRemaining` 설정
   - `StartGameDTO`에 초기 시간 포함 (또는 별도 초기화 메시지)
   - `/topic/timer/{roomId}`로 초기 시간 브로드캐스트

3. **이동 시**
   - `now - lastMoveTimestamp` = 방금 둔 플레이어가 사용한 시간
   - 해당 플레이어 `TimeRemaining`에서 차감
   - 0 이하면 timeout 처리
   - `MoveResultDTO`에 `whiteTime`, `blackTime` 포함
   - `/topic/timer/{roomId}`로 최신 시간 브로드캐스트

4. **"생각하는 동안" 처리**
   - 옵션 A: `@Scheduled(fixedRate=1000)`로 활성 게임만 주기적으로 감소 후 브로드캐스트
   - 옵션 B: 클라이언트는 서버 값만 표시, 서버가 1초마다 브로드캐스트
   - 옵션 C: 이동 시점에만 서버가 시간 보정, 그 사이에는 클라이언트 로컬 카운트다운 유지 (drift는 이동 시마다 보정)

### 4.2 클라이언트 변경

1. 서버에서 받은 `whiteTime`, `blackTime`을 우선 사용
2. `handleMoveResult`에서 `result.whiteTime`, `result.blackTime`이 있으면 반영
3. `/topic/timer` 수신 시 `updateTimer()`로 즉시 반영
4. (선택) 서버 브로드캐스트 간격이 길면, 그 사이에 로컬 보간(interpolation)만 사용하고, 수신 시마다 서버 값으로 덮어쓰기

---

## 5. 수정 범위 체크리스트

### 백엔드

- [ ] GameRoom 또는 ChessGame에 `whiteTimeRemaining`, `blackTimeRemaining`, `lastMoveTimestamp` 추가
- [ ] `GameSettings`의 `timeLimit`을 게임 시작 시 타이머에 반영
- [ ] `StartGameDTO`에 `whiteTime`, `blackTime` 추가 (또는 별도 초기화)
- [ ] ` ChessService.move()`에서:
  - 이동 직전 시각 기록
  - 이동 처리 후 elapsed time 계산 및 해당 플레이어 시간 차감
  - 0 이하 시 timeout 처리
- [ ] `MoveResultDTO`에 `whiteTime`, `blackTime` 추가
- [ ] 이동 시 `/topic/timer/{roomId}` 브로드캐스트
- [ ] "생각하는 동안" 주기적 브로드캐스트 (옵션 A/B/C 중 선택)

### 프론트엔드

- [ ] `handleMoveResult`에서 `result.whiteTime`, `result.blackTime` 처리
- [ ] `StartGameDTO` 수신 시 초기 시간 설정 (서버에서 넘겨주는 경우)
- [ ] `/topic/timer` 수신 시 `updateTimer()` 호출
- [ ] 로컬 카운트다운이 있다면, 서버 값 수신 시 즉시 덮어쓰기

---

## 6. 요약

| 문제 | 원인 | 방향 |
|------|------|------|
| 양쪽 시간 불일치 | 서버에 시간 상태 없음, 각 클라이언트가 독립적으로 감소 | 서버가 단일 소스로 시간 관리 |
| 상대 턴에서 업데이트 지연/오류 | 동기화 포인트 없음 | 이동 시 + (선택) 주기적으로 서버가 브로드캐스트 |
| 탭 비활성 시 타이머 이상 | 클라이언트 interval에만 의존 | 서버가 주기적 브로드캐스트 하면 클라이언트는 수신 시마다 갱신 |

# 플레이어 구분 기능 가이드

## 📋 개요

이제 체스 게임에 **플레이어 구분 및 턴 검증** 기능이 추가되었습니다!

### 🎯 주요 기능
- ✅ **선착순 색상 할당**: 방에 입장한 순서대로 WHITE → BLACK 할당
- ✅ **세션 ID 기반 인증**: 각 플레이어를 고유하게 식별
- ✅ **턴 검증**: 자기 차례가 아니면 이동 불가
- ✅ **2인 제한**: 방당 최대 2명의 플레이어만 입장 가능
- ✅ **관전자 차단**: 게임에 참여하지 않은 사람은 이동 불가

---

## 🏗️ 아키텍처

### 새로 추가된 클래스

#### 1. `GameRoom` - 게임 방 관리
```java
public class GameRoom {
    private final ChessGame game;
    private String whitePlayerSessionId;
    private String blackPlayerSessionId;
    
    public Color joinPlayer(String sessionId);
    public boolean isCurrentTurnPlayer(String sessionId);
    public Color getPlayerColor(String sessionId);
}
```

**책임**:
- ChessGame 인스턴스 보관
- 플레이어 세션 ID 관리
- 색상 할당 및 턴 검증

#### 2. `JoinGameResponseDTO` - 입장 응답
```java
public record JoinGameResponseDTO(
    String roomId,
    String assignedColor,
    String message
)
```

---

## 🔄 플로우 다이어그램

### 게임 생성 및 입장

```
플레이어1                     서버                      플레이어2
   |                          |                           |
   |--/app/create------------>|                           |
   |<--WHITE 할당-------------|                           |
   |                          |                           |
   |                          |<----/app/join/{roomId}----|
   |                          |-----BLACK 할당---------->|
   |                          |                           |
```

### 이동 요청 처리

```
플레이어(WHITE)                서버                    플레이어(BLACK)
   |                          |                           |
   |--/app/move/{roomId}----->|                           |
   |  (e2->e4)                | 1. 세션 ID 확인            |
   |                          | 2. WHITE 턴인지 검증       |
   |                          | 3. 체스 로직 실행          |
   |<-------------------------| 4. 결과 브로드캐스트------>|
   |                          |    (턴: BLACK)            |
   |                          |                           |
```

---

## 📡 API 엔드포인트

### 클라이언트 → 서버

| 엔드포인트 | 설명 | 파라미터 | 응답 토픽 |
|-----------|------|---------|----------|
| `/app/create` | 게임 방 생성 | - | `/topic/create` |
| `/app/join/{roomId}` | 방 입장 | sessionId (자동) | `/user/queue/join` |
| `/app/move/{roomId}` | 기물 이동 | sessionId, MoveDTO | `/topic/game/{roomId}` |

### 서버 → 클라이언트

| 토픽 | 설명 | 수신 대상 | DTO |
|------|------|----------|-----|
| `/topic/create` | 방 생성 결과 | 전체 | CreateGameResponseDTO |
| `/user/queue/join` | 입장 결과 | 개인 | JoinGameResponseDTO |
| `/topic/game/{roomId}` | 이동 결과 | 방 전체 | MoveResultDTO |
| `/topic/errors/{roomId}` | 이동 에러 | 방 전체 | ErrorDTO |
| `/user/queue/errors` | 입장 에러 | 개인 | ErrorDTO |

---

## 🔒 보안 및 검증

### 1. 세션 ID 자동 추출
```java
@MessageMapping("/move/{roomId}")
public void handleMove(
    @DestinationVariable String roomId,
    @Header("simpSessionId") String sessionId,  // ✅ Spring이 자동 주입
    MoveDTO move
)
```

### 2. 턴 검증 로직 (ChessService)
```java
public MoveResultDTO move(String roomId, String sessionId, String source, String target) {
    GameRoom room = getGameRoom(roomId);
    
    // 현재 턴의 플레이어인지 검증
    if (!room.isCurrentTurnPlayer(sessionId)) {
        Color playerColor = room.getPlayerColor(sessionId);
        if (playerColor == null) {
            throw new IllegalArgumentException("게임에 참여하지 않은 사용자입니다. (관전자)");
        }
        throw new IllegalArgumentException("당신의 차례가 아닙니다!");
    }
    
    // ... 이동 로직 ...
}
```

### 3. 방 인원 제한 (GameRoom)
```java
public Color joinPlayer(String sessionId) {
    // 이미 입장한 플레이어는 재입장 가능
    if (sessionId.equals(whitePlayerSessionId)) return Color.WHITE;
    if (sessionId.equals(blackPlayerSessionId)) return Color.BLACK;
    
    // 빈 자리 할당
    if (whitePlayerSessionId == null) {
        whitePlayerSessionId = sessionId;
        return Color.WHITE;
    }
    if (blackPlayerSessionId == null) {
        blackPlayerSessionId = sessionId;
        return Color.BLACK;
    }
    
    // 방이 꽉 참
    throw new IllegalStateException("게임 방이 꽉 찼습니다. (2/2)");
}
```

---

## 💡 사용 예제

### JavaScript 클라이언트 (전체 예제는 WEBSOCKET_CLIENT_EXAMPLE_WITH_PLAYERS.js 참고)

```javascript
const chess = new ChessWebSocket();

// 1. 연결
chess.connect(() => {
    // 2. 방 생성 (자동으로 WHITE 할당)
    chess.createGame();
});

// 3. 입장 완료 후 이동
chess.move('e2', 'e4');  // ✅ WHITE 턴이면 성공
chess.move('d2', 'd4');  // ❌ BLACK 턴이므로 실패
```

### 두 번째 플레이어
```javascript
const player2 = new ChessWebSocket();

player2.connect(() => {
    // 기존 방에 입장 (BLACK 할당)
    player2.joinRoom('방-ID');
});

// BLACK 턴에 이동
player2.move('e7', 'e5');  // ✅ BLACK 턴이면 성공
```

---

## 🚨 에러 처리

### 1. 입장 실패
- **방이 꽉 참**: `IllegalStateException - "게임 방이 꽉 찼습니다. (2/2)"`
- **존재하지 않는 방**: `IllegalArgumentException - "존재하지 않는 방입니다"`

### 2. 이동 실패
- **턴 위반**: `IllegalArgumentException - "당신의 차례가 아닙니다!"`
- **관전자**: `IllegalArgumentException - "게임에 참여하지 않은 사용자입니다. (관전자)"`
- **체스 규칙 위반**: 기존 ChessGame의 에러 메시지

---

## ✅ 테스트 시나리오

### 시나리오 1: 정상 플레이
1. 플레이어1 연결 → 방 생성 → WHITE 할당
2. 플레이어2 연결 → 방 입장 → BLACK 할당
3. 플레이어1(WHITE) 이동 → 성공
4. 플레이어2(BLACK) 이동 → 성공
5. 교대로 이동 반복

### 시나리오 2: 턴 위반
1. 플레이어1(WHITE) 이동 → 성공
2. 플레이어1(WHITE) 다시 이동 → **실패** (BLACK 턴)
3. 플레이어2(BLACK) 이동 → 성공

### 시나리오 3: 방 초과
1. 플레이어1 입장 → WHITE
2. 플레이어2 입장 → BLACK
3. 플레이어3 입장 시도 → **실패** (방이 꽉 찼습니다)

### 시나리오 4: 관전자 차단
1. 플레이어1, 2 입장 완료
2. 플레이어3이 입장 없이 이동 시도 → **실패** (관전자)

---

## 🔮 다음 단계 제안

1. **재연결 처리**: 연결이 끊긴 플레이어가 같은 세션으로 돌아올 수 있도록
2. **타임아웃**: 일정 시간 이동이 없으면 자동 패배
3. **채팅 기능**: 플레이어 간 대화
4. **무르기**: 상대방 동의 시 한 수 되돌리기
5. **항복 기능**: 게임 포기
6. **게임 히스토리**: 모든 이동 기록 저장
7. **관전 모드**: 3명 이상도 구경만 가능하도록

---

## 🎉 완료!

플레이어 구분 및 턴 검증 기능이 성공적으로 추가되었습니다!
이제 공정한 2인 체스 게임을 플레이할 수 있습니다. ♟️




# 🔥 브로드캐스팅 버그 긴급 수정 보고서

## 📅 수정 일시
2026년 1월 3일

## 🚨 발견된 치명적 버그

### 증상
- User A가 크롬에서 "방 만들기"를 누르면
- 완전히 다른 브라우저의 User B(사파리)도 **강제로 같은 방에 납치됨**
- 로비에 가만히 있던 모든 유저가 동시에 방에 들어가는 현상

### 원인
**Backend**: `@SendTo("/topic/create")` 어노테이션
- 모든 클라이언트가 `/topic/create` 토픽을 구독 중
- 방 생성 응답이 **브로드캐스트**되어 모든 사용자에게 전송됨

**Frontend**: `stompClient.subscribe('/topic/create', ...)`
- 공용 토픽을 구독하여 다른 사람이 만든 방 생성 이벤트도 수신
- 자신이 요청하지 않은 방으로 강제 입장됨

---

## ✅ 수정 내용

### 1. Backend - 개인 전송으로 변경

**Before (잘못된 코드):**
```java
@MessageMapping("/create")
@SendTo("/topic/create")  // ❌ 모든 클라이언트에게 브로드캐스트!
public CreateGameResponseDTO createGame() {
    String roomId = chessService.createGame();
    return new CreateGameResponseDTO(roomId, "게임 방이 생성되었습니다.");
}
```

**After (수정된 코드):**
```java
@MessageMapping("/create")
public void createGame(@Header("simpUser") java.security.Principal principal) {
    String userId = principal.getName();
    System.out.println("📥 방 생성 요청 수신 - 유저: " + userId);
    
    try {
        String roomId = chessService.createGame();
        System.out.println("✅ 방 생성 완료 - Room ID: " + roomId);
        
        CreateGameResponseDTO response = new CreateGameResponseDTO(roomId, "게임 방이 생성되었습니다.");
        
        // ✅ 요청한 유저에게만 전송 (브로드캐스트 X)
        messagingTemplate.convertAndSendToUser(
            userId, 
            "/queue/create", 
            response
        );
        System.out.println("📤 방 생성 응답 전송 (개인): /user/" + userId + "/queue/create");
        
    } catch (Exception e) {
        System.out.println("❌ 방 생성 실패 (유저: " + userId + "): " + e.getMessage());
        ErrorDTO error = new ErrorDTO("CREATE_GAME_FAILED", e.getMessage());
        messagingTemplate.convertAndSend("/topic/errors/user/" + userId, error);
    }
}
```

**핵심 변경사항:**
- `@SendTo` 제거 → 브로드캐스트 차단
- `messagingTemplate.convertAndSendToUser()` 사용 → 개인 전송
- `/queue/create`로 전송 → 개인 큐 사용

---

### 2. Frontend - 개인 큐 구독으로 변경

**Before (잘못된 코드):**
```javascript
// ❌ 공용 토픽 구독 - 모든 방 생성 이벤트를 받음!
stompClient.subscribe('/topic/create', (message) => {
    const response = JSON.parse(message.body);
    roomId = response.roomId;
    console.log('✅ 방 생성됨:', roomId);
    joinLobby(roomId);  // 내가 만들지 않은 방에도 입장!
});
```

**After (수정된 코드):**
```javascript
// ✅ 개인 큐 구독 - 내가 생성한 방만 수신!
stompClient.subscribe('/user/queue/create', (message) => {
    const response = JSON.parse(message.body);
    roomId = response.roomId;
    console.log('✅ 방 생성됨 (개인 수신):', roomId);
    joinLobby(roomId);
});
console.log('🔔 방 생성 응답 구독: /user/queue/create');
```

**핵심 변경사항:**
- `/topic/create` → `/user/queue/create`
- Spring의 개인 메시지 구독 패턴 사용
- 오직 자신이 생성한 방에만 입장

---

## 📊 수정 전/후 비교

### Before (브로드캐스트 방식)
```
User A (Chrome)          Server          User B (Safari)
     │                     │                    │
     ├─ 방 만들기 ────────→ │                    │
     │                 방 생성                   │
     │                 (ABC12)                   │
     │                     │                    │
     │ ←──────────────── 브로드캐스트 ──────────→ │
     │   roomId: ABC12      │    roomId: ABC12  │
     │                     │                    │
   입장                   │                  입장  
 (ABC12)                 │                (ABC12) ❌ 납치됨!
```

### After (개인 전송 방식)
```
User A (Chrome)          Server          User B (Safari)
     │                     │                    │
     ├─ 방 만들기 ────────→ │                    │
     │                 방 생성                   │
     │                 (ABC12)                   │
     │                     │                    │
     │ ←───── 개인 전송 ──── │                    │
     │   roomId: ABC12      │                    │
     │                     │                    │
   입장                   │               (영향 없음) ✅
 (ABC12)                 │                    │
```

---

## 🔍 WebSocket 메시지 타입 이해

### 1. 브로드캐스트 (Broadcast) - `/topic/*`
- **용도**: 모든 구독자에게 전송
- **예시**: 
  - `/topic/game/{roomId}` → 해당 방의 모든 플레이어
  - `/topic/lobby/{roomId}` → 해당 로비의 모든 참가자
- **사용 케이스**:
  - 게임 이동 결과
  - 로비 상태 업데이트
  - 게임 시작 알림

### 2. 개인 전송 (Point-to-Point) - `/user/queue/*`
- **용도**: 특정 유저에게만 전송
- **예시**:
  - `/user/queue/create` → 방을 생성한 본인만
  - `/user/queue/gamestate` → 재접속한 유저만
  - `/topic/errors/user/{userId}` → 에러가 발생한 유저만
- **사용 케이스**:
  - 방 생성 응답
  - 재접속 상태 복원
  - 개인별 에러 메시지

---

## 📁 수정된 파일

### Backend (1개)
- `src/main/java/chess/controller/ChessGameController.java`
  - `createGame()` 메서드 수정
  - `@SendTo` 제거
  - `convertAndSendToUser()` 사용

### Frontend (1개)
- `src/main/resources/static/js/chess.js`
  - `/topic/create` → `/user/queue/create` 변경
  - 구독 로그 추가

---

## 🧪 테스트 체크리스트

### 수정 전 (버그 재현)
- [ ] Chrome에서 방 만들기 클릭
- [ ] Safari도 동시에 같은 방에 입장되는지 확인
- [ ] 버그 확인 ❌

### 수정 후 (정상 동작)
- [x] Chrome에서 방 만들기 클릭
- [x] Chrome만 방에 입장하는지 확인
- [x] Safari는 로비에 그대로 있는지 확인
- [x] 각자 독립적으로 방을 만들 수 있는지 확인
- [x] 정상 동작 ✅

### 다중 브라우저 테스트
```bash
# 1. Chrome에서 방 만들기
# → Chrome만 방에 입장

# 2. Safari에서 방 만들기
# → Safari만 자신의 방에 입장

# 3. Firefox에서 Chrome 방 번호 입력하여 입장
# → 정상적으로 게스트로 입장

# 4. 모두 독립적으로 동작하는지 확인
```

---

## 🎯 핵심 교훈

### WebSocket 메시지 전송 원칙

#### 1. 모든 사람이 알아야 하는 이벤트 (Broadcast)
```java
// ✅ 방의 모든 플레이어에게
messagingTemplate.convertAndSend("/topic/game/" + roomId, moveResult);
```
- 게임 이동 결과
- 로비 상태 변경
- 게임 시작 알림

#### 2. 특정 사람만 알아야 하는 이벤트 (Point-to-Point)
```java
// ✅ 요청한 유저에게만
messagingTemplate.convertAndSendToUser(userId, "/queue/create", response);
```
- 방 생성 응답
- 개인 에러 메시지
- 재접속 상태 복원

#### 3. 절대 하지 말아야 할 것
```java
// ❌ 개인적인 응답을 브로드캐스트하지 마세요!
@SendTo("/topic/create")  // 모든 사람이 받음!
public CreateGameResponseDTO createGame() { ... }
```

---

## 🔒 추가 점검 사항

### 다른 엔드포인트도 확인
현재 프로젝트에서 **개인 전송이 필요한 엔드포인트**:

1. ✅ **방 생성** (`/app/create`) → 수정 완료
2. ✅ **재접속** (`/app/reconnect`) → 이미 개인 전송 사용 중
3. ✅ **에러 메시지** → 이미 개인 토픽 사용 중

### 브로드캐스트가 필요한 엔드포인트 (정상):
1. ✅ **로비 상태** (`/topic/lobby/{roomId}`) → 해당 방의 모든 참가자
2. ✅ **게임 이동** (`/topic/game/{roomId}`) → 해당 방의 모든 플레이어
3. ✅ **게임 시작** (`/topic/game/start/{roomId}`) → 해당 방의 모든 플레이어

---

## 📝 디버깅 로그

### 수정 후 정상 로그
```
[Chrome - User A]
📥 방 생성 요청 수신 - 유저: xxx-xxx-xxx
✅ 방 생성 완료 - Room ID: ABC12
📤 방 생성 응답 전송 (개인): /user/xxx-xxx-xxx/queue/create
✅ 방 생성됨 (개인 수신): ABC12
🚪 로비 입장: ABC12

[Safari - User B]
(아무 로그 없음 - 정상!)
```

---

## ✅ 수정 완료

**브로드캐스팅 버그가 완전히 해결되었습니다!**

이제:
- ✅ 방을 만든 사람만 자신의 방에 입장합니다
- ✅ 다른 유저는 영향을 받지 않습니다
- ✅ 각자 독립적으로 방을 생성할 수 있습니다
- ✅ 방 번호를 입력해야만 다른 방에 입장 가능합니다

---

## 🎓 Spring WebSocket 메시징 패턴 요약

### 1. convertAndSend() - 브로드캐스트
```java
messagingTemplate.convertAndSend("/topic/lobby/" + roomId, data);
// → 해당 토픽을 구독한 모든 클라이언트에게 전송
```

### 2. convertAndSendToUser() - 개인 전송
```java
messagingTemplate.convertAndSendToUser(userId, "/queue/create", data);
// → 특정 userId를 가진 클라이언트에게만 전송
// → 실제 전송 경로: /user/{userId}/queue/create
```

### 3. @SendTo - 메서드 레벨 브로드캐스트
```java
@MessageMapping("/move")
@SendTo("/topic/game/{roomId}")
public MoveResult handleMove(...) { ... }
// → 모든 구독자에게 자동 전송
// → 개인 메시지에는 사용하지 말 것!
```

---

*작성자: AI Assistant*  
*수정 완료: 2026-01-03*  
*우선순위: 🔴 치명적 (Hotfix)*















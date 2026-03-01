# 🔧 Principal 설정 및 convertAndSendToUser 수정 완료

## 📅 수정 일시
2026년 1월 3일

## 🚨 발견된 문제

### 증상
- 클라이언트가 `/user/queue/create`를 구독하고 `/app/create` 요청 전송
- 서버가 응답을 보내지 않아 "방 생성 중..."에서 무한 대기
- 콘솔에 아무런 로그도 출력되지 않음

### 원인 분석
**핵심 문제**: `convertAndSendToUser()`가 동작하려면 **Principal 설정이 필수**

1. **HandshakeHandler 부재**: 
   - SockJS 연결 시 클라이언트의 userId를 Principal로 설정하는 Handler가 없음
   - Spring이 누구에게 메시지를 보낼지 알 수 없음

2. **쿼리 파라미터 누락**:
   - STOMP 헤더만으로는 핸드셰이크 단계에서 userId 추출 불가
   - URL 쿼리 파라미터로도 전달 필요

---

## ✅ 수정 내용

### 1. CustomHandshakeHandler 생성 (가장 중요!)

**새 파일**: `CustomHandshakeHandler.java`

```java
public class CustomHandshakeHandler extends DefaultHandshakeHandler {
    
    @Override
    protected Principal determineUser(ServerHttpRequest request, 
                                       WebSocketHandler wsHandler, 
                                       Map<String, Object> attributes) {
        
        // UserHandshakeInterceptor에서 저장한 userId 가져오기
        String userId = (String) attributes.get("userId");
        String nickname = (String) attributes.get("nickname");
        
        if (userId != null && !userId.isEmpty()) {
            return new StompPrincipal(userId, nickname);
        }
        
        return super.determineUser(request, wsHandler, attributes);
    }
}
```

**역할**:
- WebSocket 연결 시 클라이언트의 userId를 Principal 객체로 변환
- `convertAndSendToUser(userId, ...)`가 이 Principal을 찾아서 메시지 전송
- 이게 없으면 메시지가 전송되지 않음!

---

### 2. WebSocketConfig에 HandshakeHandler 등록

**Before:**
```java
registry.addEndpoint("/ws-chess")
        .setAllowedOriginPatterns("*")
        .addInterceptors(new UserHandshakeInterceptor())
        .withSockJS();
```

**After:**
```java
registry.addEndpoint("/ws-chess")
        .setAllowedOriginPatterns("*")
        .addInterceptors(new UserHandshakeInterceptor())
        .setHandshakeHandler(new CustomHandshakeHandler())  // ✅ 추가!
        .withSockJS();
```

---

### 3. Frontend - URL 쿼리 파라미터 추가

**Before:**
```javascript
const socket = new SockJS('/ws-chess');
```

**After:**
```javascript
// userId와 nickname을 URL에 포함
const socketUrl = '/ws-chess?userId=' + encodeURIComponent(myUserId) + 
                  '&nickname=' + encodeURIComponent(myNickname);

const socket = new SockJS(socketUrl);
```

**이유**:
- 핸드셰이크 단계에서 userId를 추출하려면 URL 파라미터 필요
- STOMP 헤더는 연결 이후에만 사용 가능

---

### 4. 디버깅 로그 대폭 강화

#### Controller (ChessGameController.java)
```java
@MessageMapping("/create")
public void createGame(@Header("simpUser") Principal principal) {
    System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    System.out.println("📥 [CREATE] 방 생성 요청 수신");
    
    if (principal == null) {
        System.out.println("❌ [CREATE] Principal이 null입니다!");
        return;
    }
    
    String userId = principal.getName();
    System.out.println("👤 [CREATE] UserId: " + userId);
    
    // ... 방 생성 로직 ...
    
    System.out.println("📤 [CREATE] 메시지 전송 시도...");
    System.out.println("   → 대상 userId: " + userId);
    System.out.println("   → 목적지: /queue/create");
    
    messagingTemplate.convertAndSendToUser(userId, "/queue/create", response);
    
    System.out.println("✅ [CREATE] 메시지 전송 완료!");
}
```

#### HandshakeInterceptor
```java
@Override
public boolean beforeHandshake(...) {
    System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    System.out.println("🤝 [HANDSHAKE] WebSocket 핸드셰이크 시작");
    
    String userId = servletRequest.getServletRequest().getParameter("userId");
    System.out.println("🔍 [HANDSHAKE] 파라미터 확인:");
    System.out.println("   → userId: " + userId);
    
    if (userId != null) {
        attributes.put("userId", userId);
        System.out.println("✅ [HANDSHAKE] userId 저장 완료");
    }
}
```

---

## 📊 메시지 흐름도

### Before (Principal 없음)
```
Client                           Server
  │                                │
  ├─ WebSocket 연결 ─────────────→ │
  │                          (Principal 없음) ❌
  │                                │
  ├─ /app/create 요청 ───────────→ │
  │                          createGame() 실행
  │                          convertAndSendToUser(userId, ...)
  │                          → Principal을 찾을 수 없음 ❌
  │                          → 메시지 전송 실패 (조용히)
  │                                │
  │ (응답 없음, 무한 대기) ⏰       │
```

### After (Principal 설정됨)
```
Client                           Server
  │                                │
  ├─ WebSocket 연결 ─────────────→ │
  │  ?userId=xxx&nickname=User     │
  │                          HandshakeInterceptor
  │                          → userId 추출: xxx
  │                          CustomHandshakeHandler
  │                          → Principal 생성: xxx ✅
  │                                │
  ├─ /app/create 요청 ───────────→ │
  │                          createGame() 실행
  │                          Principal: xxx
  │                          convertAndSendToUser(xxx, ...)
  │                          → /user/xxx/queue/create로 전송 ✅
  │                                │
  │ ←── 방 생성 응답 ─────────────┤
  │  roomId: ABC12                 │
  └─ 방 입장 완료! 🎉              │
```

---

## 🔍 핵심 개념: convertAndSendToUser의 동작 원리

### 1. Principal이 필요한 이유
```java
// 이 코드는 userId를 "문자열"로 받지만
messagingTemplate.convertAndSendToUser("user-123", "/queue/create", data);

// 실제로는 "user-123"이라는 이름을 가진 Principal 객체를 찾아야 함!
// Principal principal = sessionRegistry.getPrincipal("user-123");
// if (principal == null) → 메시지 전송 실패 (조용히)
```

### 2. Principal 생성 과정
```
1. 클라이언트 연결 시도
   → /ws-chess?userId=xxx

2. UserHandshakeInterceptor
   → attributes.put("userId", "xxx")

3. CustomHandshakeHandler
   → Principal principal = new StompPrincipal("xxx")
   → return principal

4. Spring이 세션에 Principal 등록
   → sessionRegistry.register("xxx", principal)

5. convertAndSendToUser()
   → Principal principal = sessionRegistry.getPrincipal("xxx")
   → principal의 세션으로 메시지 전송 ✅
```

### 3. 경로 변환 과정
```
서버: convertAndSendToUser("user-123", "/queue/create", data)
      ↓
실제 경로: /user/user-123/queue/create
      ↓
클라이언트: subscribe("/user/queue/create")
      ↓
Spring이 자동 매핑: /user/{userId}/queue/* → /user/queue/*
      ↓
메시지 수신 ✅
```

---

## 🧪 테스트 방법

### 1. 서버 로그 확인
```bash
./gradlew bootRun
```

**예상 로그 (정상):**
```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
🤝 [HANDSHAKE] WebSocket 핸드셰이크 시작
📍 [HANDSHAKE] 요청 URI: /ws-chess?userId=xxx&nickname=User-1234
🔍 [HANDSHAKE] 파라미터 확인:
   → userId: xxx
   → nickname: User-1234
✅ [HANDSHAKE] userId 저장 완료: xxx
✅ [HANDSHAKE] nickname 저장 완료: User-1234
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
✅ CustomHandshakeHandler - Principal 생성: xxx (User-1234)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
📥 [CREATE] 방 생성 요청 수신
🔑 [CREATE] Principal 확인: CustomHandshakeHandler$StompPrincipal
👤 [CREATE] UserId: xxx
✅ [CREATE] 방 생성 완료 - Room ID: ABC12
📤 [CREATE] 메시지 전송 시도...
   → 대상 userId: xxx
   → 목적지: /queue/create
   → 실제 경로: /user/xxx/queue/create
✅ [CREATE] 메시지 전송 완료!
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

### 2. 브라우저 콘솔 확인
```javascript
// 예상 로그 (정상)
🔌 WebSocket 연결 시도: /ws-chess?userId=xxx&nickname=User-1234
✅ WebSocket 연결 성공
🔑 내 사용자 ID: xxx
👤 내 닉네임: User-1234
🔔 방 생성 응답 구독: /user/queue/create
✅ 방 생성됨 (개인 수신): ABC12
🚪 로비 입장: ABC12
```

---

## 📁 수정/생성된 파일

### 신규 생성 (1개)
- `src/main/java/chess/config/CustomHandshakeHandler.java` ⭐ **핵심**

### 수정 (4개)
- `src/main/java/chess/config/WebSocketConfig.java`
  - `setHandshakeHandler()` 추가

- `src/main/java/chess/config/UserHandshakeInterceptor.java`
  - 디버깅 로그 대폭 강화

- `src/main/java/chess/controller/ChessGameController.java`
  - `createGame()` 메서드 로그 강화
  - Principal null 체크 추가

- `src/main/resources/static/js/chess.js`
  - WebSocket URL에 쿼리 파라미터 추가

---

## 🎓 배운 교훈

### 1. convertAndSendToUser()는 Principal이 필수!
- HandshakeHandler 없이는 동작하지 않음
- 조용히 실패하므로 로그가 중요

### 2. SockJS 환경에서 Principal 설정
```java
// 1. URL 파라미터로 userId 전달
/ws-chess?userId=xxx

// 2. HandshakeInterceptor에서 attributes에 저장
attributes.put("userId", userId);

// 3. HandshakeHandler에서 Principal 생성
return new StompPrincipal(userId);

// 4. Spring이 세션과 Principal 매핑
// 5. convertAndSendToUser() 동작 ✅
```

### 3. 디버깅 로그의 중요성
- WebSocket은 에러가 조용히 사라지는 경우가 많음
- 각 단계마다 명확한 로그 필수

---

## ✅ 수정 완료

이제 다음이 정상 동작합니다:
- ✅ Principal이 올바르게 설정됨
- ✅ convertAndSendToUser()가 정상 작동
- ✅ 방 생성 응답이 요청한 유저에게만 전달됨
- ✅ 상세한 디버깅 로그로 문제 추적 가능

---

*작성자: AI Assistant*  
*수정 완료: 2026-01-03*  
*우선순위: 🔴 치명적 (Hotfix)*














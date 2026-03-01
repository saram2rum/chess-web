# 🔍 방 생성 문제 해결 가이드

## ✅ 수정 완료 내용

### 1. 서버에 디버깅 로그 추가
- 방 생성 요청 수신 확인
- 방 ID 생성 확인
- 응답 전송 확인

### 2. 클라이언트에 디버깅 로그 추가
- 방 생성 요청 전송 확인
- 응답 수신 확인
- 데이터 파싱 확인

---

## 🧪 테스트 방법

### 1단계: 서버 재시작
```bash
# 기존 서버 중지 (Ctrl+C)
# 새로 시작
./gradlew bootRun
```

**중요**: 반드시 서버를 재시작해야 변경사항이 적용됩니다!

### 2단계: 브라우저 완전 새로고침
1. **F12** 눌러서 개발자 도구 열기
2. **Console 탭** 선택
3. **Ctrl+Shift+R** (또는 Cmd+Shift+R) - 캐시 무시하고 새로고침
4. 콘솔 내용 모두 지우기

### 3단계: "새 게임 만들기" 클릭

---

## 📊 예상 로그 흐름

### ✅ 정상 작동 시

#### 브라우저 콘솔 (F12)
```
✅ WebSocket 연결 성공: [Stomp frame object]
📤 방 생성 요청 전송...
✅ 방 생성 요청 완료
🎯 방 생성 응답 수신: [Stomp message object]
✅ 방 생성됨: abc-123-def-456
📦 응답 내용: {roomId: "abc-123-def-456", message: "게임 방이 생성되었습니다."}
🚪 방 입장 요청: abc-123-def-456
✅ 입장 성공! 내 색상: WHITE
```

#### 서버 터미널
```
📥 방 생성 요청 수신 - 세션 ID: [session-id]
✅ 방 생성 완료 - Room ID: abc-123-def-456
📤 응답 전송: /user/[session-id]/queue/create
✅ 응답 전송 완료
```

---

## 🐛 문제 진단

### Case 1: "📤 방 생성 요청 전송..." 만 뜨고 멈춤
**원인**: 서버가 응답을 보내지 않음
**해결**:
1. 서버가 실행 중인지 확인
2. 서버 터미널에서 로그 확인
3. 서버 재시작

### Case 2: 브라우저에서 "서버에 연결되지 않았습니다"
**원인**: WebSocket 연결 실패
**해결**:
1. 서버가 8080 포트로 실행 중인지 확인
2. 브라우저 콘솔에서 WebSocket 에러 확인
3. 방화벽 확인

### Case 3: 서버 로그는 있지만 브라우저 응답 없음
**원인**: 주소 불일치 또는 세션 ID 문제
**확인**:
```javascript
// 브라우저 콘솔에서 실행
console.log('Subscriptions:', stompClient._subscriptions);
```
- `/user/queue/create` 구독이 있는지 확인

### Case 4: 구독은 되어있지만 메시지 수신 안됨
**원인**: STOMP 버전 호환성 문제
**해결**:
```html
<!-- index.html에서 라이브러리 버전 확인 -->
<script src="https://cdn.jsdelivr.net/npm/stompjs@2.3.3/lib/stomp.min.js"></script>
```

---

## 🔧 추가 디버깅 팁

### 1. WebSocket 연결 상태 확인
브라우저 콘솔에서:
```javascript
console.log('STOMP 연결 상태:', stompClient.connected);
```

### 2. 수동으로 메시지 전송 테스트
브라우저 콘솔에서:
```javascript
stompClient.send('/app/create', {}, JSON.stringify({}));
```

### 3. 구독 상태 확인
브라우저 콘솔에서:
```javascript
console.log('구독 목록:', Object.keys(stompClient._subscriptions));
```

### 4. 서버 측 WebSocket 연결 확인
서버가 WebSocket 연결을 받았는지 확인하려면
`WebSocketConfig.java`에 로그 추가 가능

---

## 🎯 가장 흔한 문제와 해결책

### 문제 1: 서버 재시작 안함
**증상**: 코드를 수정했는데 반영이 안됨
**해결**: 
```bash
# 서버 중지 (Ctrl+C)
./gradlew bootRun
```

### 문제 2: 브라우저 캐시
**증상**: 이전 JS 파일이 계속 실행됨
**해결**:
- Chrome: Ctrl+Shift+R (또는 Cmd+Shift+R)
- 또는 시크릿 모드로 테스트

### 문제 3: 포트 충돌
**증상**: 서버 시작 시 "Port already in use" 에러
**해결**:
```bash
# Mac/Linux
lsof -ti:8080 | xargs kill -9

# Windows
netstat -ano | findstr :8080
taskkill /PID [PID번호] /F
```

---

## 📝 체크리스트

실행 전 확인:
- [ ] 서버 재시작 완료
- [ ] 브라우저 완전 새로고침 (Ctrl+Shift+R)
- [ ] F12 콘솔 열림
- [ ] Console 탭 선택
- [ ] 이전 로그 지움

버튼 클릭 후:
- [ ] "📤 방 생성 요청 전송..." 출력됨
- [ ] "✅ 방 생성 요청 완료" 출력됨
- [ ] "🎯 방 생성 응답 수신" 출력됨
- [ ] "✅ 방 생성됨: [UUID]" 출력됨
- [ ] 로비 모달이 사라짐
- [ ] 체스판이 나타남

---

## 🆘 여전히 안되면?

다음 정보를 확인해주세요:

### 브라우저 콘솔 전체 출력
- F12 → Console 탭
- 마우스 우클릭 → "Save as..."

### 서버 터미널 출력
- 서버 시작부터 버튼 클릭 후까지 모든 로그

### 네트워크 탭 확인
- F12 → Network 탭
- WS (WebSocket) 필터
- 연결 상태 확인

이 정보들을 제공하시면 정확한 원인을 찾을 수 있습니다!

---

## ✨ 성공 시 화면

```
로비 화면 → [새 게임 만들기 클릭] → 체스판 화면

┌──────────────────────────┐
│ 방: abc-123 | WHITE | ... │
└──────────────────────────┘
        ♜ ♞ ♝ ♛ ♚ ♝ ♞ ♜
        ♟ ♟ ♟ ♟ ♟ ♟ ♟ ♟
        
        
        
        
        ♙ ♙ ♙ ♙ ♙ ♙ ♙ ♙
        ♖ ♘ ♗ ♕ ♔ ♗ ♘ ♖
```

**게임 시작!** 🎮♟️




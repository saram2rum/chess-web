# cp build→src 복사 명령 영향 분석

## 복사 명령 (문제 발생 추정 시점)
```bash
cp build/resources/main/static/css/style.css src/main/resources/static/css/style.css
cp build/resources/main/templates/index.html src/main/resources/templates/index.html
cp build/resources/main/static/js/modules/*.js src/main/resources/static/js/modules/
```

## 정상적인 Gradle 흐름
- `./gradlew build` 시: **src → build** (src가 원본, build가 산출물)
- 즉, build는 항상 src의 "복사본"

## 복사가 문제가 되는 시나리오
1. **src에 최신 수정(동작하던 코드)**이 있었음
2. **build는 이전 빌드 결과**로, 오래된/다른 버전
3. `cp build→src` 실행 → **src가 build로 덮어쓰기됨**
4. 결과: 동작하던 src가 사라지고, build 기준 오래된 코드로 되돌아감

## chess.js.bak = 복원 전 동작하던 구조 (참고용)
- 연결: `userId`, `nickname` 쿼리+헤더로 전달
- Create 응답: `/user/queue/create` 구독 (Principal 필요)
- createGame(): 단순 전송, 버튼 비활성화/타임아웃 없음
- WebSocketConfig: HandshakeHandler, UserHandshakeInterceptor, ChannelInterceptor
- 백엔드: convertAndSendToUser(userId, "/queue/create", response)

## 복원 시도 (적용함)
- network.js: chess.js.bak 구조로 맞춤 (userId 연결, /user/queue + /topic fallback)
- createGame: 단순 전송 (타임아웃/버튼 비활성화 제거)
- WebSocketConfig: HandshakeHandler, Interceptor 복원
- ChessGameController: Principal + convertAndSendToUser

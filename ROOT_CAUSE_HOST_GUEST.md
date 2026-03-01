# 호스트/게스트 역할 혼동 - 원인 분석

## 증상

방 번호를 입력하고 입장한 유저(게스트)가 자신을 호스트로 인식함.
- 대기실 왼쪽 슬롯(호스트 자리)에 내 닉네임 표시
- "Host chooses" 메시지가 보이지만, 실제로는 방장이 아님

---

## 1. "되던 게 안 된" 건지, 처음부터 안 된 건지

**정리: 처음부터 통합이 안 되어 있었음.**

- **USER_IDENTIFICATION_UPGRADE.md**에 따르면, 백엔드를 `userId` 기반으로 바꾸고 `hostUserId`, `hostNickname` 등을 추가할 **계획**이 있었음.
- 실제 구현은 이 문서대로 적용되지 않음.
- 즉, 프론트엔드는 "업그레이드된 API"를 가정하고 작성됐고, 백엔드는 그 API를 구현하지 않은 상태.

---

## 2. 백엔드와 프론트엔드의 불일치

| 구분 | 백엔드 (실제) | 프론트엔드 (기대) |
|------|----------------|-------------------|
| 식별자 | `sessionId` (STOMP 세션) | `userId` (LocalStorage UUID) |
| LobbyStateDTO | `hostSessionId`, `guestSessionId`만 전송 | `hostUserId`, `hostNickname`, `guestUserId`, `guestNickname` 사용 |
| 역할 판별 | `GameRoom.isHost(sessionId)` | `lobbyState.hostUserId === myUserId` |

- 백엔드: `@Header("simpSessionId")`로 sessionId만 받고, GameRoom도 sessionId 기준으로 동작.
- 프론트엔드: `myUserId`(LocalStorage)로만 자신을 식별하고, `hostUserId`로 호스트인지 판단.
- **문제**: 프론트엔드가 사용하는 `hostUserId`를 백엔드가 보내지 않음.

---

## 3. 왜 게스트가 호스트처럼 보였는가

```javascript
// network.js - 역할 판별
isHost = (lobbyState.hostUserId === myUserId);  // hostUserId가 undefined → 항상 false

// ui.js - 호스트 슬롯 표시
hostNameEl.textContent = lobbyState.hostNickname || myNickname;  // hostNickname undefined → 내 닉네임
```

- `hostUserId`, `hostNickname`이 서버에서 내려오지 않아 둘 다 `undefined`.
- `isHost`는 `undefined === myUserId`로 항상 `false`가 됨.
- 그런데 `hostNickname || myNickname` 때문에 호스트 슬롯에는 항상 **내 닉네임**이 표시됨.
- 결과: 게스트 입장 시에도 왼쪽(호스트 자리)에 내 이름이 보여서, 마치 내가 호스트인 것처럼 보임.
- `isHost`는 false라 설정은 "Host chooses"로 뜨지만, UI 배치와 이름 표시 때문에 혼란이 발생.

---

## 4. 근본 원인

1. **API 설계와 구현 불일치**
   - USER_IDENTIFICATION_UPGRADE.md 기준으로 프론트엔드는 `userId` 기반 API를 전제로 작성됨.
   - 백엔드는 `sessionId` 기반 그대로 두고, DTO 확장 없이 `hostSessionId`만 전송.

2. **식별자 불일치**
   - 클라이언트: `userId`로 자기 식별, 역할 판별
   - 서버: `sessionId`로 플레이어 식별
   - `sessionId → userId` 매핑이 없어, 서버가 `hostUserId`를 만들 수 없었음.

3. **폴백의 부작용**
   - `hostNickname || myNickname` 때문에 데이터가 없을 때 항상 내 닉네임이 호스트 슬롯에 표시됨.
   - “데이터 없음”이 “내가 호스트”처럼 보이게 만듦.

---

## 5. 패치의 의미 (원인에 대한 대응)

패치는 위 불일치를 **매핑 레이어**로 연결한 것.

1. **sessionId → userId/nickname 매핑**
   - WebSocket 연결 시 `handleUserConnect(userId, sessionId, nickname)` 호출.
   - ChessService에서 `sessionToUserId`, `sessionToNickname` 맵으로 보관.

2. **LobbyStateDTO 확장**
   - `hostSessionId`, `guestSessionId`를 위 맵으로 변환해 `hostUserId`, `hostNickname`, `guestUserId`, `guestNickname` 채움.

3. **원본 설계 유지**
   - GameRoom, join/start 등 내부 로직은 `sessionId` 기반으로 유지.

즉, “땜빵”이라기보다는, **설계 문서와 실제 구현 사이에 없던 매핑 레이어를 추가한 것**에 가깝다.

---

## 6. 향후 방향

- **정석**: USER_IDENTIFICATION_UPGRADE.md 대로 GameRoom을 `userId` 기반으로 완전히 바꾸는 것.
- **현재 선택**: 기존 `sessionId` 구조는 유지하고, LobbyStateDTO 생성 시 매핑해서 `userId`/`nickname`을 채우는 방식.

지금 구조에서는 이 매핑이 **필수**이며, 단순 패치가 아니라 설계상 빠져 있던 연결고리를 구현한 것으로 보는 게 맞다.

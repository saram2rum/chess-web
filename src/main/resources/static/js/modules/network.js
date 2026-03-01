// ============================================
// WebSocket Connection & Networking
// ============================================

function connectWebSocket() {
    // userId/nickname 전달 (세션 식별)
    const socketUrl = '/ws-chess?userId=' + encodeURIComponent(myUserId) + 
                      '&nickname=' + encodeURIComponent(myNickname);
    const socket = new SockJS(socketUrl);
    stompClient = Stomp.over(socket);
    
    const connectHeaders = { 'userId': myUserId, 'nickname': myNickname };
    console.log('🔌 WebSocket 연결 시도:', socketUrl);
    
    stompClient.connect(connectHeaders, (frame) => {
        console.log('✅ WebSocket 연결 성공');
        console.log('🔑 내 사용자 ID:', myUserId);
        console.log('👤 내 닉네임:', myNickname);
        
        // 🔥 [수정] 개인 에러 토픽 구독 (userId 기반)
        stompClient.subscribe('/topic/errors/user/' + myUserId, (message) => {
            const error = JSON.parse(message.body);
            console.error('❌ 에러 수신:', error.message);
            
            // [스마트 에러 핸들링] "You already have a room (CODE)" / "Already have room (CODE)" 에러 시 자동 입장
            const alreadyExistsMatch = error.message.match(/(?:이미 생성한 방이 있습니다|You already have a room|Already have room)\s*\(([A-Z0-9]+)\)/i);
            if (alreadyExistsMatch && alreadyExistsMatch[1]) {
                const existingRoomId = alreadyExistsMatch[1];
                console.log('🔄 [AUTO-JOIN] 기존 방 감지:', existingRoomId, '→ 자동 입장 시도');
                
                // 버튼 상태 복구 (혹시 모르니)
                const createBtn = document.getElementById('create-game-btn');
                if (createBtn) {
                    createBtn.disabled = false;
                    createBtn.textContent = 'Create Game';
                }
                
                // 알림 대신 바로 입장
                // alert('⚠️ 이미 방이 있어 기존 방으로 이동합니다.'); 
                roomId = existingRoomId;
                justCreatedRoom = false; // 재입장이므로
                joinLobby(existingRoomId);
                return;
            }

            // 존재하지 않는 방 에러 처리 - 조용히 처리 (알림/메시지 없음)
            const isRoomNotFound = error.code === 'LOBBY_JOIN_FAILED' || 
                (error.message && (error.message.includes('Room not found')));
            if (isRoomNotFound) {
                roomId = null;
                clearCurrentRoomId();
                const roomInput = document.getElementById('room-id-input');
                if (roomInput) roomInput.value = '';
                if (typeof showLobbyStatus === 'function') showLobbyStatus('', '');
                const overlay = document.getElementById('reconnect-overlay');
                if (overlay) overlay.classList.add('hidden');
                if (typeof goToHomeScreen === 'function') goToHomeScreen();
                return;
            }

            if (typeof playErrorSound === 'function') playErrorSound();
            alert('⚠️ ' + error.message);
            
            // 🆕 [롤백] 낙관적 업데이트 실패 시 동기화를 위해 게임 상태 재요청
            // 에러가 났다는 건 내 화면과 서버가 다르다는 뜻이므로 강제 동기화
            if (roomId) {
                console.log('🔄 [ROLLBACK] 에러 발생으로 인한 상태 복구 시도');
                stompClient.send('/app/reconnect/' + roomId, {}, JSON.stringify({}));
            }
        });
        console.log('🔔 개인 에러 토픽 구독: /topic/errors/user/' + myUserId);
        
        // 🆕 재접속 상태 구독 (개인) - 전역으로 한 번만 구독
        stompClient.subscribe('/user/queue/gamestate', (message) => {
            const gameState = JSON.parse(message.body);
            console.log('🔄 재접속 - 게임 상태 수신:', gameState);
            handleReconnect(gameState);
        });
        console.log('🔔 재접속 상태 구독: /user/queue/gamestate');
        
        // 🔥 [버그픽스] 로비 상태 개인 수신 (호스트가 topic 브로드캐스트 못 받을 때 대비 - 게스트 입장 시 화면 반영)
        stompClient.subscribe('/user/queue/lobby', (message) => {
            const lobbyState = JSON.parse(message.body);
            console.log('📥 [LOBBY_DIRECT] 로비 상태 개인 수신 (호스트용):', lobbyState);
            handleLobbyUpdate(lobbyState);
        });
        console.log('🔔 로비 개인 큐 구독: /user/queue/lobby');
        
        
        // 🆕 [자동 재접속] 로컬 스토리지에 방 정보가 있다면 자동 입장 시도
        const savedRoomId = getCurrentRoomId();
        if (savedRoomId && !roomId) {
            console.log('🔄 [AUTO-RECONNECT] 저장된 방 ID 발견:', savedRoomId);
            // justCreatedRoom은 false여야 함 (재접속이므로)
            justCreatedRoom = false; 
            joinLobby(savedRoomId);
        }
        
        // 방 생성 응답 구독 (/user/queue/create + /topic/create)
        const handleCreateResponse = (message) => {
            const response = JSON.parse(message.body);
            roomId = response.roomId;
            console.log('✅ 방 생성됨:', roomId);
            justCreatedRoom = true;
            showLobbyStatus('Room created! Joining...', 'success');
            joinLobby(roomId);
        };
        stompClient.subscribe('/user/queue/create', handleCreateResponse);
        stompClient.subscribe('/topic/create', handleCreateResponse);
        console.log('🔔 방 생성 응답 구독: /user/queue/create, /topic/create');
        
    }, (error) => {
        console.error('❌ WebSocket 연결 실패:', error);
        showLobbyStatus('Connection failed.', 'error');
    });
}

function createGame() {
    if (!stompClient || !stompClient.connected) {
        showLobbyStatus('Not connected to server.', 'error');
        return;
    }
    
    // 방 생성 전송
    showLobbyStatus('Creating game room...', 'info');
    stompClient.send('/app/create', {}, JSON.stringify({}));
}

function refreshLobby() {
    if (!stompClient || !stompClient.connected || !roomId) return;
    console.log('🔄 [REFRESH] 로비 새로고침 요청:', roomId);
    stompClient.send('/app/lobby/refresh/' + roomId, {}, JSON.stringify({}));
}

function joinGameFromInput() {
    const input = document.getElementById('room-id-input');
    let inputRoomId = input.value.trim();
    
    if (!inputRoomId) {
        showLobbyStatus('Please enter room code.', 'error');
        return;
    }
    
    // UUID(하이픈 포함)는 그대로, 짧은 방 코드만 대문자
    inputRoomId = inputRoomId.includes('-') ? inputRoomId : inputRoomId.toUpperCase();
    
    roomId = inputRoomId;
    
    // 🔥 [핵심] 입력으로 들어가는 경우는 방금 만든 게 아님
    justCreatedRoom = false;
    
    joinLobby(roomId);
}

function joinLobby(targetRoomId) {
    // UUID(하이픈 포함)는 그대로, 짧은 방 코드(5자)만 대문자
    const raw = (targetRoomId || '').trim();
    roomId = raw.includes('-') ? raw : raw.toUpperCase();
    console.log('━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━');
    console.log('🚪 [JOIN_LOBBY] 로비 입장 시작:', roomId);
    console.log('📍 [JOIN_LOBBY] justCreatedRoom:', justCreatedRoom);
    
    // 🔥 [수정] 1. 먼저 해당 방의 토픽들을 구독 (순서 중요!)
    console.log('🔔 [JOIN_LOBBY] 방 전용 토픽 구독 시작...');
    
    // 로비 상태 구독 (방 전용)
    stompClient.subscribe('/topic/lobby/' + roomId, (message) => {
        const lobbyState = JSON.parse(message.body);
        console.log('📥 [LOBBY_STATE] 로비 상태 수신:', lobbyState);
        handleLobbyUpdate(lobbyState);
    });
    console.log('✅ [JOIN_LOBBY] 구독 완료: /topic/lobby/' + roomId);
    
    // 방 전용 에러 구독 - 입장 실패 시 홈으로 (재접속/세션 만료 대응)
    stompClient.subscribe('/topic/errors/' + roomId, (message) => {
        const err = JSON.parse(message.body);
        const code = err.error || err.code;
        const msg = err.message || '';
        const isReconnectFail = code === 'LOBBY_JOIN_FAILED' && (
            msg.includes('Room not found') || msg.includes('Room is full')
        );
        if (isReconnectFail) {
            const overlay = document.getElementById('reconnect-overlay');
            if (overlay) overlay.classList.add('hidden');
            clearCurrentRoomId();
            goToHomeScreen();
            if (typeof showLobbyStatus === 'function') {
                showLobbyStatus('Previous room is no longer available. Create a new room.', 'error');
            }
            return;
        }
        if (typeof showLobbyStatus === 'function') showLobbyStatus(msg || 'Error', 'error');
    });
    console.log('✅ [JOIN_LOBBY] 구독 완료: /topic/errors/' + roomId);
    
    // 게임 시작 알림 구독 (방 전용)
    stompClient.subscribe('/topic/game/start/' + roomId, (message) => {
        const startInfo = JSON.parse(message.body);
        console.log('🎮 [GAME_START] 게임 시작 알림 수신!', startInfo);
        startGameMode(startInfo.hostColor, startInfo.guestColor);
        // ⏱️ 서버 초기 타이머 값 적용 후 카운트다운 시작
        if (startInfo.whiteTime != null && startInfo.blackTime != null) {
            updateTimer({ whiteTime: startInfo.whiteTime, blackTime: startInfo.blackTime });
        }
        startTimerCountdown();
    });
    console.log('✅ [JOIN_LOBBY] 구독 완료: /topic/game/start/' + roomId);
    
    // 게임 이동 구독 (방 전용)
    stompClient.subscribe('/topic/game/' + roomId, (message) => {
        const moveResult = JSON.parse(message.body);
        console.log('📥 [MOVE] 이동 결과 수신');
        handleMoveResult(moveResult);
    });
    console.log('✅ [JOIN_LOBBY] 구독 완료: /topic/game/' + roomId);
    
    // 이동 가능 위치 구독 (방 전용)
    stompClient.subscribe('/topic/movable/' + roomId, (message) => {
        const response = JSON.parse(message.body);
        handleMovablePositions(response);
    });
    console.log('✅ [JOIN_LOBBY] 구독 완료: /topic/movable/' + roomId);
    
    // ⏱️ 타이머 업데이트 구독 (방 전용)
    stompClient.subscribe('/topic/timer/' + roomId, (message) => {
        const timerData = JSON.parse(message.body);
        updateTimer(timerData);  // 3초마다 서버 동기화 (탭 전환 시 정확한 시간 복원)
    });
    console.log('✅ [JOIN_LOBBY] 구독 완료: /topic/timer/' + roomId);
    
    // 🔥 [수정] 2. 구독 완료 후 로비 입장 요청
    console.log('📤 [JOIN_LOBBY] 서버에 로비 입장 요청 전송...');
    stompClient.send('/app/lobby/join/' + roomId, {}, JSON.stringify({}));
    
    // 🔥 [수정] 3. 재접속 요청은 방을 막 만든 경우가 아닐 때만! (lobby/join 처리 후 재전송하여 "이 방의 플레이어가 아닙니다" 방지)
    if (!justCreatedRoom) {
        setTimeout(() => {
            if (stompClient && stompClient.connected && roomId) {
                console.log('🔄 [JOIN_LOBBY] 재접속 시도 (기존 방 입장) - lobby/join 처리 대기 후');
                stompClient.send('/app/reconnect/' + roomId, {}, JSON.stringify({}));
            }
        }, 300);
    } else {
        console.log('🆕 [JOIN_LOBBY] 신규 방 생성이므로 재접속 건너뜀');
        // 플래그 리셋
        justCreatedRoom = false;
    }
    
    console.log('━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━');
}

function handleLobbyUpdate(lobbyState) {
    console.log('📥 로비 상태 업데이트:', lobbyState);
    
    // 🆕 방 입장 성공 시 로컬 스토리지에 저장 (새로고침 대응)
    saveCurrentRoomId(lobbyState.roomId);
    
    // 🆕 [핵심] 로비 상태 메시지가 왔다는 것은...
    // 1. 게임 시작 전 (isGameStarted=false)
    // 2. 게임 중 누군가 들어옴 (isGameStarted=true)
    // 3. 게임 종료 후 로비 복귀 (isGameStarted=false)
    
    if (lobbyState.isGameStarted) {
        // 게임 중이면 로비 화면 업데이트를 무시하거나 최소한으로 처리
        // 하지만 startGameMode에서 이미 isGameRunning=true로 막고 있으므로 여기서는 pass
    } else {
        // 게임이 시작되지 않은 상태라면(또는 끝난 상태라면) 게임 실행 플래그 해제
        if (isGameRunning) {
            console.log('⏹️ [LOBBY] 게임 종료/미시작 상태 확인 → 게임 모드 해제');
            isGameRunning = false;
        }
    }
    
    // 🔥 [수정] 내 역할 확인 (userId 기반)
    isHost = (lobbyState.hostUserId === myUserId);
    myRole = isHost ? "HOST" : "GUEST";
    
    console.log('내 역할:', myRole, '방장:', isHost);
    
    // 로비 화면 표시
    showWaitingRoom(lobbyState);
}

function sendSettings() {
    const timeLimit = parseInt(document.getElementById('time-limit').value);
    const increment = parseInt(document.getElementById('time-increment').value);
    
    // 클라이언트 측 검증
    if (timeLimit < 1 || timeLimit > 60) {
        alert('⚠️ Please set time between 1 and 60 minutes.');
        document.getElementById('time-limit').value = 10;
        return;
    }
    
    if (increment < 0 || increment > 60) {
        alert('⚠️ Please set increment between 0 and 60 seconds.');
        document.getElementById('time-increment').value = 0;
        return;
    }
    
    const settings = {
        startingSide: document.querySelector('input[name="side"]:checked').value,
        timeLimit: timeLimit,
        increment: increment
    };
    
    console.log('📤 설정 전송:', settings);
    stompClient.send('/app/lobby/settings/' + roomId, {}, JSON.stringify(settings));
}

function requestStartGame() {
    console.log('📤 게임 시작 요청');
    stompClient.send('/app/lobby/start/' + roomId, {}, JSON.stringify({}));
}


// 🆕 방 나가기 요청 (UI에서 호출)
function leaveGame() {
    if (stompClient && stompClient.connected && roomId) {
        console.log('📤 [EXIT] 서버에 퇴장 요청 전송');
        stompClient.send('/app/lobby/leave/' + roomId, {}, JSON.stringify({}));
    }
    clearCurrentRoomId();
}

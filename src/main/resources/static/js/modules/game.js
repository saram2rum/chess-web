// ============================================
// Game Logic, State Management & Initialization
// ============================================

/**
 * 🆕 [신규] LocalStorage 기반 사용자 식별 초기화
 */
function initializeUser() {
    // 1. userId 확인 및 생성
    myUserId = localStorage.getItem('chessUserId');
    if (!myUserId) {
        myUserId = generateUUID();
        localStorage.setItem('chessUserId', myUserId);
        console.log('🆕 새 사용자 ID 생성:', myUserId);
    } else {
        console.log('✅ 기존 사용자 ID 로드:', myUserId);
    }
    
    // 2. nickname 확인 및 생성
    myNickname = localStorage.getItem('chessNickname');
    if (!myNickname) {
        myNickname = 'User-' + Math.floor(Math.random() * 10000);
        localStorage.setItem('chessNickname', myNickname);
        console.log('🆕 새 닉네임 생성:', myNickname);
    } else {
        console.log('✅ 기존 닉네임 로드:', myNickname);
    }
    
    // 3. UI에 닉네임 표시
    updateNicknameDisplay();
}

/**
 * 🆕 [신규] UUID 생성
 */
function generateUUID() {
    return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function(c) {
        const r = Math.random() * 16 | 0;
        const v = c === 'x' ? r : (r & 0x3 | 0x8);
        return v.toString(16);
    });
}

function initializeLobbyButtons() {
    document.getElementById('create-game-btn').addEventListener('click', createGame);
    document.getElementById('join-game-btn').addEventListener('click', joinGameFromInput);
    document.getElementById('play-vs-ai-btn').addEventListener('click', openAISettingsModal);
    document.getElementById('ai-start-btn').addEventListener('click', handleAIStartClick);
    document.getElementById('ai-cancel-btn').addEventListener('click', closeAISettingsModal);
    
    const roomInput = document.getElementById('room-id-input');
    
    // 엔터 키 입력 시 입장
    roomInput.addEventListener('keypress', (e) => {
        if (e.key === 'Enter') joinGameFromInput();
    });
    
    // 🆕 닉네임 변경 버튼 이벤트
    const changeNicknameBtn = document.getElementById('change-nickname-btn');
    if (changeNicknameBtn) {
        changeNicknameBtn.addEventListener('click', openNicknameModal);
    }
    
    // 🆕 모달 버튼 이벤트
    document.getElementById('save-nickname-btn').addEventListener('click', saveNickname);
    document.getElementById('cancel-nickname-btn').addEventListener('click', closeNicknameModal);
    
    // 엔터 키로 저장
    document.getElementById('new-nickname-input').addEventListener('keypress', (e) => {
        if (e.key === 'Enter') saveNickname();
    });
}

function handleReconnect(gameState) {
    console.log('🔄 재접속 처리 시작:', gameState);
    
    if (!gameState.isGameStarted) {
        console.log('⚠️ 게임이 아직 시작되지 않음');
        return;
    }
    
    // 1. 대기실 숨기기
    document.getElementById('waiting-room').classList.add('hidden');
    document.getElementById('lobby-modal').classList.add('hidden');
    
    // 2. 게임 영역 + 체스판 표시
    const gameArea = document.getElementById('game-area');
    if (gameArea) gameArea.classList.remove('hidden');
    const boardContainer = document.getElementById('board-container');
    
    // 3. 방 정보 표시
    const roomIdEl = document.getElementById('game-room-id');
    if (roomIdEl) roomIdEl.textContent = gameState.roomId;
    
    // 4. 내 색상 설정
    myColor = gameState.myColor;
    currentTurn = gameState.currentTurn;
    const myColorEl = document.getElementById('game-my-color');
    if (myColorEl) {
        myColorEl.textContent = myColor;
        myColorEl.className = 'color-badge ' + myColor;
    }
    
    // 5. 보드 생성
    createEmptyBoard();
    
    // 6. 보드 상태 복원
    for (const [coord, pieceInfo] of Object.entries(gameState.boardState)) {
        const cell = document.querySelector(`[data-coord="${coord}"]`);
        if (cell) {
            updateCell(cell, pieceInfo.team, pieceInfo.type);
        }
    }
    
    // 7. 흑 플레이어일 경우 보드 회전
    if (myColor === 'BLACK') {
        boardContainer.classList.add('flipped');
    }
    
    // 8. 턴 표시 업데이트
    updateTurnDisplay();
    refreshBoardState();
    
    // chess.js 동기화 (재접속 시)
    if (typeof initChessGame === 'function') {
        initChessGame();
        if (typeof syncChessFromSnapshot === 'function') {
            syncChessFromSnapshot(gameState.boardState, gameState.currentTurn);
        }
    }
    if (typeof updateCapturedPiecesUI === 'function') updateCapturedPiecesUI();
    
    console.log('✅ 재접속 완료 - 게임 상태 복원됨');
    alert('🔄 Reconnected to the game!');
}

function startGameMode(hostColor, guestColor) {
    // 🆕 로딩 오버레이 숨김
    const overlay = document.getElementById('reconnect-overlay');
    if (overlay) overlay.classList.add('hidden');

    console.log('━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━');
    console.log('🎮 [GAME_MODE] 게임 모드 시작 (UI 전환)');
    
    // 🔧 [수정] 이전 게임의 타이머 정리 (재대결 시 중복 방지)
    stopTimerCountdown();
    
    // 🔧 [수정] 게임 상태 완전 초기화 (이전 게임 잔재 제거)
    currentTurn = 'WHITE';          // 체스는 항상 WHITE가 먼저!
    selectedSquare = null;          // 선택 상태 초기화
    lastMoveSource = null;          // 이전 수 하이라이트 초기화
    lastMoveTarget = null;
    lastOptimisticMove = null;       // 🔊 소리 이중 재생 방지 초기화
    lowTimeSoundPlayed = false;     // ⏱️ LowTime 소리 리셋
    if (typeof highlightCheck === 'function') highlightCheck(null);  // 체크 강조 초기화
    
    // 🔥 [핵심] 게임 실행 상태 설정 (로비 화면 복귀 방지)
    isGameRunning = true;
    
    console.log('   → Host Color:', hostColor);
    console.log('   → Guest Color:', guestColor);
    
    // 1. 내 색상 확정
    myColor = isHost ? hostColor : guestColor;
    console.log('   ✅ 내 색상 확정:', myColor);
    
    // 2. 화면 전환 (강제 실행)
    const lobbyModal = document.getElementById('lobby-modal');
    const waitingRoom = document.getElementById('waiting-room');
    const gameArea = document.getElementById('game-area');
    const boardContainer = document.getElementById('board-container');
    
    // 모든 모달 숨기기
    if (lobbyModal) {
        lobbyModal.classList.add('hidden');
        lobbyModal.style.display = 'none';
    }
    if (waitingRoom) {
        waitingRoom.classList.add('hidden');
        waitingRoom.style.display = 'none';
    }
    
    // 게임 영역 표시 (1v1: ai-header는 숨김 유지)
    if (gameArea && boardContainer) {
        gameArea.classList.remove('hidden');
        boardContainer.style.display = 'flex';
    } else {
        console.error('❌ game-area 또는 board-container 요소를 찾을 수 없습니다!');
        return;
    }
    
    // 3. UI 정보 업데이트
    // 방 번호
    const roomIdEl = document.getElementById('game-room-id');
    if (roomIdEl) roomIdEl.textContent = roomId;
    
    // 내 색상 배지
    const myColorEl = document.getElementById('game-my-color');
    if (myColorEl) {
        myColorEl.textContent = myColor;
        myColorEl.className = 'color-badge ' + myColor;
    }
    
    // 4. 보드 초기화 및 회전
    createEmptyBoard();
    initializeBoard();
    
    // chess.js 초기화 (유효 이동 하이라이트용)
    if (typeof initChessGame === 'function') initChessGame();
    
    if (myColor === 'BLACK') {
        boardContainer.classList.add('flipped');
    } else {
        boardContainer.classList.remove('flipped');
    }
    
    // 5. 턴 표시 및 완료 로그
    updateTurnDisplay();
    if (typeof updateCapturedPiecesUI === 'function') updateCapturedPiecesUI();
    console.log('✅ 게임 화면 전환 완료');
}

function sendMove(source, target) {
    const sourceCell = document.querySelector(`[data-coord="${source}"]`);
    const targetCell = document.querySelector(`[data-coord="${target}"]`);
    
    if (!sourceCell || !targetCell) return;
    
    const pieceType = sourceCell.getAttribute('data-type');
    const pieceTeam = sourceCell.getAttribute('data-team');
    const targetY = parseInt(target[1]);
    
    // 🤖 AI 모드: 1v1 로직 건너뛰고 AI 전용 처리
    if (isAIMode) {
        const needsPromotion = pieceType === 'PAWN' && (targetY === 8 || targetY === 1);
        if (needsPromotion) {
            showPromotionDialog(source, target, pieceTeam);
        } else {
            handleAIUserMove(source, target, null);
        }
        return;
    }
    
    // 🆕 폰 승급 체크 (1v1)
    const needsPromotion = pieceType === 'PAWN' && (targetY === 8 || targetY === 1);
    
    if (needsPromotion) {
        console.log('🔄 [PROMOTION] 폰 승급 필요!');
        showPromotionDialog(source, target, pieceTeam);
    } else {
        // 일반 이동
        executiveMove(source, target, null);
    }
}

function showPromotionDialog(source, target, team) {
    console.log('━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━');
    console.log('🔄 [PROMOTION] 폰 승급 UI 표시');
    console.log('   → Source:', source);
    console.log('   → Target:', target);
    console.log('   → Team:', team);
    
    const overlay = document.getElementById('promotion-overlay');
    const choices = overlay.querySelector('.promotion-choices');
    
    // 기물 이미지 색상 설정
    const colorPrefix = team === 'WHITE' ? 'w' : 'b';
    const typeMap = {
        'queen': 'q',
        'knight': 'n',
        'rook': 'r',
        'bishop': 'b'
    };
    
    choices.querySelectorAll('.promotion-piece').forEach(piece => {
        const type = piece.getAttribute('data-type').toLowerCase();
        const shortType = typeMap[type];
        const img = piece.querySelector('img');
        img.src = `/images/${colorPrefix}_${shortType}.svg`;
    });
    
    // 오버레이 표시
    overlay.classList.remove('hidden');
    
    // 클릭 이벤트 설정 (한 번만)
    const handleChoice = (e) => {
        const target_elem = e.target.closest('.promotion-piece, .promotion-cancel');
        if (!target_elem) return;
        
        const choice = target_elem.getAttribute('data-type');
        console.log('   ✅ 선택:', choice);
        
        // 오버레이 숨기기
        overlay.classList.add('hidden');
        
        // 이벤트 리스너 제거
        overlay.removeEventListener('click', handleChoice);
        
        if (choice === 'CANCEL') {
            console.log('   ❌ 승급 취소');
            console.log('━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━');
            clearMoveSelection();
            return;
        }
        
        if (isAIMode) {
            handleAIUserMove(source, target, choice);
            return;
        }
        
        console.log('   📤 서버에 이동 + 승급 요청 전송');
        console.log('━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━');
        executiveMove(source, target, choice);
    };
    
    overlay.addEventListener('click', handleChoice);
}

function executiveMove(source, target, promotion) {
    const moveData = { 
        source: source, 
        target: target,
        promotion: promotion
    };
    
    // 🚀 [Optimistic UI] 서버 응답 대기 없이 즉시 화면 업데이트
    const sourceCell = document.querySelector(`[data-coord="${source}"]`);
    const targetCell = document.querySelector(`[data-coord="${target}"]`);
    
    if (sourceCell && targetCell) {
        const team = sourceCell.getAttribute('data-team');
        let type = sourceCell.getAttribute('data-type');
        
        // 승급 예측 처리
        if (promotion) {
            const typeParts = promotion.split('_'); 
            type = typeParts[typeParts.length - 1]; 
        }
        
        // 1. 화면 이동 즉시 실행
        updateCell(targetCell, team, type);
        updateCell(sourceCell, 'NONE', 'NONE');
        
        playMoveSound();
        
        // 3. 하이라이트/선택 즉시 제거
        clearSelection();
        
        // 4. [신규] 이전 수 하이라이트 즉시 적용
        highlightLastMove(source, target);
    }
    
    // 🔊 낙관적 이동 기록 (handleMoveResult에서 중복 소리 스킵용)
    lastOptimisticMove = { source, target };
    console.log('📤 [MOVE] 이동 전송 (화면 선반영):', moveData);
    stompClient.send('/app/move/' + roomId, {}, JSON.stringify(moveData));
}

function handleMoveResult(result) {
    console.log('📥 [MOVE_RESULT] 이동 결과 수신:', result);
    
    // 🔊 [소리 우선] 상대 수면 즉시 재생 (내 수는 executiveMove에서 이미 재생함)
    const isMyMove = lastOptimisticMove && result.source === lastOptimisticMove.source && result.target === lastOptimisticMove.target;
    if (isMyMove) {
        lastOptimisticMove = null;
    } else {
        playMoveSound();
    }
    
    // 1. 보드 동기화: boardSnapshot 우선 (표시용, 파싱 없이 바로 DOM 반영)
    if (result.boardSnapshot) {
        syncBoardWithSnapshot(result.boardSnapshot);
        if (result.fen && typeof syncChessFromFen === 'function') {
            syncChessFromFen(result.fen, false);
        } else if (typeof syncChessFromSnapshot === 'function') {
            syncChessFromSnapshot(result.boardSnapshot, result.nextTurn);
        }
    } else if (result.fen && typeof syncChessFromFen === 'function') {
        syncChessFromFen(result.fen, true);
    } else {
        // 폴백: boardSnapshot 없을 때만 개별 이동 적용 (레거시 호환)
        if (!isMyMove) {
            updateBoardFromMove(result);
        } else if (result.capturedPiecePosition) {
            const cap = document.querySelector(`[data-coord="${result.capturedPiecePosition}"]`);
            if (cap && typeof updateCell === 'function') updateCell(cap, 'NONE', 'NONE');
        }
        if (result.source && result.target && typeof syncChessFromMove === 'function') {
            syncChessFromMove(result.source, result.target, result.promotedPiece);
        }
    }
    
    currentTurn = result.nextTurn;
    updateTurnDisplay();
    refreshBoardState(); // 내 기물 표시 갱신
    if (typeof updateCapturedPiecesUI === 'function') updateCapturedPiecesUI();
    
    // ⏱️ 서버 타이머 동기화 (이동 시마다)
    if (result.whiteTime != null && result.blackTime != null) {
        updateTimer({ whiteTime: result.whiteTime, blackTime: result.blackTime });
    }
    
    // 🆕 상대방 이동 하이라이트
    if (result.source && result.target) {
        highlightLastMove(result.source, result.target);
    }
    
    // 🆕 체크 시 킹 칸 배경 강조 + 체크 소리
    if (result.isCheck && typeof findKingInCheck === 'function' && typeof highlightCheck === 'function') {
        const kingCoord = findKingInCheck(result);
        highlightCheck(kingCoord);
        if (!result.isGameOver) playCheckSound();
    } else {
        highlightCheck(null);
    }
    
    if (result.isGameOver) {
        console.log("🏁 게임 종료! 타이머 정지");
        stopTimerCountdown();
        playGameOverSound(result);
        setTimeout(() => showGameOverModal(result), 500);
    }
}

function playMoveSound() {
    try {
        SOUNDS.MOVE.currentTime = 0;
        SOUNDS.MOVE.play().catch(e => console.log("Sound blocked"));
    } catch (e) {}
}

function playCheckSound() {
    try {
        SOUNDS.CHECK.currentTime = 0;
        SOUNDS.CHECK.play().catch(e => console.log("Sound blocked"));
    } catch (e) {}
}

function playGameOverSound(result) {
    try {
        let sound = null;
        if (result.winner === 'DRAW') sound = SOUNDS.DRAW;
        else if (result.winner === myColor) sound = SOUNDS.VICTORY;
        else sound = SOUNDS.DEFEAT;
        if (sound) {
            sound.currentTime = 0;
            sound.play().catch(e => console.log("Sound blocked"));
        }
    } catch (e) {}
}

function playCheckmateSound() {
    try {
        SOUNDS.CHECKMATE.currentTime = 0;
        SOUNDS.CHECKMATE.play().catch(e => console.log("Sound blocked"));
    } catch (e) {}
}

function playErrorSound() {
    // sfx/Error → standard(비자유) 심링크라 재생 생략
}

function playLowTimeSound() {
    try {
        SOUNDS.LOW_TIME.currentTime = 0;
        SOUNDS.LOW_TIME.play().catch(e => console.log("Sound blocked"));
    } catch (e) {}
}

/**
 * ⏱️ 타이머 업데이트 (서버에서 받은 값)
 */
function updateTimer(timerData) {
    whiteTime = timerData.whiteTime;
    blackTime = timerData.blackTime;
    
    console.log('⏱️ [TIMER] 업데이트:', {
        white: formatTime(whiteTime),
        black: formatTime(blackTime)
    });
    
    displayTimer();
    updateTimerStyles();
}

/**
 * ⏱️ 타이머 초기화 (로비 설정에서 읽기 - 게임 시작 시 호출)
 */
function initializeTimerFromLobby() {
    const timeLimitInput = document.getElementById('time-limit');
    const timeLimit = timeLimitInput ? parseInt(timeLimitInput.value, 10) : 10;
    const timeMs = Math.max(1, timeLimit) * 60 * 1000;  // 분 → 밀리초
    whiteTime = timeMs;
    blackTime = timeMs;
    if (typeof displayTimer === 'function') displayTimer();
    if (typeof updateTimerStyles === 'function') updateTimerStyles();
    console.log('⏱️ [TIMER] 초기화:', timeLimit, '분 →', formatTime(timeMs));
}

/**
 * ⏱️ 타이머 시작 (클라이언트 측 카운트다운)
 */
function startTimerCountdown() {
    // 기존 인터벌 정리
    if (timerInterval) {
        clearInterval(timerInterval);
    }
    // whiteTime/blackTime이 0이면 로비 설정으로 초기화
    if (whiteTime <= 0 && blackTime <= 0) {
        initializeTimerFromLobby();
    }
    
    // 100ms마다 현재 턴 플레이어의 시간 감소
    timerInterval = setInterval(() => {
        if (currentTurn === 'WHITE') {
            whiteTime = Math.max(0, whiteTime - 100);
        } else {
            blackTime = Math.max(0, blackTime - 100);
        }
        
        displayTimer();
        updateTimerStyles();
        
        // ⏱️ LowTime 소리 (내 시간 30초 미만 진입 시 1회)
        const myTime = (myColor === 'WHITE') ? whiteTime : blackTime;
        if (myTime > 0 && myTime <= 30000 && !lowTimeSoundPlayed) {
            lowTimeSoundPlayed = true;
            playLowTimeSound();
        }
        
        // 시간 초과 체크
        if ((currentTurn === 'WHITE' && whiteTime <= 0) || 
            (currentTurn === 'BLACK' && blackTime <= 0)) {
            clearInterval(timerInterval);
            console.log('⏱️❌ 시간 초과!');
            
            // 🆕 서버에 타임아웃 알림 (둘 중 한 명만 보내도 됨)
            if (stompClient && stompClient.connected) {
                 stompClient.send("/app/timeout/" + roomId, {}, {});
            }
        }
    }, 100);
}

/**
 * ⏱️ 타이머 중지
 */
function stopTimerCountdown() {
    if (timerInterval) {
        clearInterval(timerInterval);
        timerInterval = null;
    }
}

// ============================================
// UI Helpers & Display Logic
// ============================================

/**
 * 🆕 닉네임 UI 업데이트
 */
function updateNicknameDisplay() {
    const nicknameDisplay = document.getElementById('nickname-display');
    if (nicknameDisplay) {
        nicknameDisplay.textContent = myNickname;
    }
}

/**
 * 🆕 닉네임 변경 (모달 사용으로 대체됨 - 구버전 호환용)
 */
function changeNickname() {
    openNicknameModal();
}

/**
 * 🆕 닉네임 변경 모달 열기
 */
function openNicknameModal() {
    const modal = document.getElementById('nickname-modal');
    const input = document.getElementById('new-nickname-input');
    
    input.value = myNickname; // 현재 닉네임 채우기
    modal.classList.remove('hidden');
    input.focus();
}

/**
 * 🆕 닉네임 변경 모달 닫기
 */
function closeNicknameModal() {
    const modal = document.getElementById('nickname-modal');
    modal.classList.add('hidden');
}

/**
 * 🆕 닉네임 저장
 */
function saveNickname() {
    const input = document.getElementById('new-nickname-input');
    let newNickname = input.value.trim();
    
    if (!newNickname) {
        alert('Please enter a nickname.');
        return;
    }
    
    if (newNickname.length > 12) {
        alert('Nickname cannot exceed 12 characters.');
        return;
    }
    
    myNickname = newNickname;
    localStorage.setItem('chessNickname', myNickname);
    updateNicknameDisplay();
    console.log('✅ 닉네임 변경:', myNickname);
    
    closeNicknameModal();
}

function showLobbyStatus(message, type) {
    const statusEl = document.getElementById('lobby-status');
    statusEl.textContent = message;
    statusEl.className = 'lobby-status ' + type;
    statusEl.classList.remove('hidden');
}

function showWaitingRoom(lobbyState) {
    // 🆕 로딩 완료되었으므로 오버레이 숨김
    const overlay = document.getElementById('reconnect-overlay');
    if (overlay) overlay.classList.add('hidden');

    // 🔥 [핵심] 게임이 이미 시작되었다면 대기실을 보여주지 않음
    if (isGameRunning) {
        console.log('⚠️ [UI] 게임 진행 중이므로 대기실 화면 전환 차단');
        return;
    }

    console.log('✨ [UI] 대기실 표시 및 로비 모달 닫기');
    
    // 1. 로비 모달 확실하게 숨기기
    const lobbyModal = document.getElementById('lobby-modal');
    if (lobbyModal) {
        lobbyModal.classList.add('hidden');
        lobbyModal.style.display = 'none'; // 이중 안전장치
    }
    
    // 🔧 [수정] 게임 보드 숨기기 (로비 복귀 시 확실하게)
    const boardContainer = document.getElementById('board-container');
    if (boardContainer) {
        boardContainer.classList.add('hidden');
        boardContainer.style.display = '';  // inline style 제거 (CSS 기본값 복구)
    }
    
    // 🔧 [수정] 게임 오버 모달 숨기기 (잔존물 제거)
    const gameOverModal = document.getElementById('game-over-modal');
    if (gameOverModal) {
        gameOverModal.classList.add('hidden');
    }
    
    // 2. 대기실 표시
    const waitingRoom = document.getElementById('waiting-room');
    if (waitingRoom) {
        waitingRoom.classList.remove('hidden');
        waitingRoom.style.display = 'flex'; // flex 레이아웃 복구
    }
    
    
    // 방 번호 표시
    const roomIdText = document.getElementById('room-id-text');
    if (roomIdText) roomIdText.textContent = roomId;
    
    // 🆕 나가기 버튼 (Leave) 표시 - 버튼 ID 확인 필요 (exit-room-btn 인지 leave-room-btn 인지)
    // HTML에는 class="exit-room-btn"으로 되어있고 ID는 없음.
    // 쿼리 셀렉터로 찾아서 처리
    const exitBtn = document.querySelector('.exit-room-btn');
    if (exitBtn) exitBtn.classList.remove('hidden');
    
    // Guest 슬롯 업데이트
    const guestCard = document.getElementById('guest-card');
    const nameEl = guestCard?.querySelector('.player-name');
    const statusEl = guestCard?.querySelector('.player-status');
    if (lobbyState.guestUserId) {
        guestCard.classList.add('active');
        if (nameEl) nameEl.textContent = lobbyState.guestNickname || 'User 2';
        if (statusEl) { statusEl.textContent = '●'; statusEl.classList.remove('waiting'); statusEl.classList.add('ready'); }
    } else {
        guestCard?.classList.remove('active');
        if (nameEl) nameEl.textContent = 'Waiting...';
        if (statusEl) { statusEl.textContent = '○'; statusEl.classList.remove('ready'); statusEl.classList.add('waiting'); }
    }
    
    // 🆕 Host 닉네임 표시
    const hostCard = document.getElementById('host-card');
    if (hostCard) {
        const hostNameEl = hostCard.querySelector('.player-name');
        if (hostNameEl) {
            hostNameEl.textContent = lobbyState.hostNickname || myNickname;
        }
    }
    
    // 설정 업데이트
    updateSettings(lobbyState.settings);
    
    // 시작 버튼 활성화 (방장이고 2명 모두 입장한 경우)
    const startBtn = document.getElementById('start-game-btn');
    if (isHost && lobbyState.isReady) {
        startBtn.disabled = false;
        startBtn.onclick = requestStartGame;
        document.querySelector('.start-note').textContent = 'ALL PLAYERS READY';
    }
    
    // 설정 변경 이벤트 (방장만)
    if (isHost) {
        setupSettingsListeners();
        document.getElementById('settings-note').textContent = '';
    } else {
        disableSettings();
        document.getElementById('settings-note').textContent = 'Host chooses';
    }
    
    // 방 번호 복사: room-code-value에 onclick 이미 있음

    // 🔥 [진단용] 로비 새로고침 버튼 (게스트 입장이 안 보일 때)
    const refreshBtn = document.getElementById('refresh-lobby-btn');
    if (refreshBtn) {
        refreshBtn.onclick = () => typeof refreshLobby === 'function' && refreshLobby();
        refreshBtn.classList.remove('hidden');
    }
}

function updateSettings(settings) {
    // 진영 선택
    document.getElementById('side-white').checked = (settings.startingSide === 'WHITE');
    document.getElementById('side-black').checked = (settings.startingSide === 'BLACK');
    document.getElementById('side-random').checked = (settings.startingSide === 'RANDOM');
    
    // 시간 설정
    document.getElementById('time-limit').value = settings.timeLimit;
    document.getElementById('time-increment').value = settings.increment;
}

function setupSettingsListeners() {
    // 진영 선택 변경
    ['side-white', 'side-black', 'side-random'].forEach(id => {
        document.getElementById(id).addEventListener('change', sendSettings);
    });
    
    // 시간 설정 변경
    document.getElementById('time-limit').addEventListener('change', sendSettings);
    document.getElementById('time-increment').addEventListener('change', sendSettings);
}

function disableSettings() {
    document.getElementById('side-white').disabled = true;
    document.getElementById('side-black').disabled = true;
    document.getElementById('side-random').disabled = true;
    document.getElementById('time-limit').disabled = true;
    document.getElementById('time-increment').disabled = true;
}

function copyRoomCode() {
    // 요소 가져오기
    const textEl = document.getElementById('room-id-text');
    if (!textEl) return;

    // 텍스트 추출 (모든 공백 제거)
    let text = textEl.textContent || textEl.innerText;
    text = text.replace(/\s/g, '').trim();
    
    // 유효성 검사
    if (!text || text === '-----') {
        console.warn('복사할 코드가 없습니다.');
        return;
    }

    // 클립보드 복사 시도
    if (navigator.clipboard && navigator.clipboard.writeText) {
        navigator.clipboard.writeText(text).then(() => {
            handleCopySuccess(text);
        }).catch(err => {
            console.error('Clipboard API 실패:', err);
            fallbackCopyText(text);
        });
    } else {
        fallbackCopyText(text);
    }
}

function fallbackCopyText(text) {
    const textArea = document.createElement("textarea");
    textArea.value = text;
    textArea.style.position = "fixed";  // 화면 밖으로 안 나가게
    textArea.style.left = "-9999px";
    document.body.appendChild(textArea);
    textArea.focus();
    textArea.select();
    
    try {
        const successful = document.execCommand('copy');
        if (successful) {
            handleCopySuccess(text);
        } else {
            prompt("Ctrl+C to copy:", text);
        }
    } catch (err) {
        console.error('Fallback copy 실패:', err);
        prompt("Ctrl+C to copy:", text);
    }
    
    document.body.removeChild(textArea);
}

function handleCopySuccess(text) {
    console.log('📋 복사 성공:', text);
    const codeValue = document.getElementById('room-code-value');
    if (codeValue) {
        const originalBg = codeValue.style.backgroundColor;
        codeValue.style.backgroundColor = 'rgba(9, 132, 227, 0.2)';
        setTimeout(() => {
            codeValue.style.backgroundColor = originalBg || '';
        }, 300);
    }
    showLobbyStatus('Code copied!', 'success');
}

function showGameOverModal(result) {
    const modal = document.getElementById('game-over-modal');
    const winnerText = document.getElementById('winner-text');
    const reasonEl = document.getElementById('game-over-reason');

    modal.classList.remove('hidden');

    // 🆕 승자 텍스트 설정
    if (result.winner) {
        if (result.winner === 'DRAW') {
            winnerText.innerText = "Game Drawn";
            winnerText.style.color = "#aaa";
        } else {
            const winner = result.winner; // "WHITE" or "BLACK"
            winnerText.innerText = winner + " WINS!";
            winnerText.style.color = (winner === 'WHITE') ? "#ffb700" : "#a8c0ff";
        }
    } else {
        // 백업 로직 (서버 데이터 누락 시)
        winnerText.innerText = "GAME OVER";
        winnerText.style.color = "#aaa";
    }

    // 🆕 종료 사유 상세 표시
    if (reasonEl) {
        let reason = result.endReason || result.drawReason || "Game Over";
        
        // 사유 텍스트 다듬기
        if (reason === 'Checkmate') reason = 'by Checkmate';
        else if (reason === 'Time Out') reason = 'on Time';
        else if (reason === 'Resignation') reason = 'by Resignation';
        else if (reason === 'Stalemate') reason = 'Draw by Stalemate';
        else if (reason === 'Threefold Repetition') reason = 'Draw by Repetition';
        else if (reason === '50-Move Rule') reason = 'Draw by 50-Move Rule';
        
        reasonEl.textContent = reason;
    }
}

function returnToLobby() {
    if (stompClient && stompClient.connected && roomId) {
        console.log('📤 로비 복귀 (Play Again)');
        
        stopTimerCountdown();
        currentTurn = 'WHITE';
        isGameRunning = false;
        selectedSquare = null;
        
        // 게임 보드 숨기기
        const boardContainer = document.getElementById('board-container');
        if (boardContainer) {
            boardContainer.classList.add('hidden');
            boardContainer.style.display = '';
            boardContainer.classList.remove('flipped');
        }
        
        // 게임 오버 모달 닫기
        const gameOverModal = document.getElementById('game-over-modal');
        if (gameOverModal) gameOverModal.classList.add('hidden');
        
        // 🆕 대기실 다시 표시 (안 하면 빈 화면)
        const waitingRoom = document.getElementById('waiting-room');
        if (waitingRoom) {
            waitingRoom.classList.remove('hidden');
            waitingRoom.style.display = 'flex';
        }
        
        // 서버에 로비 복귀 알림 (선택적, 게임 리셋 등)
        stompClient.send('/app/lobby/return/' + roomId, {}, JSON.stringify({}));
    } else {
        window.location.reload();
    }
}

function updateTurnDisplay() {
    const frame = document.getElementById('board-frame');
    if (frame) {
        frame.classList.remove('turn-white', 'turn-black');
        frame.classList.add('turn-' + currentTurn.toLowerCase());
    }
    
    // HTML에서 요소가 삭제되었으므로 null 체크
    const turnBadge = document.getElementById('game-current-turn');
    if (turnBadge) {
        turnBadge.textContent = currentTurn;
        turnBadge.className = 'turn-badge ' + currentTurn;
    }
}

/**
 * ⏱️ 타이머 표시
 */
function displayTimer() {
    const myTimerEl = document.querySelector('#timer-me .timer-value');
    const opponentTimerEl = document.querySelector('#timer-opponent .timer-value');
    
    if (!myTimerEl || !opponentTimerEl) return;
    
    if (myColor === 'WHITE') {
        myTimerEl.textContent = formatTime(whiteTime);
        opponentTimerEl.textContent = formatTime(blackTime);
    } else {
        myTimerEl.textContent = formatTime(blackTime);
        opponentTimerEl.textContent = formatTime(whiteTime);
    }
}

/**
 * ⏱️ 시간 포맷 (밀리초 → MM:SS)
 */
function formatTime(ms) {
    if (ms < 0) ms = 0;
    const totalSeconds = Math.floor(ms / 1000);
    const minutes = Math.floor(totalSeconds / 60);
    const seconds = totalSeconds % 60;
    return `${minutes}:${seconds.toString().padStart(2, '0')}`;
}

/**
 * ⏱️ 타이머 스타일 업데이트 (현재 턴 강조, 경고 표시)
 */
function updateTimerStyles() {
    const myTimerEl = document.getElementById('timer-me');
    const opponentTimerEl = document.getElementById('timer-opponent');
    
    if (!myTimerEl || !opponentTimerEl) return;
    
    // 현재 턴인 사람의 타이머 강조
    if (currentTurn === myColor) {
        myTimerEl.classList.add('active');
        opponentTimerEl.classList.remove('active');
    } else {
        myTimerEl.classList.remove('active');
        opponentTimerEl.classList.add('active');
    }
    
    // 시간 부족 경고 (30초 미만)
    const myTime = (myColor === 'WHITE') ? whiteTime : blackTime;
    const opponentTime = (myColor === 'WHITE') ? blackTime : whiteTime;
    
    if (myTime < 30000) {
        myTimerEl.classList.add('warning');
    } else {
        myTimerEl.classList.remove('warning');
    }
    
    if (opponentTime < 30000) {
        opponentTimerEl.classList.add('warning');
    } else {
        opponentTimerEl.classList.remove('warning');
    }
}

/**
 * 홈 화면으로 전환 (에러/재접속 실패 시)
 */
function goToHomeScreen() {
    const overlay = document.getElementById('reconnect-overlay');
    if (overlay) overlay.classList.add('hidden');
    const lobbyModal = document.getElementById('lobby-modal');
    if (lobbyModal) { lobbyModal.classList.remove('hidden'); lobbyModal.style.display = 'flex'; }
    const waitingRoom = document.getElementById('waiting-room');
    if (waitingRoom) { waitingRoom.classList.add('hidden'); waitingRoom.style.display = 'none'; }
    const boardContainer = document.getElementById('board-container');
    if (boardContainer) boardContainer.classList.add('hidden');
}

/**
 * 🆕 홈으로 나가기 (페이지 리로드로 깔끔하게 초기화)
 */
function exitToHome() {
    if (confirm('Are you sure you want to leave the room?')) {
        // 🆕 네트워크 모듈에 위임
        leaveGame();
        
        window.location.href = '/'; 
    }
}

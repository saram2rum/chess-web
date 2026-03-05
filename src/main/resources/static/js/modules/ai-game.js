// ============================================
// AI 대전 전용 로직 (1v1과 완전 분리)
// isAIMode=true일 때만 사용, WebSocket/roomId 미사용
//
// [Resource Optimization] AI 서버 슬롯 제한(최대 3) - 시작 전 capacity 체크
// ============================================

const MSG_AI_SERVER_BUSY = 'The AI server is currently busy and unable to accept new battles.';
const AI_IDLE_MINUTES = 30;
const MSG_AI_IDLE = 'You have been disconnected due to inactivity.';
const EVAL_SEARCHTIME_MS = 500;  // 논문: 형세분석 품질(movetime) 실험용

function touchAIActivity() {
    if (typeof aiLastActivityAt !== 'undefined') aiLastActivityAt = Date.now();
}

function startAIIdleCheck() {
    if (aiIdleCheckInterval) clearInterval(aiIdleCheckInterval);
    touchAIActivity();
    aiIdleCheckInterval = setInterval(() => {
        if (!isAIMode) return;
        const idleMs = Date.now() - aiLastActivityAt;
        if (idleMs > AI_IDLE_MINUTES * 60 * 1000) {
            if (aiIdleCheckInterval) {
                clearInterval(aiIdleCheckInterval);
                aiIdleCheckInterval = null;
            }
            exitAIToHome();
            alert(MSG_AI_IDLE);
        }
    }, 60_000); // 1분마다 체크
}

/** centipawn → 바 위치 0~100% (백 + / 흑 -) */
function centipawnToBarPercent(cp) {
    if (typeof cp !== 'number') return 50;
    const clamped = Math.max(-400, Math.min(400, cp));
    return 50 + (clamped / 8);  // ±400 → 0~100
}

function formatEvalValue(data) {
    if (!data) return '0.0';
    if (data.type === 'mate') {
        return 'M' + Math.abs(data.value || 0);
    }
    const cp = data.value || 0;
    return (Math.abs(cp) / 100).toFixed(1);  // 부호 없이 수치만 (유리한 쪽 극단에 있으므로)
}

/** FEN에서 턴 추출 (w=백, b=흑) */
function getTurnFromFen(fen) {
    if (!fen || typeof fen !== 'string') return 'w';
    const parts = fen.trim().split(/\s+/);
    return (parts[1] === 'b') ? 'b' : 'w';
}

function updateEvalBar(data) {
    if (!data || (!isAIMode && !evalBarEnabled)) return;
    let cp = 0;
    if (data.type === 'cp') {
        cp = data.value || 0;
    } else if (data.type === 'mate') {
        cp = (data.value > 0 ? 400 : -400);
    }
    // Stockfish turn perspective: + = 턴인 쪽 유리. 항상 백 기준으로 표시하려면 흑 턴일 때 부호 반전
    const turn = getTurnFromFen(data.fen);
    if (turn === 'b') cp = -cp;
    const wrapper = document.getElementById('eval-bar-wrapper');
    const track = document.getElementById('eval-bar-track');
    const valueEl = document.getElementById('eval-value');
    if (!wrapper || !track) return;
    const pct = Math.max(0, Math.min(100, centipawnToBarPercent(cp)));
    const splitPct = (myColor === 'WHITE' ? 100 - pct : pct) + '%';
    // 유저 색에 맞게 바 배치: 백=유저면 흰색을 하단(유저쪽)에
    track.classList.toggle('user-white', myColor === 'WHITE');
    track.style.setProperty('--eval-split', splitPct);
    // 수치: 유리한 쪽 극단에 배치 (0.0도 한쪽이 미세 유리하므로 극단에 표기)
    const valueEdge = cp >= 0 ? (myColor === 'WHITE' ? 'bottom' : 'top')   // 백 유리 또는 동점
        : (myColor === 'WHITE' ? 'top' : 'bottom');                        // 흑 유리
    wrapper.dataset.evalEdge = valueEdge;
    // cp는 바 위치용 값. 표시용에는 원본 data 사용 (mate일 때 value가 2 등 실제 수)
    if (valueEl) {
        const displayData = data.type === 'mate'
            ? { type: 'mate', value: (turn === 'b' ? -data.value : data.value) }
            : { type: 'cp', value: cp };
        valueEl.textContent = formatEvalValue(displayData);
    }
}

/** 탐색 기반 형세 평가만 사용 (static 제거). 1v1/AI 공통 */
function requestPositionEvaluation(fen) {
    if ((!isAIMode && !evalBarEnabled) || !fen) return;
    aiEvalRequestFen = fen;

    fetch('/ai/evaluate?fen=' + encodeURIComponent(fen) + '&searchtime=' + EVAL_SEARCHTIME_MS)
        .then(r => r.json())
        .then(data => {
            if (aiEvalRequestFen === fen) updateEvalBar(data);
        })
        .catch(() => {});
}

function stopAIIdleCheck() {
    if (aiIdleCheckInterval) {
        clearInterval(aiIdleCheckInterval);
        aiIdleCheckInterval = null;
    }
}

/**
 * AI 대전 시작 전 서버 수용 여부 확인 (최대 3개 슬롯 제한)
 */
async function handleAIStartClick() {
    try {
        const res = await fetch('/ai/status');
        const data = await res.json().catch(() => ({}));
        if (!data.available) {
            alert(MSG_AI_SERVER_BUSY);
            return;
        }
        startAIMode();
    } catch (e) {
        console.error('AI status check failed:', e);
        alert(MSG_AI_SERVER_BUSY);
    }
}

function openAISettingsModal() {
    const modal = document.getElementById('ai-settings-modal');
    if (modal) modal.classList.remove('hidden');
}

function closeAISettingsModal() {
    const modal = document.getElementById('ai-settings-modal');
    if (modal) modal.classList.add('hidden');
}

function startAIMode() {
    const diffEl = document.querySelector('input[name="ai-difficulty"]:checked');
    const difficulty = parseInt(diffEl?.value || 6);
    aiMovetimeMs = parseInt(diffEl?.dataset?.movetime || 1000, 10);
    let playerColor = document.querySelector('input[name="ai-color"]:checked')?.value || 'RANDOM';
    if (playerColor === 'RANDOM') {
        playerColor = Math.random() < 0.5 ? 'WHITE' : 'BLACK';
    }

    closeAISettingsModal();
    document.getElementById('lobby-modal').classList.add('hidden');
    document.getElementById('lobby-modal').style.display = 'none';

    isAIMode = true;
    myColor = playerColor;
    aiColor = playerColor === 'WHITE' ? 'BLACK' : 'WHITE';
    aiSkillLevel = difficulty;
    currentTurn = 'WHITE';
    selectedSquare = null;
    lastMoveSource = null;
    lastMoveTarget = null;
    if (typeof highlightCheck === 'function') highlightCheck(null);

    const gameArea = document.getElementById('game-area');
    if (gameArea) gameArea.classList.remove('hidden');
    const aiHeader = document.getElementById('ai-header');
    if (aiHeader) aiHeader.classList.remove('hidden');
    const boardContainer = document.getElementById('board-container');
    boardContainer.classList.add('ai-mode');
    boardContainer.style.display = 'flex';

    createEmptyBoard();
    initializeBoard();
    if (typeof initChessGame === 'function') initChessGame();

    if (myColor === 'BLACK') {
        boardContainer.classList.add('flipped');
    } else {
        boardContainer.classList.remove('flipped');
    }

    updateTurnDisplay();
    refreshBoardState();
    updateUndoButtonState();
    if (typeof updateCapturedPiecesUI === 'function') updateCapturedPiecesUI();

    if (myColor === 'WHITE') {
        requestPositionEvaluation(chessGame.fen());
    }
    if (myColor === 'BLACK') {
        currentTurn = 'WHITE';
        requestAndApplyAIMove();
    }

    startAIIdleCheck();
}

const UNDO_COOLDOWN_MS = 1000;

/**
 * 무르기 (Takeback) - AI 대전 전용
 * 두 수 무르기: AI 수 + 내 수 → 내 턴으로 복귀
 * 예외: history 1개일 때(백 첫 수만 둠 / 흑이 AI 첫 수만 있음) → 1번만 undo
 * 1초 쿨다운: 연타로 인한 부하 방지
 */
function undoAIMove() {
    if (!isAIMode || !chessGame) return;
    if (aiGameThinking) return;  // AI 계산 중에는 무시
    if (Date.now() - aiLastUndoAt < UNDO_COOLDOWN_MS) return;  // 1초 쿨다운
    touchAIActivity();

    const hist = chessGame.history();
    if (hist.length === 0) return;

    const undoCount = hist.length === 1 ? 1 : 2;
    for (let i = 0; i < undoCount; i++) {
        chessGame.undo();
    }

    syncBoardFromFen();
    refreshBoardState();
    lastMoveSource = null;
    lastMoveTarget = null;
    document.querySelectorAll('.last-move').forEach(el => el.classList.remove('last-move'));
    if (typeof highlightCheck === 'function') highlightCheck(null);
    clearSelection();
    const gameOverModal = document.getElementById('game-over-modal');
    if (gameOverModal) gameOverModal.classList.add('hidden');

    currentTurn = myColor;
    updateTurnDisplay();
    aiLastUndoAt = Date.now();
    updateUndoButtonState();
    if (typeof updateCapturedPiecesUI === 'function') updateCapturedPiecesUI();

    // 흑: AI 첫 수만 undo 한 경우 → 초기 상태, AI가 다시 첫 수 두어야 함
    if (myColor === 'BLACK' && hist.length === 1) {
        currentTurn = 'WHITE';
        updateTurnDisplay();
        requestAndApplyAIMove();
    } else {
        requestPositionEvaluation(chessGame.fen());
    }
    // 1초 후 버튼 재활성화 (쿨다운 종료)
    setTimeout(updateUndoButtonState, UNDO_COOLDOWN_MS);
}

/** 무르기 버튼 활성/비활성 (AI 계산 중, 초기 상태, 1초 쿨다운 시 비활성) */
function updateUndoButtonState() {
    const btn = document.getElementById('ai-undo-btn');
    if (!btn) return;
    if (!isAIMode) {
        btn.disabled = true;
        return;
    }
    if (aiGameThinking) {
        btn.disabled = true;
        return;
    }
    if (Date.now() - aiLastUndoAt < UNDO_COOLDOWN_MS) {
        btn.disabled = true;
        return;
    }
    const hist = chessGame?.history?.() ?? [];
    btn.disabled = hist.length === 0;
}

function exitAIToHome() {
    stopAIIdleCheck();
    aiLastUndoAt = 0;
    aiEvalRequestFen = null;
    isAIMode = false;
    aiColor = null;
    aiSkillLevel = 10;
    aiMovetimeMs = 2000;

    const gameArea = document.getElementById('game-area');
    if (gameArea) gameArea.classList.add('hidden');
    const aiHeader = document.getElementById('ai-header');
    if (aiHeader) aiHeader.classList.add('hidden');
    const boardContainer = document.getElementById('board-container');
    if (boardContainer) {
        boardContainer.classList.remove('ai-mode');
        boardContainer.classList.remove('flipped');
    }

    const gameOverModal = document.getElementById('game-over-modal');
    if (gameOverModal) gameOverModal.classList.add('hidden');

    aiGameThinking = false;
    updateUndoButtonState();

    document.getElementById('lobby-modal').classList.remove('hidden');
    document.getElementById('lobby-modal').style.display = 'flex';
}

function handleAIUserMove(source, target, promotion) {
    if (!isAIMode || currentTurn !== myColor) return;
    touchAIActivity();

    const promotionLetter = promotion ? String(promotion.charAt(0)).toLowerCase() : undefined;
    const opt = { from: source, to: target };
    if (promotionLetter) opt.promotion = promotionLetter;

    if (!chessGame || chessGame.move(opt) === null) return;

    syncBoardFromFen();
    refreshBoardState();
    playMoveSound();
    clearSelection();
    highlightLastMove(source, target);
    if (typeof updateCapturedPiecesUI === 'function') updateCapturedPiecesUI();

    if (checkAIGameOver()) return;

    currentTurn = aiColor;
    updateTurnDisplay();
    updateUndoButtonState();
    requestPositionEvaluation(chessGame.fen());
    requestAndApplyAIMove();
}

async function requestAndApplyAIMove() {
    if (!chessGame) return;
    const fen = chessGame.fen();
    if (!fen) return;

    aiGameThinking = true;
    updateUndoButtonState();

    try {
        const res = await fetch('/ai/get-move', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                fen: fen,
                skill_level: aiSkillLevel,
                movetime_ms: aiMovetimeMs
            })
        });
        const data = await res.json().catch(() => ({}));
        if (!res.ok || !data.best_move) {
            if (res.status === 503 && data.detail) {
                alert(data.detail);
            } else {
                console.error('AI API error:', data);
            }
            currentTurn = myColor;
            updateTurnDisplay();
            return;
        }
        applyAIMove(data.best_move);
    } catch (e) {
        console.error('AI fetch error:', e);
        alert(MSG_AI_SERVER_BUSY);
        currentTurn = myColor;
        updateTurnDisplay();
    } finally {
        aiGameThinking = false;
        updateUndoButtonState();
    }
}

function applyAIMove(uciMove) {
    if (!uciMove || uciMove.length < 4) return;
    touchAIActivity();

    const from = uciMove.slice(0, 2);
    const to = uciMove.slice(2, 4);
    const promotion = uciMove.length > 4 ? uciMove[4].toLowerCase() : undefined;

    const opt = { from: from, to: to };
    if (promotion) opt.promotion = promotion;

    if (!chessGame || chessGame.move(opt) === null) return;

    syncBoardFromFen();
    refreshBoardState();
    playMoveSound();
    highlightLastMove(from, to);

    if (chessGame.in_check() && typeof highlightCheck === 'function') {
        const kingSquare = findAICheckedKing();
        highlightCheck(kingSquare);
    } else {
        if (typeof highlightCheck === 'function') highlightCheck(null);
    }

    if (checkAIGameOver()) return;

    currentTurn = myColor;
    updateTurnDisplay();
    updateUndoButtonState();
    if (typeof updateCapturedPiecesUI === 'function') updateCapturedPiecesUI();
    requestPositionEvaluation(chessGame.fen());
}

function findAICheckedKing() {
    const turn = chessGame.turn();
    const kingColor = turn === 'w' ? 'WHITE' : 'BLACK';
    const board = chessGame.board();
    for (let r = 0; r < 8; r++) {
        for (let c = 0; c < 8; c++) {
            const p = board[r][c];
            if (p && p.type === 'k' && p.color === turn) {
                const file = String.fromCharCode(97 + c);
                const rank = 8 - r;
                return file + rank;
            }
        }
    }
    return null;
}

function checkAIGameOver() {
    if (!chessGame) return false;

    let winner = null;
    let reason = 'Game Over';

    if (chessGame.in_checkmate()) {
        winner = currentTurn;
        reason = 'Checkmate';
    } else if (chessGame.in_stalemate()) {
        winner = 'DRAW';
        reason = 'Stalemate';
    } else if (chessGame.in_draw()) {
        winner = 'DRAW';
        reason = chessGame.in_threefold_repetition() ? 'Threefold Repetition' :
                 chessGame.in_fifty_move() ? '50-Move Rule' : 'Draw';
    }

    if (winner !== null) {
        setTimeout(() => {
            showGameOverModal({ winner: winner, endReason: reason });
            playGameOverSound({ winner: winner, endReason: reason });
        }, 300);
        return true;
    }
    return false;
}

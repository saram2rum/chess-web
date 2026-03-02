// ============================================
// AI 대전 전용 로직 (1v1과 완전 분리)
// isAIMode=true일 때만 사용, WebSocket/roomId 미사용
//
// [Resource Optimization] AI 서버 슬롯 제한(최대 3) - 시작 전 capacity 체크
// ============================================

const MSG_AI_SERVER_BUSY = 'The AI server is currently busy and unable to accept new battles.';

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

    if (myColor === 'BLACK') {
        currentTurn = 'WHITE';
        requestAndApplyAIMove();
    }
}

/**
 * 무르기 (Takeback) - AI 대전 전용
 * 두 수 무르기: AI 수 + 내 수 → 내 턴으로 복귀
 * 예외: history 1개일 때(백 첫 수만 둠 / 흑이 AI 첫 수만 있음) → 1번만 undo
 */
function undoAIMove() {
    if (!isAIMode || !chessGame) return;
    if (aiGameThinking) return;  // AI 계산 중에는 무시

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
    updateUndoButtonState();
    if (typeof updateCapturedPiecesUI === 'function') updateCapturedPiecesUI();

    // 흑: AI 첫 수만 undo 한 경우 → 초기 상태, AI가 다시 첫 수 두어야 함
    if (myColor === 'BLACK' && hist.length === 1) {
        currentTurn = 'WHITE';
        updateTurnDisplay();
        requestAndApplyAIMove();
    }
}

/** 무르기 버튼 활성/비활성 (AI 계산 중, 초기 상태 시 비활성) */
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
    const hist = chessGame?.history?.() ?? [];
    btn.disabled = hist.length === 0;
}

function exitAIToHome() {
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

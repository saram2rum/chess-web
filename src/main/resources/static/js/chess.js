// chess.js

// 1. Assets Configuration
const PIECE_IMAGES = {
    'WHITE_PAWN':   '/images/w_p.svg',
    'WHITE_ROOK':   '/images/w_r.svg',
    'WHITE_KNIGHT': '/images/w_n.svg',
    'WHITE_BISHOP': '/images/w_b.svg',
    'WHITE_QUEEN':  '/images/w_q.svg',
    'WHITE_KING':   '/images/w_k.svg',

    'BLACK_PAWN':   '/images/b_p.svg',
    'BLACK_ROOK':   '/images/b_r.svg',
    'BLACK_KNIGHT': '/images/b_n.svg',
    'BLACK_BISHOP': '/images/b_b.svg',
    'BLACK_QUEEN':  '/images/b_q.svg',
    'BLACK_KING':   '/images/b_k.svg'
};

const SOUNDS = {
    MOVE: new Audio('/sounds/move.mp3'),
    CAPTURE: new Audio('/sounds/capture.mp3')
};

// 2. Sound Logic
function playMoveSound(isCapture) {
    try {
        const sound = isCapture ? SOUNDS.CAPTURE : SOUNDS.MOVE;
        sound.currentTime = 0;
        sound.play().catch(e => console.log("Sound blocked:", e));
    } catch (e) { console.error(e); }
}

// 3. Game State & Event Listeners
let selectedSquare = null;

window.onload = function() {
    renderInitialBoard();
    const initialTurn = document.getElementById('turn-data').innerText;
    refreshBoardState(initialTurn);
};

// 4. Board Rendering Logic
function renderInitialBoard() {
    document.querySelectorAll('.chess-board td').forEach(cell => {
        const type = cell.getAttribute('data-type');
        const team = cell.getAttribute('data-team');
        if (type && type !== 'NONE') {
            const key = team + '_' + type;
            const imgUrl = PIECE_IMAGES[key];
            if (imgUrl) cell.innerHTML = `<img src="${imgUrl}" class="piece-img">`;
        }
    });
}

function refreshBoardState(currentTurn) {
    document.querySelectorAll('.chess-board td').forEach(cell => {
        cell.classList.remove('my-piece');
        if (cell.getAttribute('data-team') === currentTurn) {
            cell.classList.add('my-piece');
        }
    });
}

function updateBoard(boardMap, teamMap) {
    for (const [coord, symbol] of Object.entries(boardMap)) {
        const cell = document.querySelector(`[data-coord="${coord}"]`);
        if (cell) {
            const team = teamMap[coord];
            let type = 'NONE';
            if (symbol !== "") {
                if ("♙♟︎".includes(symbol)) type = "PAWN";
                else if ("♖♜".includes(symbol)) type = "ROOK";
                else if ("♘♞".includes(symbol)) type = "KNIGHT";
                else if ("♗♝".includes(symbol)) type = "BISHOP";
                else if ("♕♛".includes(symbol)) type = "QUEEN";
                else if ("♔♚".includes(symbol)) type = "KING";
            }

            cell.setAttribute('data-team', team);
            cell.setAttribute('data-type', type);

            if (type !== 'NONE') {
                const key = team + '_' + type;
                const imgUrl = PIECE_IMAGES[key];
                cell.innerHTML = `<img src="${imgUrl}" class="piece-img">`;
            } else {
                cell.innerHTML = "";
            }
        }
    }
}

function updateTurn(turn) {
    const frame = document.getElementById('board-frame');
    frame.classList.remove('turn-white', 'turn-black');
    if (turn === 'WHITE') frame.classList.add('turn-white');
    else frame.classList.add('turn-black');
}

// 5. Interaction Logic
function onClickSquare(cell) {
    if (document.querySelector('.promotion-popup')) { clearSelection(); return; }

    if (selectedSquare == null) {
        if (!cell.classList.contains('my-piece')) return;
        selectPiece(cell);
    } else {
        if (cell.classList.contains('my-piece')) {
            if (cell === selectedSquare) clearSelection();
            else { clearSelection(); selectPiece(cell); }
            return;
        }
        if (!cell.classList.contains('target-square')) { clearSelection(); return; }

        const source = selectedSquare.getAttribute('data-coord');
        const target = cell.getAttribute('data-coord');
        const isCapture = cell.getAttribute('data-type') !== 'NONE';

        if (isPromotion(selectedSquare, target)) {
            showPromotionPopup(cell, source, target, isCapture);
            return;
        }
        sendMove(source, target, null, isCapture);
    }
}

function selectPiece(cell) {
    selectedSquare = cell;
    cell.classList.add('selected');
    const source = cell.getAttribute('data-coord');
    fetch(`/movable?source=${source}`)
        .then(res => res.json())
        .then(paths => {
            paths.forEach(coord => {
                const targetCell = document.querySelector(`[data-coord="${coord}"]`);
                if (targetCell) targetCell.classList.add('target-square');
            });
        });
}

function clearSelection() {
    const existingPopup = document.querySelector('.promotion-popup');
    if (existingPopup) existingPopup.remove();
    if (selectedSquare) {
        selectedSquare.classList.remove('selected');
        selectedSquare = null;
    }
    document.querySelectorAll('.target-square').forEach(cell => {
        cell.classList.remove('target-square');
    });
}

// 6. Server Communication
function sendMove(source, target, promotion, isCapture) {
    const params = new URLSearchParams({ source: source, target: target });
    if (promotion) params.append('promotion', promotion);

    fetch('/move', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: params
    })
    .then(res => res.json())
    .then(data => {
        if (data.code === 'ERROR') {
            alert("⚠️ " + data.message);
        } else {
            updateBoard(data.board, data.team);
            playMoveSound(isCapture);

            if (data.gameOver) {
                showGameOverModal(data.winner);
            } else {
                updateTurn(data.turn);
                refreshBoardState(data.turn);
            }
        }
        clearSelection();
    })
    .catch(e => console.error(e));
}

function restartGame() {
    fetch('/restart', { method: 'POST' }).then(() => window.location.reload());
}

// 7. UI Helpers (Popup, Modal)
function isPromotion(sourceCell, targetCoord) {
    const type = sourceCell.getAttribute('data-type');
    if (type !== 'PAWN') return false;
    const rank = targetCoord.charAt(1);
    return rank === '1' || rank === '8';
}

function showPromotionPopup(targetCell, source, target, isCapture) {
    const popup = document.createElement('div');
    popup.className = 'promotion-popup';

    const rank = target.charAt(1);
    if (rank === '8') popup.style.top = '0';
    else popup.style.bottom = '0';

    const isWhite = document.getElementById('board-frame').classList.contains('turn-white');
    const colorPrefix = isWhite ? 'WHITE_' : 'BLACK_';
    const pieces = ['QUEEN', 'KNIGHT', 'ROOK', 'BISHOP'];

    pieces.forEach(type => {
        const btn = document.createElement('button');
        const imgUrl = PIECE_IMAGES[colorPrefix + type];
        btn.innerHTML = `<img src="${imgUrl}">`;
        btn.onclick = (e) => { e.stopPropagation(); sendMove(source, target, type, isCapture); };
        popup.appendChild(btn);
    });

    const closeBtn = document.createElement('button');
    closeBtn.className = 'close-btn'; closeBtn.innerText = '✕';
    closeBtn.onclick = (e) => { e.stopPropagation(); clearSelection(); };
    popup.appendChild(closeBtn);

    targetCell.appendChild(popup);
}

function showGameOverModal(winner) {
    const modal = document.getElementById('game-over-modal');
    const winnerText = document.getElementById('winner-text');
    const reasonText = document.getElementById('game-over-reason');
    if (winner === 'WHITE') {
        winnerText.innerText = "White Wins!";
        winnerText.style.color = "#ffb700";
        reasonText.innerText = "Checkmate";
    } else if (winner === 'BLACK') {
        winnerText.innerText = "Black Wins!";
        winnerText.style.color = "#a8c0ff";
        reasonText.innerText = "Checkmate";
    } else if (winner === 'DRAW') {
        winnerText.innerText = "Draw!";
        winnerText.style.color = "#aaa";
        reasonText.innerText = "50-Move Rule";
    }
    modal.classList.remove('hidden');
}
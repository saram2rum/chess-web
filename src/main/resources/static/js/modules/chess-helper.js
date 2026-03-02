// ============================================
// chess.js 래퍼 - 유효 이동 하이라이트용
// 서버(Java)가 실제 이동 검증, 여기는 힌트만
// ============================================

let chessGame = null;

const FEN_PIECE = {
    'WHITE_PAWN': 'P', 'WHITE_KNIGHT': 'N', 'WHITE_BISHOP': 'B', 'WHITE_ROOK': 'R', 'WHITE_QUEEN': 'Q', 'WHITE_KING': 'K',
    'BLACK_PAWN': 'p', 'BLACK_KNIGHT': 'n', 'BLACK_BISHOP': 'b', 'BLACK_ROOK': 'r', 'BLACK_QUEEN': 'q', 'BLACK_KING': 'k'
};

function initChessGame() {
    if (typeof Chess === 'undefined') return false;
    chessGame = new Chess();
    return true;
}

function syncChessFromMove(source, target, promotion) {
    if (!chessGame || typeof Chess === 'undefined') return false;
    const opt = { from: source, to: target };
    if (promotion) opt.promotion = promotion.charAt(0).toLowerCase();
    return chessGame.move(opt) !== null;
}

function toPieceKey(v) {
    if (typeof v === 'string') return v;
    if (v && v.team && v.type) return v.team + '_' + v.type;
    return null;
}

function buildFenFromSnapshot(snapshot, activeColor) {
    const parts = [];
    for (let rank = 8; rank >= 1; rank--) {
        let row = '', empty = 0;
        for (let file = 0; file < 8; file++) {
            const coord = String.fromCharCode(97 + file) + rank;
            const pieceKey = toPieceKey(snapshot[coord]);
            if (!pieceKey) {
                empty++;
            } else {
                if (empty > 0) { row += empty; empty = 0; }
                row += FEN_PIECE[pieceKey] || '';
            }
        }
        if (empty > 0) row += empty;
        parts.push(row);
    }
    const color = (activeColor === 'WHITE') ? 'w' : 'b';
    return parts.join('/') + ' ' + color + ' KQkq - 0 1';
}

function syncChessFromSnapshot(snapshot, activeColor) {
    if (!chessGame || typeof Chess === 'undefined') return false;
    try {
        const fen = buildFenFromSnapshot(snapshot, activeColor);
        return chessGame.load(fen);
    } catch (e) {
        return false;
    }
}

const FEN_TYPE = { p: 'PAWN', n: 'KNIGHT', b: 'BISHOP', r: 'ROOK', q: 'QUEEN', k: 'KING' };

/** 서버 FEN으로 chess.js 동기화. updateDom=true면 DOM도 갱신 (boardSnapshot 없을 때 폴백) */
function syncChessFromFen(fen, updateDom) {
    if (!chessGame || typeof Chess === 'undefined' || !fen) return false;
    try {
        if (!chessGame.load(fen)) return false;
        if (updateDom && typeof updateCell === 'function') syncBoardFromFen();
        return true;
    } catch (e) {
        return false;
    }
}

/** chess.js board()로 DOM 갱신 (fen만 받았을 때 폴백용) */
function syncBoardFromFen() {
    if (!chessGame) return;
    const board = chessGame.board();
    document.querySelectorAll('.chess-board td').forEach(cell => {
        cell.setAttribute('data-team', 'NONE');
        cell.setAttribute('data-type', 'NONE');
        const oldImg = cell.querySelector('.piece-img');
        if (oldImg) cell.removeChild(oldImg);
    });
    for (let row = 0; row < 8; row++) {
        for (let col = 0; col < 8; col++) {
            const piece = board[row][col];
            if (!piece) continue;
            const coord = String.fromCharCode(97 + col) + (8 - row);
            const team = piece.color === 'w' ? 'WHITE' : 'BLACK';
            const type = FEN_TYPE[piece.type] || 'PAWN';
            const cell = document.querySelector(`[data-coord="${coord}"]`);
            if (cell) updateCell(cell, team, type);
        }
    }
}

function getLegalMovesFromChess(square) {
    if (!chessGame || typeof Chess === 'undefined') return [];
    try {
        const moves = chessGame.moves({ square: square, verbose: true });
        return moves ? moves.map(m => m.to) : [];
    } catch (e) {
        return [];
    }
}

function isChessReady() {
    return chessGame !== null && typeof Chess !== 'undefined';
}

// ============================================
// 잡은 기물 + 점수 차이 (Material)
// 체스판과 동일한 PIECE_IMAGES 경로 사용
// ============================================
const PIECE_VALUES = { p: 1, n: 3, b: 3, r: 5, q: 9, k: 0 };
const INITIAL_COUNTS = { p: 8, n: 2, b: 2, r: 2, q: 1, k: 1 };
const FEN_TO_TYPE = { p: 'PAWN', n: 'KNIGHT', b: 'BISHOP', r: 'ROOK', q: 'QUEEN', k: 'KING' };

/** chessGame.board()에서 각 진영별 기물 개수 추출 */
function getPieceCountsFromChess() {
    const counts = { w: { p: 0, n: 0, b: 0, r: 0, q: 0, k: 0 }, b: { p: 0, n: 0, b: 0, r: 0, q: 0, k: 0 } };
    if (!chessGame) return counts;
    const board = chessGame.board();
    for (let r = 0; r < 8; r++) {
        for (let c = 0; c < 8; c++) {
            const p = board[r]?.[c];
            if (p && p.type && counts[p.color]) {
                counts[p.color][p.type] = (counts[p.color][p.type] || 0) + 1;
            }
        }
    }
    return counts;
}

/** 잡힌 기물: initial - current (각 진영이 잃은 기물) */
function getCapturedPieces(counts) {
    const captured = { w: [], b: [] };
    for (const color of ['w', 'b']) {
        for (const [type, initial] of Object.entries(INITIAL_COUNTS)) {
            const current = counts[color][type] ?? 0;
            const lost = Math.max(0, initial - current);
            for (let i = 0; i < lost; i++) {
                captured[color].push(type);
            }
        }
    }
    return captured;
}

/** 머티리얼 점수 (K 제외) */
function getMaterialScore(counts) {
    let score = 0;
    for (const [type, val] of Object.entries(PIECE_VALUES)) {
        if (type === 'k') continue;
        score += (counts.w[type] ?? 0) * val;
        score -= (counts.b[type] ?? 0) * val;
    }
    return score; // >0 = White 유리, <0 = Black 유리
}

/** 잡은 기물 UI + 점수 차이 갱신 (1v1/AI 공통) */
function updateCapturedPiecesUI() {
    if (typeof myColor === 'undefined') return;
    const counts = getPieceCountsFromChess();
    const captured = getCapturedPieces(counts);
    const materialDiff = getMaterialScore(counts);

    // 상대가 잡은 기물 = 내가 잃은 기물 (내 색 아이콘)
    const opponentCaptured = captured[myColor === 'WHITE' ? 'w' : 'b'];
    // 내가 잡은 기물 = 상대가 잃은 기물 (상대 색 아이콘)
    const myCaptured = captured[myColor === 'WHITE' ? 'b' : 'w'];

    const oppPrefix = myColor === 'WHITE' ? 'w' : 'b';
    const myCapPrefix = myColor === 'WHITE' ? 'b' : 'w';

    const oppEl = document.getElementById('captured-opponent');
    const myEl = document.getElementById('captured-me');
    const oppBadge = document.getElementById('material-opponent');
    const myBadge = document.getElementById('material-me');

    const renderPieces = (list, colorPrefix) => {
        const frag = document.createDocumentFragment();
        const colorKey = colorPrefix === 'w' ? 'WHITE' : 'BLACK';
        list.forEach(t => {
            const key = colorKey + '_' + (FEN_TO_TYPE[t] || 'PAWN');
            const src = typeof PIECE_IMAGES !== 'undefined' ? PIECE_IMAGES[key] : `/images/${colorPrefix}_${t}.svg`;
            const div = document.createElement('div');
            div.className = 'captured-piece';
            const img = document.createElement('img');
            img.src = src;
            img.alt = t;
            img.className = 'captured-piece-img';
            div.appendChild(img);
            frag.appendChild(div);
        });
        return frag;
    };

    if (oppEl) {
        oppEl.innerHTML = '';
        oppEl.appendChild(renderPieces(opponentCaptured, oppPrefix));
    }
    if (myEl) {
        myEl.innerHTML = '';
        myEl.appendChild(renderPieces(myCaptured, myCapPrefix));
    }

    // 점수 차이: 유리한 쪽에만 표시
    const myAdvantage = myColor === 'WHITE' ? materialDiff : -materialDiff;
    if (oppBadge) {
        oppBadge.textContent = myAdvantage < 0 ? '+' + (-myAdvantage) : '';
        oppBadge.classList.toggle('empty', myAdvantage >= 0);
    }
    if (myBadge) {
        myBadge.textContent = myAdvantage > 0 ? '+' + myAdvantage : '';
        myBadge.classList.toggle('empty', myAdvantage <= 0);
    }
}

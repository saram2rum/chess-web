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

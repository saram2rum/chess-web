// ============================================
// Board Rendering, Interaction & Drag-and-Drop
// ============================================

// 🔥 [신규] 드래그 직후 클릭 방지 플래그
let justFinishedDrag = false;

function createEmptyBoard() {
    const board = document.getElementById('chess-board');
    board.innerHTML = '';
    
    for (let y = 7; y >= 0; y--) {
        const row = document.createElement('tr');
        for (let x = 0; x < 8; x++) {
            const cell = document.createElement('td');
            const coord = String.fromCharCode(97 + x) + (y + 1);
            const isWhiteSquare = (x + y) % 2 === 1;
            
            cell.className = isWhiteSquare ? 'white-square' : 'black-square';
            cell.setAttribute('data-coord', coord);
            cell.setAttribute('data-team', 'NONE');
            cell.setAttribute('data-type', 'NONE');
            cell.onclick = function() { onClickSquare(this); };
            
            // 좌표 추가
            const isRankEdge = (myColor === 'BLACK') ? (x === 7) : (x === 0);
            if (isRankEdge) {
                const rankSpan = document.createElement('span');
                rankSpan.className = 'coord-rank';
                rankSpan.innerText = (y + 1);
                cell.appendChild(rankSpan);
            }
            
            const isFileEdge = (myColor === 'BLACK') ? (y === 7) : (y === 0);
            if (isFileEdge) {
                const fileSpan = document.createElement('span');
                fileSpan.className = 'coord-file';
                fileSpan.innerText = String.fromCharCode(97 + x);
                cell.appendChild(fileSpan);
            }
            
            row.appendChild(cell);
        }
        board.appendChild(row);
    }
}

function initializeBoard() {
    const initialSetup = {
        'a1': {team: 'WHITE', type: 'ROOK'}, 'b1': {team: 'WHITE', type: 'KNIGHT'},
        'c1': {team: 'WHITE', type: 'BISHOP'}, 'd1': {team: 'WHITE', type: 'QUEEN'},
        'e1': {team: 'WHITE', type: 'KING'}, 'f1': {team: 'WHITE', type: 'BISHOP'},
        'g1': {team: 'WHITE', type: 'KNIGHT'}, 'h1': {team: 'WHITE', type: 'ROOK'},
        'a2': {team: 'WHITE', type: 'PAWN'}, 'b2': {team: 'WHITE', type: 'PAWN'},
        'c2': {team: 'WHITE', type: 'PAWN'}, 'd2': {team: 'WHITE', type: 'PAWN'},
        'e2': {team: 'WHITE', type: 'PAWN'}, 'f2': {team: 'WHITE', type: 'PAWN'},
        'g2': {team: 'WHITE', type: 'PAWN'}, 'h2': {team: 'WHITE', type: 'PAWN'},
        'a8': {team: 'BLACK', type: 'ROOK'}, 'b8': {team: 'BLACK', type: 'KNIGHT'},
        'c8': {team: 'BLACK', type: 'BISHOP'}, 'd8': {team: 'BLACK', type: 'QUEEN'},
        'e8': {team: 'BLACK', type: 'KING'}, 'f8': {team: 'BLACK', type: 'BISHOP'},
        'g8': {team: 'BLACK', type: 'KNIGHT'}, 'h8': {team: 'BLACK', type: 'ROOK'},
        'a7': {team: 'BLACK', type: 'PAWN'}, 'b7': {team: 'BLACK', type: 'PAWN'},
        'c7': {team: 'BLACK', type: 'PAWN'}, 'd7': {team: 'BLACK', type: 'PAWN'},
        'e7': {team: 'BLACK', type: 'PAWN'}, 'f7': {team: 'BLACK', type: 'PAWN'},
        'g7': {team: 'BLACK', type: 'PAWN'}, 'h7': {team: 'BLACK', type: 'PAWN'}
    };
    
    for (const [coord, piece] of Object.entries(initialSetup)) {
        const cell = document.querySelector(`[data-coord="${coord}"]`);
        if (cell) updateCell(cell, piece.team, piece.type);
    }
    
    refreshBoardState();
    
    // 🔥 [신규] 전역 드래그 청소 리스너 등록
    document.addEventListener('mouseleave', cleanupDragState);
    document.addEventListener('contextmenu', cleanupDragState);
    window.addEventListener('blur', cleanupDragState);
}

function updateCell(cell, team, type) {
    cell.setAttribute('data-team', team);
    cell.setAttribute('data-type', type);
    
    // 1. 기존 기물 이미지 제거
    const oldImg = cell.querySelector('.piece-img');
    if (oldImg) {
        cell.removeChild(oldImg);
    }
    
    // 2. 새 기물 이미지 추가
    if (type !== 'NONE') {
        const key = team + '_' + type;
        const imgUrl = PIECE_IMAGES[key];
        if (imgUrl) {
            const img = document.createElement('img');
            img.src = imgUrl;
            img.className = 'piece-img';
            
            // 🆕 드래그 이벤트 연결
            img.addEventListener('mousedown', handleDragStart);
            
            // 🔥 [수정] 브라우저 기본 드래그 원천 차단
            img.setAttribute('draggable', 'false');
            img.ondragstart = function(e) { e.preventDefault(); return false; };
            img.oncontextmenu = function(e) { e.preventDefault(); return false; };
            
            cell.appendChild(img);
        }
    }
}

function onClickSquare(cell) {
    // 🔥 드래그 직후 클릭 무시 (깜빡임 방지)
    if (justFinishedDrag) return;
    
    // 드래그 중이었다면 클릭 무시 (안전장치)
    if (document.querySelector('.dragging-ghost')) return;
    
    if (currentTurn !== myColor) return;
    
    if (selectedSquare == null) {
        const team = cell.getAttribute('data-team');
        if (team !== myColor) return;
        selectPiece(cell);
    } else {
        const team = cell.getAttribute('data-team');
        if (team === myColor) {
            if (cell === selectedSquare) {
                clearSelection();
            } else {
                clearSelection();
                selectPiece(cell);
            }
            return;
        }
        
        const source = selectedSquare.getAttribute('data-coord');
        const target = cell.getAttribute('data-coord');
        const isTargetValid = cell.classList.contains('target-square');
        const chessFailed = typeof isChessReady === 'function' && !isChessReady();
        if (!isTargetValid && !chessFailed) {
            clearSelection();
            return;
        }
        sendMove(source, target);
    }
}

function selectPiece(cell) {
    selectedSquare = cell;
    cell.classList.add('selected');
    const coord = cell.getAttribute('data-coord');
    const validMoves = (typeof getLegalMovesFromChess === 'function' && isChessReady())
        ? getLegalMovesFromChess(coord) : [];
    applyValidMovesHighlight(validMoves);
}

function applyValidMovesHighlight(validMoves) {
    if (!selectedSquare) return;
    const selectedType = selectedSquare.getAttribute('data-type');
    const sourceCoord = selectedSquare.getAttribute('data-coord');
    const selectedFile = sourceCoord.charCodeAt(0);
    validMoves.forEach(coord => {
        const targetCell = document.querySelector(`[data-coord="${coord}"]`);
        if (targetCell) {
            targetCell.classList.add('target-square');
            const targetType = targetCell.getAttribute('data-type');
            let isCapture = (targetType !== 'NONE');
            if (!isCapture && selectedType === 'PAWN') {
                const targetFile = coord.charCodeAt(0);
                if (Math.abs(targetFile - selectedFile) === 1) isCapture = true;
            }
            if (isCapture) targetCell.classList.add('target-capture');
        }
    });
}

function clearSelection() {
    if (selectedSquare) {
        selectedSquare.classList.remove('selected');
        selectedSquare = null;
    }
    document.querySelectorAll('.target-square').forEach(cell => {
        cell.classList.remove('target-square');
        cell.classList.remove('target-capture'); // 🔥 캡처 클래스도 제거
    });
}

function refreshBoardState() {
    document.querySelectorAll('.chess-board td').forEach(cell => {
        cell.classList.remove('my-piece');
        if (cell.getAttribute('data-team') === myColor) {
            cell.classList.add('my-piece');
        }
    });
}

function handleMovablePositions(response) {
    console.log('⚠️ 서버로부터 이동 가능 위치를 받았지만 무시합니다 (클라이언트 계산 사용)');
}

/**
 * boardSnapshot으로 보드 동기화 (캐슬링/앙파상/프로모션 모두 반영)
 * [최적화] 변경된 칸만 updateCell 호출 - 최종 상태는 기존과 동일, DOM 작업만 감소
 */
function syncBoardWithSnapshot(snapshot) {
    if (!snapshot || typeof snapshot !== 'object') return;
    
    const cells = document.querySelectorAll('.chess-board td');
    let updateCount = 0;
    
    cells.forEach(cell => {
        const coord = cell.getAttribute('data-coord');
        if (!coord) return;
        
        const pieceKey = snapshot[coord];
        let desiredTeam, desiredType;
        if (pieceKey && typeof pieceKey === 'string' && pieceKey.includes('_')) {
            const parts = pieceKey.split('_');
            desiredTeam = parts[0];
            desiredType = parts[1];
        } else {
            desiredTeam = 'NONE';
            desiredType = 'NONE';
        }
        
        const currentTeam = cell.getAttribute('data-team') || 'NONE';
        const currentType = cell.getAttribute('data-type') || 'NONE';
        
        if (currentTeam !== desiredTeam || currentType !== desiredType) {
            updateCell(cell, desiredTeam, desiredType);
            updateCount++;
        }
    });
    
    if (updateCount > 0) {
        console.log('✅ 보드 동기화:', updateCount, '칸 변경 (캐슬링/앙파상 반영)');
    }
}

function updateBoardFromMove(result) {
    const sourceCell = document.querySelector(`[data-coord="${result.source}"]`);
    const targetCell = document.querySelector(`[data-coord="${result.target}"]`);
    
    if (sourceCell && targetCell) {
        const team = sourceCell.getAttribute('data-team');
        let type = sourceCell.getAttribute('data-type');
        
        if (result.promotedPiece) {
            type = result.promotedPiece;
            console.log('🎯 [PROMOTION] 기물 변경:', result.promotedPiece);
        }
        
        updateCell(targetCell, team, type);
        updateCell(sourceCell, 'NONE', 'NONE');
        
        if (result.capturedPiecePosition) {
            console.log('⚔️ [EN_PASSANT] 앙파상 기물 삭제:', result.capturedPiecePosition);
            const capturedCell = document.querySelector(`[data-coord="${result.capturedPiecePosition}"]`);
            if (capturedCell) {
                updateCell(capturedCell, 'NONE', 'NONE');
            }
        }
    }
}

function highlightLastMove(source, target) {
    document.querySelectorAll('.last-move').forEach(el => {
        el.classList.remove('last-move');
    });
    
    const sourceCell = document.querySelector(`[data-coord="${source}"]`);
    const targetCell = document.querySelector(`[data-coord="${target}"]`);
    
    if (sourceCell) sourceCell.classList.add('last-move');
    if (targetCell) targetCell.classList.add('last-move');
    
    lastMoveSource = source;
    lastMoveTarget = target;
}

/**
 * 체크 시 킹 칸 배경 강조 (서버 isCheck 활용)
 * @param {string|null} kingCoord 킹 위치 (e1, e8 등) 또는 null이면 제거
 */
function highlightCheck(kingCoord) {
    document.querySelectorAll('.square-in-check').forEach(el => el.classList.remove('square-in-check'));
    if (kingCoord) {
        const cell = document.querySelector(`[data-coord="${kingCoord}"]`);
        if (cell) cell.classList.add('square-in-check');
    }
}

/**
 * 체크 상태일 때 nextTurn 쪽 킹 위치 찾기
 * @param {object} result handleMoveResult의 result (boardSnapshot, nextTurn 포함)
 * @returns {string|null} 킹 좌표 또는 null
 */
function findKingInCheck(result) {
    const nextTurn = result.nextTurn;
    if (!nextTurn) return null;

    // boardSnapshot 우선 (coord -> "WHITE_KING" 형태)
    const snapshot = result.boardSnapshot;
    if (snapshot) {
        for (const [coord, pieceKey] of Object.entries(snapshot)) {
            if (pieceKey === nextTurn + '_KING') return coord;
        }
    }

    // snapshot 없으면 DOM에서 검색
    const cells = document.querySelectorAll('.chess-board td[data-coord]');
    for (const cell of cells) {
        if (cell.getAttribute('data-team') === nextTurn && cell.getAttribute('data-type') === 'KING') {
            return cell.getAttribute('data-coord');
        }
    }
    return null;
}

// 🖱️ 드래그 앤 드롭 로직

// 🔥 [신규] 드래그 상태 강제 초기화 (청소부)
function cleanupDragState() {
    isDragging = false;
    dragStartCoord = null;
    
    // 이벤트 리스너 제거
    document.removeEventListener('mousemove', handleDragMove);
    document.removeEventListener('mouseup', handleDragEnd);
    
    // Ghost 제거 (모든 잔존물 삭제)
    document.querySelectorAll('.dragging-ghost').forEach(el => el.remove());
    dragGhost = null;
    
    // 원본 투명도 복구
    if (dragSourceElem) {
        dragSourceElem.style.opacity = '1';
        dragSourceElem = null;
    }
}

function handleDragStart(e) {
    cleanupDragState();
    
    if (currentTurn !== myColor) return;
    
    const cell = e.target.closest('td');
    if (!cell) return;
    
    const team = cell.getAttribute('data-team');
    if (team !== myColor) return;
    
    isDragging = false;
    dragStartCoord = cell.getAttribute('data-coord');
    dragSourceElem = e.target;
    
    startX = e.clientX;
    startY = e.clientY;
    
    document.addEventListener('mousemove', handleDragMove);
    document.addEventListener('mouseup', handleDragEnd);
}

// 🔥 [최적화] requestAnimationFrame 사용
let isFrameRequested = false;

function handleDragMove(e) {
    if (!isDragging) {
        const diffX = Math.abs(e.clientX - startX);
        const diffY = Math.abs(e.clientY - startY);
        
        if (diffX > 5 || diffY > 5) {
            startRealDrag(e);
        }
        return;
    }
    
    // 드래그 중일 때는 프레임 제한으로 부드럽게
    if (!isFrameRequested) {
        isFrameRequested = true;
        requestAnimationFrame(() => {
            moveGhost(e.clientX, e.clientY);
            isFrameRequested = false;
        });
    }
}

function startRealDrag(e) {
    document.querySelectorAll('.dragging-ghost').forEach(el => el.remove());
    
    isDragging = true;
    
    const rect = dragSourceElem.getBoundingClientRect();
    dragGhost = dragSourceElem.cloneNode(true);
    dragGhost.classList.add('dragging-ghost');
    dragGhost.style.width = rect.width + 'px';
    dragGhost.style.height = rect.height + 'px';
    
    moveGhost(e.clientX, e.clientY);
    document.body.appendChild(dragGhost);
    
    dragSourceElem.style.opacity = '0';
    
    const cell = dragSourceElem.closest('td');
    // 이미 선택된 상태가 아니면 선택 (서버 요청)
    // 이미 선택된 상태면 요청 안 함 (깜빡임 방지 1차)
    if (!selectedSquare || selectedSquare !== cell) {
        if (selectedSquare) clearSelection();
        selectPiece(cell);
    }
}

function handleDragEnd(e) {
    if (!isDragging) {
        cleanupDragState();
        return;
    }
    
    // 🔥 [핵심] 드래그 종료 직후 클릭 이벤트 방지
    justFinishedDrag = true;
    setTimeout(() => { justFinishedDrag = false; }, 50);
    
    const elemBelow = document.elementFromPoint(e.clientX, e.clientY);
    const targetCell = elemBelow ? elemBelow.closest('td') : null;
    
    let moveSuccessful = false;
    let isSameSquare = false;
    
    if (targetCell) {
        const targetCoord = targetCell.getAttribute('data-coord');
        
        if (targetCoord === dragStartCoord) {
            isSameSquare = true;
        } else if (targetCell.classList.contains('target-square') ||
            (typeof isChessReady === 'function' && !isChessReady())) {
            sendMove(dragStartCoord, targetCoord);
            moveSuccessful = true;
        }
    }
    
    // 🔥 [핵심] 제자리에 놓았거나 이동에 성공했으면 선택 해제하지 않음
    // (제자리는 선택 유지, 이동 성공은 서버 응답 후 자동 갱신됨)
    // 단, 이동 실패했고 제자리도 아니면 (이상한 곳 드랍) 선택 해제
    if (!moveSuccessful && !isSameSquare) {
        clearSelection();
    }
    
    cleanupDragState();
}

function moveGhost(pageX, pageY) {
    if (!dragGhost) return;
    const halfWidth = parseFloat(dragGhost.style.width) / 2;
    const halfHeight = parseFloat(dragGhost.style.height) / 2;
    
    dragGhost.style.left = (pageX - halfWidth) + 'px';
    dragGhost.style.top = (pageY - halfHeight) + 'px';
}

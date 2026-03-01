// ============================================
// 1. Assets & Configuration
// ============================================
const PIECE_IMAGES = {
    'WHITE_PAWN':   '/images/w_p.svg', 'WHITE_ROOK':   '/images/w_r.svg',
    'WHITE_KNIGHT': '/images/w_n.svg', 'WHITE_BISHOP': '/images/w_b.svg',
    'WHITE_QUEEN':  '/images/w_q.svg', 'WHITE_KING':   '/images/w_k.svg',
    'BLACK_PAWN':   '/images/b_p.svg', 'BLACK_ROOK':   '/images/b_r.svg',
    'BLACK_KNIGHT': '/images/b_n.svg', 'BLACK_BISHOP': '/images/b_b.svg',
    'BLACK_QUEEN':  '/images/b_q.svg', 'BLACK_KING':   '/images/b_k.svg'
};

// Lichess SFX sound pack (AGPLv3+ by Enigmahack)
const SOUNDS = {
    MOVE: new Audio('/sounds/move.mp3'),
    CHECK: new Audio('/sounds/check.mp3'),
    CHECKMATE: new Audio('/sounds/checkmate.mp3'),
    VICTORY: new Audio('/sounds/victory.mp3'),
    DEFEAT: new Audio('/sounds/defeat.mp3'),
    DRAW: new Audio('/sounds/draw.mp3'),
    LOW_TIME: new Audio('/sounds/lowtime.mp3')
};
// 🔊 사전 로딩 (첫 재생 시 지연 방지)
Object.values(SOUNDS).forEach(a => a.load());

// ============================================
// 2. Global State Variables
// ============================================
let stompClient = null;
let roomId = null;
let myColor = null;
let myRole = null; // "HOST" or "GUEST"
let currentTurn = 'WHITE';
let selectedSquare = null;
let isHost = false;
let myUserId = null; // 🆕 LocalStorage 기반 고유 사용자 ID
let myNickname = null; // 🆕 사용자 닉네임
let justCreatedRoom = false; // 🆕 방금 방을 만들었는지 여부 (재접속 방지용)

// 🖱️ 드래그 앤 드롭 관련 변수
let dragGhost = null;       // 드래그 중인 유령 이미지 요소
let dragStartCoord = null;  // 드래그 시작 좌표
let isDragging = false;     // 실제 드래그가 시작되었는지 여부
let startX = 0, startY = 0; // 드래그 시작점 (거리 계산용)
let dragSourceElem = null;  // 드래그 시작된 요소

// 🔦 이전 수 하이라이트 관련 변수
let lastMoveSource = null;
let lastMoveTarget = null;

// ⏱️ 타이머 관련 변수
let whiteTime = 0;  // 밀리초
let blackTime = 0;  // 밀리초
let timerInterval = null;  // 타이머 업데이트 인터벌

// 🎮 게임 진행 상태 (화면 전환 제어용)
let isGameRunning = false;

// 🔊 낙관적 이동 추적 (내 수 소리 이중 재생 방지)
let lastOptimisticMove = null;  // { source, target } | null

// ⏱️ LowTime 소리 1회 재생 (30초 미만 진입 시)
let lowTimeSoundPlayed = false;

// ============================================
// 3. Room ID Storage (재접속 지원)
// ============================================
/** 탭 세션 동안만 유지 (탭 닫으면 삭제 → 재접속 시 홈부터) */
function saveCurrentRoomId(id) {
    if (id) sessionStorage.setItem('chessCurrentRoomId', id);
}

function getCurrentRoomId() {
    return sessionStorage.getItem('chessCurrentRoomId');
}

function clearCurrentRoomId() {
    sessionStorage.removeItem('chessCurrentRoomId');
}

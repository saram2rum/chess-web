// ============================================
// Main Entry Point
// ============================================

window.onload = function() {
    console.log('🚀 Chess Application Starting...');
    
    // 🆕 재접속 시도 중이라면 로딩 화면 표시 (sessionStorage: 탭 열려있을 때만)
    if (typeof getCurrentRoomId === 'function' && getCurrentRoomId()) {
        const overlay = document.getElementById('reconnect-overlay');
        if (overlay) overlay.classList.remove('hidden');
        // 5초 내 로비/게임 응답 없으면 홈으로 (방 없음 등)
        setTimeout(function() {
            if (overlay && !overlay.classList.contains('hidden')) {
                console.log('⏱️ 재접속 타임아웃 → 홈으로');
                if (typeof goToHomeScreen === 'function') goToHomeScreen();
                if (typeof clearCurrentRoomId === 'function') clearCurrentRoomId();
                if (typeof showLobbyStatus === 'function') showLobbyStatus('Could not reconnect. Please create or join a new room.', 'error');
            }
        }, 5000);
    }
    
    initializeUser(); // game.js
    initializeLobbyButtons(); // game.js
    connectWebSocket(); // network.js
};

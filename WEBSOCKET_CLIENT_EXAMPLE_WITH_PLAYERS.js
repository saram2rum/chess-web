// WebSocket 연결 예제 (JavaScript) - 플레이어 구분 기능 포함
// 필요한 라이브러리:
// <script src="https://cdn.jsdelivr.net/npm/sockjs-client@1/dist/sockjs.min.js"></script>
// <script src="https://cdn.jsdelivr.net/npm/@stomp/stompjs@7/dist/stomp.umd.min.js"></script>

class ChessWebSocket {
    constructor() {
        this.stompClient = null;
        this.roomId = null;
        this.myColor = null;  // 내가 할당받은 색상 (WHITE 또는 BLACK)
        this.currentTurn = 'WHITE';  // 현재 턴
    }

    // WebSocket 연결
    connect(onConnected) {
        const socket = new SockJS('/ws-chess');
        this.stompClient = Stomp.over(socket);
        
        this.stompClient.connect({}, (frame) => {
            console.log('Connected: ' + frame);
            
            // 개인 메시지 수신을 위한 구독 설정
            this.stompClient.subscribe('/user/queue/join', (message) => {
                const response = JSON.parse(message.body);
                this.handleJoinResponse(response);
            });
            
            this.stompClient.subscribe('/user/queue/errors', (message) => {
                const error = JSON.parse(message.body);
                this.handleError(error);
            });
            
            if (onConnected) onConnected();
        }, (error) => {
            console.error('Connection error:', error);
        });
    }

    // 새 게임 방 생성
    createGame() {
        this.stompClient.subscribe('/topic/create', (message) => {
            const response = JSON.parse(message.body);
            this.roomId = response.roomId;
            console.log('✅ 게임 방 생성됨:', this.roomId);
            
            // 방 생성 후 자동으로 입장 시도
            this.joinRoom(this.roomId);
        });
        
        this.stompClient.send('/app/create', {}, JSON.stringify({}));
    }

    // 게임 방 입장
    joinRoom(roomId) {
        this.roomId = roomId;
        
        // 게임 상태 업데이트 구독
        this.stompClient.subscribe('/topic/game/' + roomId, (message) => {
            const moveResult = JSON.parse(message.body);
            this.handleMoveResult(moveResult);
        });
        
        // 에러 메시지 구독
        this.stompClient.subscribe('/topic/errors/' + roomId, (message) => {
            const error = JSON.parse(message.body);
            this.handleError(error);
        });
        
        // 입장 요청 전송
        this.stompClient.send('/app/join/' + roomId, {}, JSON.stringify({}));
        
        console.log('🚪 방 입장 요청:', roomId);
    }

    // 입장 응답 처리
    handleJoinResponse(response) {
        this.myColor = response.assignedColor;
        console.log('✅ 입장 성공!');
        console.log('   - 방 ID:', response.roomId);
        console.log('   - 내 색상:', this.myColor);
        console.log('   - 메시지:', response.message);
        
        // UI 업데이트
        this.updatePlayerInfo();
        
        // 알림 표시
        alert(response.message);
    }

    // 체스 기물 이동
    move(source, target) {
        if (!this.roomId) {
            console.error('❌ 먼저 방에 입장해주세요!');
            return;
        }
        
        if (!this.myColor) {
            console.error('❌ 아직 색상이 할당되지 않았습니다!');
            return;
        }
        
        // 내 턴인지 확인 (클라이언트 측 미리 검증)
        if (this.currentTurn !== this.myColor) {
            alert('⏰ 당신의 차례가 아닙니다! (현재: ' + this.currentTurn + ')');
            return;
        }
        
        const moveData = {
            source: source,  // 예: "e2"
            target: target   // 예: "e4"
        };
        
        this.stompClient.send(
            '/app/move/' + this.roomId,
            {},
            JSON.stringify(moveData)
        );
        
        console.log('📤 이동 요청:', source, '->', target);
    }

    // 이동 결과 처리
    handleMoveResult(result) {
        console.log('📥 이동 결과:', result);
        console.log('   - 이동:', result.source, '->', result.target);
        console.log('   - 다음 턴:', result.nextTurn);
        console.log('   - 메시지:', result.message);
        
        // 현재 턴 업데이트
        this.currentTurn = result.nextTurn;
        
        // 게임 상태 알림
        if (result.isCheckMate) {
            alert('🎉 체크메이트! 게임 종료!');
        } else if (result.isDraw) {
            alert('🤝 무승부!');
        } else if (result.isCheck) {
            alert('⚠️ 체크!');
        }
        
        // UI 업데이트
        this.updateBoard(result);
        this.updateTurnIndicator();
    }

    // 에러 처리
    handleError(error) {
        console.error('❌ 에러:', error.error);
        console.error('   - 메시지:', error.message);
        alert('⚠️ ' + error.message);
    }

    // 플레이어 정보 UI 업데이트
    updatePlayerInfo() {
        console.log('🎨 플레이어 정보 업데이트:', this.myColor);
        // TODO: DOM 업데이트
        // document.getElementById('my-color').textContent = this.myColor;
    }

    // 턴 표시 UI 업데이트
    updateTurnIndicator() {
        const isMyTurn = (this.currentTurn === this.myColor);
        console.log(isMyTurn ? '✅ 내 차례입니다!' : '⏰ 상대방 차례입니다.');
        // TODO: DOM 업데이트
        // document.getElementById('turn-indicator').textContent = 
        //     isMyTurn ? '당신의 차례' : '상대방의 차례';
    }

    // 보드 UI 업데이트
    updateBoard(result) {
        console.log('♟️ 보드 업데이트:', result.source, '->', result.target);
        // TODO: 실제 체스판 UI 업데이트
    }

    // 연결 종료
    disconnect() {
        if (this.stompClient !== null) {
            this.stompClient.disconnect();
        }
        console.log('👋 연결이 종료되었습니다.');
    }
}

// ==========================================
// 사용 예제 1: 방 생성자 (첫 번째 플레이어)
// ==========================================
const player1 = new ChessWebSocket();

// 1. 연결
player1.connect(() => {
    console.log('플레이어1 연결 완료');
    
    // 2. 새 게임 생성
    player1.createGame();
    // -> 자동으로 입장되어 WHITE 색상 할당됨
});

// ==========================================
// 사용 예제 2: 방 참가자 (두 번째 플레이어)
// ==========================================
const player2 = new ChessWebSocket();

// 1. 연결
player2.connect(() => {
    console.log('플레이어2 연결 완료');
    
    // 2. 기존 방에 입장
    const roomId = '기존-방-ID-입력';  // player1이 생성한 roomId
    player2.joinRoom(roomId);
    // -> BLACK 색상 할당됨
});

// ==========================================
// 게임 플레이 예제
// ==========================================

// 몇 초 후에 플레이어1(WHITE)이 첫 수를 둠
setTimeout(() => {
    player1.move('e2', 'e4');  // WHITE 폰 전진
}, 3000);

// 이후 플레이어2(BLACK)가 응수
setTimeout(() => {
    player2.move('e7', 'e5');  // BLACK 폰 전진
}, 5000);

// 턴이 아닌 사람이 이동하려고 하면 에러
setTimeout(() => {
    player2.move('d7', 'd5');  // ❌ BLACK의 턴이 아님!
    // -> "당신의 차례가 아닙니다!" 에러 발생
}, 6000);




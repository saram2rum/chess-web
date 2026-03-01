// WebSocket 연결 예제 (JavaScript)

// 1. SockJS + STOMP 라이브러리 필요
// <script src="https://cdn.jsdelivr.net/npm/sockjs-client@1/dist/sockjs.min.js"></script>
// <script src="https://cdn.jsdelivr.net/npm/@stomp/stompjs@7/dist/stomp.umd.min.js"></script>

class ChessWebSocket {
    constructor() {
        this.stompClient = null;
        this.roomId = null;
    }

    // WebSocket 연결
    connect() {
        const socket = new SockJS('/ws-chess');
        this.stompClient = Stomp.over(socket);
        
        this.stompClient.connect({}, (frame) => {
            console.log('Connected: ' + frame);
        }, (error) => {
            console.error('Connection error:', error);
        });
    }

    // 새 게임 방 생성
    createGame() {
        this.stompClient.subscribe('/topic/create', (message) => {
            const response = JSON.parse(message.body);
            this.roomId = response.roomId;
            console.log('게임 방 생성됨:', this.roomId);
            
            // 방 생성 후 자동으로 구독
            this.joinRoom(this.roomId);
        });
        
        this.stompClient.send('/app/create', {}, JSON.stringify({}));
    }

    // 게임 방 입장 (구독)
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
        
        console.log('방에 입장했습니다:', roomId);
    }

    // 체스 기물 이동
    move(source, target) {
        if (!this.roomId) {
            console.error('먼저 방에 입장해주세요!');
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
    }

    // 이동 결과 처리
    handleMoveResult(result) {
        console.log('이동 결과:', result);
        console.log('메시지:', result.message);
        console.log('다음 턴:', result.nextTurn);
        
        if (result.isCheckMate) {
            alert('체크메이트! 게임 종료!');
        } else if (result.isDraw) {
            alert('무승부!');
        } else if (result.isCheck) {
            alert('체크!');
        }
        
        // 여기서 보드 UI를 업데이트하면 됩니다
        this.updateBoard(result);
    }

    // 에러 처리
    handleError(error) {
        console.error('에러:', error.error);
        alert('이동 실패: ' + error.message);
    }

    // 보드 UI 업데이트 (구현 필요)
    updateBoard(result) {
        // TODO: 실제 체스판 UI 업데이트 로직
        console.log('보드 업데이트 필요:', result.source, '->', result.target);
    }

    // 연결 종료
    disconnect() {
        if (this.stompClient !== null) {
            this.stompClient.disconnect();
        }
        console.log('연결이 종료되었습니다.');
    }
}

// 사용 예제
const chess = new ChessWebSocket();

// 1. 연결
chess.connect();

// 2-a. 새 게임 생성
setTimeout(() => {
    chess.createGame();
}, 1000);

// 2-b. 또는 기존 방에 입장
// setTimeout(() => {
//     chess.joinRoom('기존-방-ID');
// }, 1000);

// 3. 기물 이동
setTimeout(() => {
    chess.move('e2', 'e4');  // 폰 2칸 전진
}, 3000);

setTimeout(() => {
    chess.move('e7', 'e5');  // 상대방 폰 2칸 전진
}, 5000);




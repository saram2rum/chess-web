package chess.domain.game;

import chess.domain.board.Board;
import chess.domain.board.Position;
import chess.domain.piece.Color;
import chess.domain.piece.Piece;
import chess.domain.piece.Type;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChessGame {
    private final Board board;
    private Color currentTurn; // ⏱️ 핵심: 현재 턴을 기억하는 변수
    private int fullMoveNumber = 1; // FEN fullmove number (1부터 시작, 흑이 두면 +1)

    // 🚩 게임 진행 중인지 확인하는 플래그
    private boolean isRunning = true;
    
    // 무승부 사유 플래그
    private boolean isThreefoldDraw = false;
    private boolean isStaleMateDraw = false;

    // 3수 동형 판독기용 맵 (Key: PositionKey, Value: 등장 횟수)
    private final Map<String, Integer> positionHistory = new HashMap<>();

    // 게임 종료 선언
    public void endGame() {
        this.isRunning = false;
    }

    // 외부에서 게임 끝났는지 확인할 때 사용
    public boolean isRunning() {
        return isRunning;
    }

    // ChessGame.java 안에 추가
    public List<Position> calculateMovablePositions(Position source) {
        return board.calculateMovablePositions(source);
    }

    public ChessGame() {
        this.board = new Board();
        this.board.initialize(); // 게임 시작 시 기물 배치
        this.currentTurn = Color.WHITE; // 체스는 항상 흰색 먼저 시작
        this.fullMoveNumber = 1;
        
        // 초기 상태 기록 (1회)
        updatePositionHistory();
    }

    public void move(String sourceStr, String targetStr, Type promotionType) {
        if (!isRunning) {
            System.out.println("게임이 이미 종료되었습니다. 🚫");
            return;
        }

        // 🚨 여기서 통역사를 사용합니다!
        Position source = new Position(sourceStr);
        Position target = new Position(targetStr);

        // 🚨 여기서 에러가 나면 -> 함수가 즉시 중단되고 -> Application의 catch로 날아갑니다.
        // 따라서 아래의 "이동 성공", "nextTurn"은 자동으로 실행되지 않습니다. (턴 유지됨)
        board.move(source, target, currentTurn, promotionType);

        // FEN fullmove는 "흑이 두고 난 뒤" 증가
        if (currentTurn == Color.BLACK) {
            fullMoveNumber++;
        }

        // --- 성공했을 때만 아래 로직 실행 ---
        System.out.println("이동 성공: " + sourceStr + " -> " + targetStr);

        // 4. 승패/체크 판정 (체크메이트, 스테일메이트, 50수 무승부)
        checkGameStatus();

        if (!isRunning) return; // 이미 게임 끝났으면 중단

        // 5. 턴 넘기기
        nextTurn();
        
        // 6. 3수 동형 체크 (턴이 넘어간 후의 상태를 기록해야 함)
        checkThreefoldRepetition();
    }

    private void checkGameStatus() {
        Color opponent = currentTurn.opponent();

        // 50수 무승부 체크
        if (board.isFiftyMoveRule()) {
            System.out.println("🤝 50수 무승부 규칙! 무승부입니다. 🤝");
            endGame();
            return;
        }

        if (board.isChecked(opponent)) {
            if (board.isCheckMate(opponent)) {
                System.out.println("🎉 CHECKMATE! " + currentTurn + " 승리! 🎉");
                endGame();
            } else {
                System.out.println("🔥 CHECK! " + opponent + " 왕이 위험합니다! 🔥");
            }
        } else {
            if (board.isStaleMate(opponent)) {
                System.out.println("🤝 STALEMATE! 무승부입니다. 🤝");
                this.isStaleMateDraw = true;
                endGame();
            }
        }
    }

    // 턴을 스위칭하는 간단한 로직
    private void nextTurn() {
        currentTurn = (currentTurn == Color.WHITE) ? Color.BLACK : Color.WHITE;
    }

    public Board getBoard() {
        return board;
    }

    public Color getCurrentTurn() {
        return currentTurn;
    }

    /**
     * 현재 보드 상태를 표준 FEN(6필드) 문자열로 반환합니다.
     * - piece placement / active color / castling / en passant / halfmove / fullmove
     */
    public String getFEN() {
        StringBuilder fen = new StringBuilder(buildFenPositionPart());
        fen.append(' ');
        fen.append(board.getHalfMoveClock());
        fen.append(' ');
        fen.append(fullMoveNumber);
        return fen.toString();
    }

    /**
     * 3수 동형(반복) 비교용 문자열을 반환합니다.
     * FEN의 앞 4필드만 포함합니다:
     * - piece placement / active color / castling / en passant
     *
     * (halfmove/fullmove 카운터는 포함하지 않음)
     */
    public String getPositionKey() {
        return buildFenPositionPart();
    }

    /**
     * FEN의 앞 4필드(배치/턴/캐슬링/앙파상) 생성.
     * getFEN(), getPositionKey() 공용.
     */
    private String buildFenPositionPart() {
        StringBuilder fen = new StringBuilder();

        // 1) Piece placement (rank 8 -> 1)
        for (int y = 7; y >= 0; y--) {
            int empty = 0;
            for (int x = 0; x < 8; x++) {
                Piece p = board.getPiece(new Position(x, y));
                if (p == null) {
                    empty++;
                    continue;
                }
                if (empty > 0) {
                    fen.append(empty);
                    empty = 0;
                }

                // Type symbol은 소문자이므로, White는 대문자 처리
                String symbol = p.getType().getSymbol(); // "p","r","n","b","q","k"
                char c = symbol.isEmpty() ? '?' : symbol.charAt(0);
                if (p.getColor().isWhite()) {
                    c = Character.toUpperCase(c);
                }
                fen.append(c);
            }
            if (empty > 0) {
                fen.append(empty);
            }
            if (y > 0) {
                fen.append('/');
            }
        }

        // 2) Active color
        fen.append(' ');
        fen.append(currentTurn == Color.WHITE ? 'w' : 'b');

        // 3) Castling availability (현재 엔진의 "킹/룩 첫 이동 여부" 로직에 맞춰 산출)
        StringBuilder castling = new StringBuilder();

        // White: King e1(4,0), rooks a1(0,0), h1(7,0)
        Piece wk = board.getPiece(new Position(4, 0));
        if (wk != null && wk.is(Type.KING, Color.WHITE) && wk.isFirstMove()) {
            Piece wrH = board.getPiece(new Position(7, 0));
            if (wrH != null && wrH.is(Type.ROOK, Color.WHITE) && wrH.isFirstMove()) {
                castling.append('K');
            }
            Piece wrA = board.getPiece(new Position(0, 0));
            if (wrA != null && wrA.is(Type.ROOK, Color.WHITE) && wrA.isFirstMove()) {
                castling.append('Q');
            }
        }

        // Black: King e8(4,7), rooks a8(0,7), h8(7,7)
        Piece bk = board.getPiece(new Position(4, 7));
        if (bk != null && bk.is(Type.KING, Color.BLACK) && bk.isFirstMove()) {
            Piece brH = board.getPiece(new Position(7, 7));
            if (brH != null && brH.is(Type.ROOK, Color.BLACK) && brH.isFirstMove()) {
                castling.append('k');
            }
            Piece brA = board.getPiece(new Position(0, 7));
            if (brA != null && brA.is(Type.ROOK, Color.BLACK) && brA.isFirstMove()) {
                castling.append('q');
            }
        }

        fen.append(' ');
        fen.append(castling.length() == 0 ? "-" : castling.toString());

        // 4) En passant target square
        fen.append(' ');
        Position ep = board.getEnPassantTarget();
        fen.append(ep == null ? "-" : ep.toString());

        return fen.toString();
    }

    // 3수 동형 체크 및 처리
    private void checkThreefoldRepetition() {
        if (updatePositionHistory() >= 3) {
            System.out.println("🤝 3수 동형 무승부! 무승부입니다. 🤝");
            this.isThreefoldDraw = true;
            endGame();
        }
    }

    /**
     * 현재 상태를 positionHistory에 기록하고, 해당 상태가 몇 번째 등장인지 반환
     */
    private int updatePositionHistory() {
        String key = getPositionKey();
        int count = positionHistory.getOrDefault(key, 0) + 1;
        positionHistory.put(key, count);
        return count;
    }

    /**
     * 50수 무승부인지 확인
     */
    public boolean isFiftyMoveDraw() {
        return board.isFiftyMoveRule();
    }

    /**
     * 통합 무승부 확인 (50수, 3수 동형, 스테일메이트)
     */
    public boolean isDraw() {
        return isFiftyMoveDraw() || isThreefoldDraw || isStaleMateDraw;
    }

    /**
     * 🆕 무승부 사유 반환
     */
    public String getDrawReason() {
        if (isThreefoldDraw) return "Threefold Repetition";
        if (isFiftyMoveDraw()) return "50-Move Rule";
        if (isStaleMateDraw) return "Stalemate";
        return null;
    }
}
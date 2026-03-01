package chess.domain.board;

import chess.domain.piece.Piece;
import chess.domain.piece.Type;

import java.util.Map;

/**
 * 캐슬링 관련 로직을 담당하는 클래스
 */
public class CastlingHandler {
    private final Map<Position, Piece> pieces;

    public CastlingHandler(Map<Position, Piece> pieces) {
        this.pieces = pieces;
    }

    /**
     * 캐슬링 유효성 검사
     */
    public void validateCastling(Position source, Position target, Piece king) {
        // 1. 방향 확인 (우측: +1, 좌측: -1)
        int direction = (target.getX() - source.getX() > 0) ? 1 : -1;

        // 2. 룩의 위치 찾기 (우측이면 x=7, 좌측이면 x=0)
        int rookX = (direction == 1) ? 7 : 0;
        Position rookPos = new Position(rookX, source.getY());
        Piece rook = pieces.get(rookPos);

        // 3. 룩이 없거나, 룩이 아니거나, 이미 움직였으면 실패!
        if (rook == null || !rook.is(Type.ROOK) || !rook.isFirstMove()) {
            throw new IllegalArgumentException("No rook available for castling (already moved or missing).");
        }

        // 4. 킹과 룩 사이의 경로가 비어있는지 확인
        for (int x = source.getX() + direction; x != rookX; x += direction) {
            if (pieces.containsKey(new Position(x, source.getY()))) {
                throw new IllegalArgumentException("Cannot castle: piece in the way between king and rook.");
            }
        }
    }

    /**
     * 캐슬링이 가능한지 확인 (예외 없이 boolean 반환)
     */
    public boolean isCastlingPossible(Position source, Position target, Piece king) {
        try {
            validateCastling(source, target, king);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * 캐슬링 시 룩을 이동시킴
     */
    public void moveRookForCastling(Position kingSource, Position kingTarget) {
        int direction = (kingTarget.getX() - kingSource.getX() > 0) ? 1 : -1;
        int y = kingSource.getY();

        // 룩의 현재 위치 (끝) -> 이동할 위치 (킹 바로 옆)
        // 킹사이드(우): h열(7) -> f열(5) / 퀸사이드(좌): a열(0) -> d열(3)
        int rookX = (direction == 1) ? 7 : 0;
        int targetRookX = (direction == 1) ? 5 : 3;

        Position rookSource = new Position(rookX, y);
        Position rookTarget = new Position(targetRookX, y);

        Piece rook = pieces.get(rookSource);

        // 룩 이동 수행
        pieces.put(rookTarget, rook);
        pieces.remove(rookSource);
        rook.moved(); // 룩도 "움직임" 처리!

        System.out.println(">>> 🏰 캐슬링 발동! 룩이 " + rookSource + " -> " + rookTarget + "로 이동했습니다.");
    }
}


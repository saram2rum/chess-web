package chess.domain.piece;

import chess.domain.board.Position;

public class King extends Piece {

    public King(Color color) {
        super(color, Type.KING);
    }

    @Override
    public boolean isKing() {
        return true; // 게임 종료 조건 체크용
    }

    @Override
    public boolean isMovable(Position source, Position target, Piece targetPiece) {
        int xDiff = Math.abs(source.xDiff(target));
        int yDiff = Math.abs(source.yDiff(target));

        // 8방향으로 1칸만 이동 가능 (0칸 이동은 Board에서 막아주니 제외)
        // 1. 기존 로직: 8방향 1칸 이동
        boolean isOneStep = (xDiff <= 1 && yDiff <= 1) && (xDiff + yDiff > 0);

        // 2. 🏰 [추가] 캐슬링 이동 조건 (가로 2칸 + 세로 0칸 + 첫 이동)
        // (경로에 룩이 있는지, 장애물이 있는지는 Board에서 검사합니다.)
        boolean isCastling = (xDiff == 2 && yDiff == 0) && isFirstMove();

        // 둘 중 하나라도 만족하면 이동 가능!
        return isOneStep || isCastling;
    }
}
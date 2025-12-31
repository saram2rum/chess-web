package chess.domain.piece;

import chess.domain.board.Direction;
import chess.domain.board.Position;

public class Rook extends Piece {

    public Rook(final Color color) {
        super(color, Type.ROOK);
    }

    @Override
    public boolean isSliding() {
        return true; // 경로 장애물 검사 필요
    }

    @Override
    public boolean isMovable(Position source, Position target, Piece targetPiece) {
        try {
            Direction direction = Direction.of(source, target);
            return direction.isLinear();
        } catch (IllegalArgumentException e) {
            return false; // 방향 성립 안 되면 이동 불가
        }
    }
}
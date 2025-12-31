package chess.domain.piece;

import chess.domain.board.Direction;
import chess.domain.board.Position;

public class Bishop extends Piece {

    public Bishop(final Color color) {
        super(color, Type.BISHOP);
    }

    @Override
    public boolean isSliding() {
        return true;
    }

    @Override
    public boolean isMovable(Position source, Position target, Piece targetPiece) {
        try {
            Direction direction = Direction.of(source, target); // 이상한 각도면 여기서 터짐💥
            return direction.isDiagonal();
        } catch (IllegalArgumentException e) {
            return false; // 방향이 없으면 이동 불가
        }
    }
}

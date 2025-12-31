package chess.domain.piece;

import chess.domain.board.Direction;
import chess.domain.board.Position;

public class Queen extends Piece {

    public Queen(Color color) {
        super(color, Type.QUEEN);
    }

    @Override
    public boolean isSliding() {
        return true; // 🚧 경로 검사 필요
    }

    @Override
    public boolean isMovable(Position source, Position target, Piece targetPiece) {
        try {
            Direction direction = Direction.of(source, target);
            // 퀸은 직선이든 대각선이든 Direction이 나오기만 하면 OK
            return direction.isLinear() || direction.isDiagonal();
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
package chess.domain.piece;

import chess.domain.board.Position;

public class Knight extends Piece {

    public Knight(final Color color) {
        super(color, Type.KNIGHT);
    }

    @Override
    public boolean isMovable(Position source, Position target, Piece targetPiece) {
        // 나이트는 도착지에 아군만 없으면 됨 (적이면 잡고, 빈칸이면 이동)
        // -> 이 검사는 Board나 부모 클래스에서 isSameColor로 하므로 여기선 "거리"만 보면 됨.

        int xDiff = Math.abs(source.xDiff(target));
        int yDiff = Math.abs(source.yDiff(target));

        // (1칸, 2칸) 또는 (2칸, 1칸) 이동
        return (xDiff == 1 && yDiff == 2) || (xDiff == 2 && yDiff == 1);
    }

}

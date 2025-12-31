package chess.domain.board;

import chess.domain.piece.Piece;

import java.util.Map;

/**
 * 경로 장애물 검사를 담당하는 클래스
 */
public class PathChecker {
    private final Map<Position, Piece> pieces;

    public PathChecker(Map<Position, Piece> pieces) {
        this.pieces = pieces;
    }

    /**
     * source에서 target까지의 경로에 장애물이 있는지 확인
     * @return 장애물이 있으면 true, 없으면 false
     */
    public boolean isPathBlocked(Position source, Position target) {
        Direction direction = Direction.of(source, target);
        Position current = source;

        while (true) {
            int nextX = current.getX() + direction.getXDegree();
            int nextY = current.getY() + direction.getYDegree();

            // 체스판 밖으로 나가면 즉시 종료 (무한 루프 방지)
            if (nextX < 0 || nextX > 7 || nextY < 0 || nextY > 7) {
                return false;
            }

            current = new Position(nextX, nextY);

            // 목적지에 도착했으면 장애물 없음
            if (current.equals(target)) {
                return false;
            }

            // 가는 길에 다른 기물이 있으면 장애물 있음
            if (pieces.containsKey(current)) {
                return true;
            }
        }
    }
}


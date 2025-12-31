package chess.domain.board;

import java.util.Arrays;

public enum Direction {
    NORTH(0, 1),
    NORTHEAST(1, 1),
    EAST(1, 0),
    SOUTHEAST(1, -1),
    SOUTH(0, -1),
    SOUTHWEST(-1, -1),
    WEST(-1, 0),
    NORTHWEST(-1, 1);

    private int xDegree;
    private int yDegree;

    public int getXDegree() {
        return xDegree;
    }

    public int getYDegree() {
        return yDegree;
    }

    public boolean isDiagonal() {
        return xDegree != 0 && yDegree != 0;
    }

    public boolean isLinear() {
        return xDegree == 0 || yDegree == 0;
    }

    Direction(int xDegree, int yDegree) {
        this.xDegree = xDegree;
        this.yDegree = yDegree;
    }

    public static Direction of(Position source, Position target) {
        int xDiff = target.getX() - source.getX();
        int yDiff = target.getY() - source.getY();

        // 1. 제자리 걸음 제외
        if (xDiff == 0 && yDiff == 0) {
            throw new IllegalArgumentException("제자리로 이동할 수 없습니다.");
        }

        // 2. 🚨 [핵심] 직선도 아니고, 대각선도 아니면 "방향 없음" 취급!
        // (x가 0이 아니고 y가 0이 아닌데, x와 y의 길이가 다르면? -> 이상한 각도)
        if (xDiff != 0 && yDiff != 0 && Math.abs(xDiff) != Math.abs(yDiff)) {
            throw new IllegalArgumentException("유효하지 않은 방향입니다.");
        }

        // 3. 여기까지 왔으면 "완벽한 직선"이거나 "완벽한 대각선"임이 보장됨
        int xDegree = Integer.compare(xDiff, 0);
        int yDegree = Integer.compare(yDiff, 0);

        return Arrays.stream(values())
                .filter(d -> d.xDegree == xDegree && d.yDegree == yDegree)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("방향을 찾을 수 없습니다."));
    }
}

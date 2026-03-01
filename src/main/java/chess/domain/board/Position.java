package chess.domain.board;

import java.util.Objects;

public class Position {

    private final int x;
    private final int y;

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    // 1. 문자열 생성자 ("e2")
    public Position(String uiPosition) {
        if (uiPosition == null || uiPosition.length() != 2) {
            throw new IllegalArgumentException("Position must be 2 characters (e.g. 'e2').");
        }

        String lowerPosition = uiPosition.toLowerCase();
        int parsedX = lowerPosition.charAt(0) - 'a';
        int parsedY = lowerPosition.charAt(1) - '1';

        // 🛡️ 공통 검증 메서드 호출
        validate(parsedX, parsedY);

        this.x = parsedX;
        this.y = parsedY;
    }

    // 2. 숫자 생성자 (0, 1) -> 여기도 검사 필수!! 🚨
    public Position(int x, int y) {
        // 🛡️ 공통 검증 메서드 호출
        validate(x, y);

        this.x = x;
        this.y = y;
    }

    // 🔒 검증 로직을 한곳에 모음 (중복 제거)
    private void validate(int x, int y) {
        if (x < 0 || x > 7 || y < 0 || y > 7) {
            throw new IllegalArgumentException("Position out of board range: x:" + x + ", y:" + y);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Position position = (Position) o;
        return x == position.x && y == position.y;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y);
    }

    // Position.java

    // ... 기존 코드 아래에 추가 ...

    // x좌표(가로) 차이 계산
    public int xDiff(Position other) {
        return other.x - this.x;
    }

    // y좌표(세로) 차이 계산
    public int yDiff(Position other) {
        return other.y - this.y;
    }

    @Override
    public String toString() {
        char file = (char) ('a' + x);
        char rank = (char) ('1' + y);
        return "" + file + rank; // "a2", "e4" 등으로 반환
    }

}


package chess.domain.piece;

import chess.domain.board.Position;

public abstract class Piece {

    private final Color color;
    private final Type type;

    public Piece(Color color, Type type) {
        this.color = color;
        this.type = type;
    }

    private boolean hasMoved = false;

    // 기물이 이동할 때마다 호출해 줄 메서드
    public void moved() {
        this.hasMoved = true;
    }

    public boolean isFirstMove() {
        return !hasMoved;
    }

    // ✅ 여기에 추가! (팀킬 방지용)
    public boolean isSameColor(Piece other) {
        if (other == null) {
            return false;
        }
        return this.color == other.getColor();
    }

    public boolean is(Type type, Color color) {
        return this.type == type && this.color == color;
    }

    public boolean is(Type type) {
        return this.type == type;
    }

    public boolean isKing() {
        return type.isKing();
    }

    public boolean isWhite() {
        return color.isWhite();
    }

    public boolean isBlack() {
        return color.isBlack();
    }

    public boolean isSliding() {
        return false;
    }

    public Type getType() {
        return type;
    }

    public Color getColor() {
        return color;
    }

    // 도착지에 적이 있는지(targetPiece) 정보를 받아서 판단
    public abstract boolean isMovable(Position source, Position target, Piece targetPiece);
}
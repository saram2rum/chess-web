package chess.domain.piece;

public enum Color {

    WHITE,
    BLACK,
    NO_COLOR;

    public Color opponent() {
        return this.isWhite() ? BLACK : WHITE;
    }

    public boolean isWhite() {
        return this == WHITE;
    }

    public boolean isBlack() {
        return this == BLACK;
    }

}

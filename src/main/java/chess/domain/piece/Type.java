package chess.domain.piece;

public enum Type {

    PAWN("p", 1.0),
    ROOK("r", 5.0),
    KNIGHT("n", 3.0),
    BISHOP("b", 3.0),
    QUEEN("q", 9.0),
    KING("k", 0.0),
    NO_PIECE(".", 0.0);

    private final String symbol;
    private final double score;

    Type(String symbol, double score) {
        this.symbol = symbol;
        this.score = score;
    }

    public boolean isKing() { return this == KING; }

    public double getScore() {
        return score;
    }

    public String getSymbol() {
        return symbol;
    }
}

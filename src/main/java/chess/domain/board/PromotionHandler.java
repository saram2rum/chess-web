package chess.domain.board;

import chess.domain.piece.*;

/**
 * 프로모션 관련 로직을 담당하는 클래스
 */
public class PromotionHandler {

    /**
     * 프로모션 자격 확인 (맨 끝 줄인가?)
     */
    public boolean canPromote(Position target, Color color) {
        int y = target.getY();
        // 백색은 y=7(8랭크), 흑색은 y=0(1랭크) 도달 시
        if (color.isWhite()) return y == 7;
        else return y == 0;
    }

    /**
     * 기물 생성 공장 (Factory 패턴의 간단 버전)
     */
    public Piece createPromotedPiece(Type type, Color color) {
        switch (type) {
            case QUEEN: return new Queen(color);
            case ROOK: return new Rook(color);
            case BISHOP: return new Bishop(color);
            case KNIGHT: return new Knight(color);
            default: throw new IllegalArgumentException("Pawn cannot promote to king or pawn.");
        }
    }
}


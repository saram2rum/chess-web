package chess.domain.board;

import chess.domain.piece.Pawn;
import chess.domain.piece.Piece;

import java.util.Map;

/**
 * 이동 검증 로직을 담당하는 클래스
 */
public class MoveValidator {
    private final Map<Position, Piece> pieces;
    private final PathChecker pathChecker;
    private Position enPassantTarget;

    public MoveValidator(Map<Position, Piece> pieces, PathChecker pathChecker) {
        this.pieces = pieces;
        this.pathChecker = pathChecker;
    }

    public void setEnPassantTarget(Position enPassantTarget) {
        this.enPassantTarget = enPassantTarget;
    }

    /**
     * 기물 규칙상 갈 수 있고 장애물도 없는지 확인
     */
    public boolean isValidMove(Position source, Position target) {
        Piece piece = pieces.get(source);
        if (piece == null) return false; // 기물이 없으면 이동 불가
        if (source.equals(target)) return false;

        Piece targetPiece = pieces.get(target);

        // 1. 아군 팀킬 방지
        if (targetPiece != null && piece.isSameColor(targetPiece)) {
            return false;
        }

        // 2. 앙파상 체크 (폰이 대각선으로 이동하는데 목적지가 비어있는 경우)
        boolean isEnPassant = false;
        if (piece instanceof Pawn && targetPiece == null && source.getX() != target.getX()) {
            // 앙파상은 한 칸 대각선 이동이어야 함
            int xDiff = Math.abs(source.getX() - target.getX());
            int yDiff = Math.abs(source.getY() - target.getY());
            
            if (xDiff == 1 && yDiff == 1) {
                // 앙파상 가능한 위치인지 확인
                if (enPassantTarget != null && target.equals(enPassantTarget)) {
                    isEnPassant = true;
                    // 앙파상은 유효한 이동이므로 계속 진행
                } else {
                    return false; // 앙파상이 아닌데 대각선으로 빈 칸으로 가려고 하면 불가
                }
            } else {
                // 대각선이지만 한 칸이 아니면 불가
                return false;
            }
        }

        // 3. 기물 자체의 이동 규칙 검사 (방향, 거리 등)
        // 앙파상의 경우: 대각선 1칸 이동이므로 Pawn.isMovable의 대각선 공격 로직과 맞지 않음
        // 따라서 앙파상은 여기서 별도로 처리
        if (isEnPassant) {
            // 앙파상은 대각선 1칸 이동이므로 유효
            return true;
        }

        if (!piece.isMovable(source, target, targetPiece)) {
            return false;
        }

        // 4. 장애물 검사 (슬라이딩 기물 OR "폰이 2칸 이동할 때")
        boolean isPawnTwoStep = (piece instanceof Pawn) && Math.abs(source.getY() - target.getY()) == 2;

        if ((piece.isSliding() || isPawnTwoStep) && pathChecker.isPathBlocked(source, target)) {
            return false; // 중간에 누구 있으면 이동 불가
        }

        return true;
    }
}


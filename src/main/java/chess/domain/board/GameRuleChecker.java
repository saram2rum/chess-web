package chess.domain.board;

import chess.domain.piece.Color;
import chess.domain.piece.Piece;
import chess.domain.piece.Type;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 체크, 체크메이트, 스테일메이트 판정을 담당하는 클래스
 */
public class GameRuleChecker {
    private final Map<Position, Piece> pieces;
    private final Position whiteKingPosition;
    private final Position blackKingPosition;
    private final PathChecker pathChecker;
    private final MoveValidator moveValidator;
    private final CastlingHandler castlingHandler;

    public GameRuleChecker(Map<Position, Piece> pieces, 
                          Position whiteKingPosition, 
                          Position blackKingPosition,
                          PathChecker pathChecker,
                          MoveValidator moveValidator,
                          CastlingHandler castlingHandler) {
        this.pieces = pieces;
        this.whiteKingPosition = whiteKingPosition;
        this.blackKingPosition = blackKingPosition;
        this.pathChecker = pathChecker;
        this.moveValidator = moveValidator;
        this.castlingHandler = castlingHandler;
    }

    /**
     * 특정 색상의 왕이 체크 상태인지 확인
     */
    public boolean isChecked(Color kingColor) {
        Position kingPosition = kingColor.isWhite() ? whiteKingPosition : blackKingPosition;
        Piece king = pieces.get(kingPosition);

        for (Position source : pieces.keySet()) {
            Piece attacker = pieces.get(source);

            // 1. 아군은 패스
            if (attacker.isSameColor(king)) continue;

            // 2. 기본 이동 규칙 검사 (방향, 거리 등)
            if (!attacker.isMovable(source, kingPosition, king)) continue;

            // 3. 슬라이딩 기물(룩, 비숍, 퀸)은 장애물 검사 필수!
            if (attacker.isSliding()) {
                if (pathChecker.isPathBlocked(source, kingPosition)) {
                    continue; // 벽에 막혔으니 체크 아님 -> 다음 놈 검사
                }
            }

            // 여기까지 통과하면 진짜 체크!
            return true;
        }
        return false;
    }

    /**
     * 특정 색상의 기물이 안전하게 이동할 수 있는 위치 목록 반환
     */
    public List<Position> calculateMovablePositions(Position source) {
        List<Position> movablePositions = new ArrayList<>();
        Piece piece = pieces.get(source);

        if (piece == null) return movablePositions; // 빈칸 클릭하면 빈 리스트

        // 체스판 전체를 훑으면서 갈 수 있는지 확인
        for (int x = 0; x < 8; x++) {
            for (int y = 0; y < 8; y++) {
                Position target = new Position(x, y);
                if (source.equals(target)) continue;

                // 1. 규칙상 갈 수 있고 (isValidMove)
                // 2. 가서 체크당하지 않는다면 (isMoveSafe) -> OK!
                // 가상 이동 후 왕 위치 계산
                Position newWhiteKingPos = whiteKingPosition;
                Position newBlackKingPos = blackKingPosition;
                if (piece.isKing()) {
                    if (piece.getColor().isWhite()) {
                        newWhiteKingPos = target;
                    } else {
                        newBlackKingPos = target;
                    }
                }

                // 캐슬링인 경우 추가 검증
                boolean isCastling = piece.is(Type.KING) && Math.abs(source.xDiff(target)) == 2;
                if (isCastling && !castlingHandler.isCastlingPossible(source, target, piece)) {
                    continue; // 캐슬링 불가능하면 스킵
                }

                if (moveValidator.isValidMove(source, target) && 
                    isMoveSafe(source, target, newWhiteKingPos, newBlackKingPos)) {
                    movablePositions.add(target);
                }
            }
        }

        return movablePositions;
    }

    /**
     * 특정 색상의 기물 중 어디로든 움직여서 살 수 있는 수가 하나라도 있는지 확인
     */
    public boolean hasAnySafeMove(Color color) {
        // 에러 방지를 위해 keySet을 새로운 List로 복사해서 사용
        List<Position> piecePositions = new ArrayList<>(pieces.keySet());

        for (Position source : piecePositions) {
            Piece piece = pieces.get(source);
            if (piece == null || piece.getColor() != color) continue;

            // 이 기물이 갈 수 있는 곳이 하나라도 있으면 생존
            if (!calculateMovablePositions(source).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 체크메이트: 체크 상태인데 살길이 없음
     */
    public boolean isCheckMate(Color color) {
        return isChecked(color) && !hasAnySafeMove(color);
    }

    /**
     * 스테일메이트: 체크 아닌데 살길이 없음 (무승부)
     */
    public boolean isStaleMate(Color color) {
        return !isChecked(color) && !hasAnySafeMove(color);
    }

    /**
     * 거기로 움직이면 우리 왕이 안전한가? (가상 이동 시뮬레이션)
     * @param whiteKingPos 현재 흰색 왕 위치 (가상 이동 후)
     * @param blackKingPos 현재 검은색 왕 위치 (가상 이동 후)
     */
    public boolean isMoveSafe(Position source, Position target, Position whiteKingPos, Position blackKingPos) {
        Piece piece = pieces.get(source);
        Piece capturedPiece = pieces.get(target);
        Color myColor = piece.getColor();

        // 1. 기물 이동 (가상)
        pieces.put(target, piece);
        pieces.remove(source);

        // 2. 안전한지 확인 (동적 왕 위치 사용)
        boolean isSafe = !isCheckedWithKingPositions(myColor, whiteKingPos, blackKingPos);

        // 3. 원상복구 (Rollback)
        pieces.put(source, piece);
        if (capturedPiece != null) {
            pieces.put(target, capturedPiece);
        } else {
            pieces.remove(target);
        }

        return isSafe;
    }

    /**
     * 특정 왕 위치로 체크 상태 확인 (가상 이동 시뮬레이션용)
     */
    private boolean isCheckedWithKingPositions(Color kingColor, Position whiteKingPos, Position blackKingPos) {
        Position kingPosition = kingColor.isWhite() ? whiteKingPos : blackKingPos;
        Piece king = pieces.get(kingPosition);
        if (king == null) return false; // 왕이 없으면 체크 아님

        for (Position source : pieces.keySet()) {
            Piece attacker = pieces.get(source);

            // 1. 아군은 패스
            if (attacker.isSameColor(king)) continue;

            // 2. 기본 이동 규칙 검사 (방향, 거리 등)
            if (!attacker.isMovable(source, kingPosition, king)) continue;

            // 3. 슬라이딩 기물은 장애물 검사 필수!
            if (attacker.isSliding()) {
                if (pathChecker.isPathBlocked(source, kingPosition)) {
                    continue;
                }
            }

            return true;
        }
        return false;
    }
}


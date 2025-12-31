package chess.domain.piece;

import chess.domain.board.Direction;
import chess.domain.board.Position;

public class Pawn extends Piece {

    // 1. 생성자 수정: 부모에게 "저는 폰입니다"라고 알려줌
    public Pawn(Color color) {
        super(color, Type.PAWN);
    }

    @Override
    public boolean isMovable(Position source, Position target, Piece targetPiece) {
        Direction direction;
        try {
            direction = Direction.of(source, target);
        } catch (IllegalArgumentException e) {
            return false;
        }

        int xDiff = Math.abs(source.getX() - target.getX());
        int yDiff = Math.abs(source.getY() - target.getY());

        // 거리 체크 (1칸 혹은 2칸)
        if (yDiff > 2 || xDiff > 1) {
            return false; // y가 2칸 넘거나, x가 1칸 넘으면 절대 불가
        }

        // (추가) 2칸 이동 시도라면? x이동은 없어야 함 (직진만 가능)
        if (yDiff == 2 && xDiff != 0) {
            return false;
        }

        Direction forward = isWhite() ? Direction.NORTH : Direction.SOUTH;

        // --- [A] 직진 로직 (1칸 or 2칸) ---
        if (direction == forward) {
            // 공통: 직진은 앞에 적이 없어야 함
            if (targetPiece != null) return false;

            // 1. 그냥 1칸 전진 -> OK
            if (yDiff == 1) {
                return true;
            }

            // 2. [NEW] 2칸 전진 -> "처음 위치"인지 확인!
            if (yDiff == 2) {
                if (isWhite() && source.getY() == 1) return true; // 백색 초기 위치
                if (!isWhite() && source.getY() == 6) return true; // 흑색 초기 위치
            }

            // 그 외의 위치에서 2칸 가려고 하면 false
            return false;
        }

        // --- [B] 대각선 공격 로직 (일반 공격 + 앙파상) ---
        if (isAttackDirection(direction)) {
            // 대각선은 무조건 1칸만 가능 (yDiff == 1)
            if (yDiff != 1) return false;

            // 일반 대각선 공격: 목적지에 적 기물이 있어야 함
            if (targetPiece != null && !isSameColor(targetPiece)) {
                return true;
            }
            
            // 앙파상: 목적지가 비어있지만 앙파상 가능한 위치일 수 있음
            // (이건 MoveValidator나 Board에서 추가로 체크해야 함)
            // 여기서는 false 반환하고, MoveValidator에서 앙파상 체크
            return false;
        }

        return false;
    }

    // 대각선 방향인지 확인하는 도우미
    private boolean isAttackDirection(Direction direction) {
        if (isWhite()) {
            return direction == Direction.NORTHWEST || direction == Direction.NORTHEAST;
        }
        return direction == Direction.SOUTHWEST || direction == Direction.SOUTHEAST;
    }
}
package chess.dto;

/**
 * 시간 패(timeout) 시 게임 종료 결과 DTO
 * 프론트엔드 handleMoveResult와 showGameOverModal과 호환
 */
public record TimeoutResultDTO(
    String nextTurn,      // 시간 초과한 플레이어 (패자)
    boolean isGameOver,
    String winner,        // 상대방 (승자)
    String endReason
) {
}

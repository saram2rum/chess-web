package chess.dto;

/**
 * 타이머 업데이트 DTO
 * @param whiteTime 백 플레이어 남은 시간 (밀리초)
 * @param blackTime 흑 플레이어 남은 시간 (밀리초)
 */
public record TimeUpdateDTO(
    long whiteTime,
    long blackTime
) {
}




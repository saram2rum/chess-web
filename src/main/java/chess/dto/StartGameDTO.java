package chess.dto;

/**
 * 게임 시작 알림 DTO
 * @param message 메시지
 * @param description 설명
 * @param hostColor 방장 색상 (WHITE/BLACK)
 * @param guestColor 게스트 색상 (WHITE/BLACK)
 */
public record StartGameDTO(
    String message,
    String description,
    String hostColor,
    String guestColor,
    long whiteTime,   // 밀리초 (초기값)
    long blackTime    // 밀리초
) {
}

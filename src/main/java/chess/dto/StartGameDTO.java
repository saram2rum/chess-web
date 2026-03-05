package chess.dto;

/**
 * 게임 시작 알림 DTO
 * @param message 메시지
 * @param description 설명
 * @param hostColor 방장 색상 (WHITE/BLACK)
 * @param guestColor 게스트 색상 (WHITE/BLACK)
 * @param evalType/evalValue 초기 형세분석 (서버 1회 호출, Single Source of Truth)
 */
public record StartGameDTO(
    String message,
    String description,
    String hostColor,
    String guestColor,
    long whiteTime,   // 밀리초 (초기값)
    long blackTime,   // 밀리초
    String evalType,  // "cp" | "mate" (초기 형세, null 가능)
    Integer evalValue // centipawn 또는 mate 수 (null 가능)
) {
}

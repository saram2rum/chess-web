package chess.dto;

/**
 * 체스 이동 요청 DTO
 * @param source 출발 좌표 (예: "e2")
 * @param target 도착 좌표 (예: "e4")
 * @param promotion 폰 승급 시 변신할 기물 타입 (예: "QUEEN", "ROOK", "BISHOP", "KNIGHT")
 */
public record MoveDTO(String source, String target, String promotion) {
}

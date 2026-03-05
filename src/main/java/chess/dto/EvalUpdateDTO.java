package chess.dto;

/**
 * 형세분석 비동기 브로드캐스트용 DTO (수 이동과 분리)
 * moveSequence: 오래된 eval 덮어쓰기 방지 (클라이언트가 received >= current 일 때만 적용)
 */
public record EvalUpdateDTO(String fen, String type, int value, int moveSequence) {
}

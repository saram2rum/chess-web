package chess.dto;

/**
 * 형세 평가 결과 (Stockfish 응답 구조)
 * type: "cp" | "mate", value: centipawn 또는 mate 수
 */
public record EvalResultDTO(String type, int value) {
}

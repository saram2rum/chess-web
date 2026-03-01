package chess.dto;

import java.util.List;

/**
 * 유효한 이동 가능 좌표 응답 DTO
 * @param source 선택한 기물의 좌표
 * @param validMoves 이동 가능한 좌표 리스트
 */
public record ValidMovesDTO(String source, List<String> validMoves) {
}











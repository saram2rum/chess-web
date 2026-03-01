package chess.dto;

public record JoinGameResponseDTO(
    String roomId,
    String assignedColor,
    String message
) {
}




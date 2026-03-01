package chess.dto;

public record LobbyStateDTO(
    String roomId,
    String hostSessionId,
    String guestSessionId,
    String hostUserId,
    String hostNickname,
    String guestUserId,
    String guestNickname,
    GameSettings settings,
    boolean isReady,
    boolean isGameStarted
) {
}


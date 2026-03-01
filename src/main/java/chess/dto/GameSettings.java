package chess.dto;

public record GameSettings(
    String startingSide,  // "WHITE", "BLACK", "RANDOM"
    int timeLimit,        // 분 단위
    int increment         // 초 단위
) {
    public static GameSettings defaultSettings() {
        return new GameSettings("RANDOM", 10, 0);
    }
}

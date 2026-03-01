package chess.dto;

import java.util.List;

public record MovablePositionsDTO(
    String source,
    List<String> targets
) {
}




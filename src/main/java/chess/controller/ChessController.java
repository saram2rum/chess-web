package chess.controller;

import chess.domain.game.ChessGame;
import chess.domain.piece.Type;
import chess.domain.piece.Color;
import chess.domain.piece.Piece;
import chess.domain.board.Position;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
public class ChessController {

    private ChessGame chessGame; // final 제거 (재시작을 위해)

    public ChessController() {
        this.chessGame = new ChessGame();
    }

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("board", chessGame.getBoard());
        model.addAttribute("turn", chessGame.getCurrentTurn());
        return "index";
    }

    // 🆕 [추가] 재시작 API
    @PostMapping("/restart")
    @ResponseBody
    public String restart() {
        this.chessGame = new ChessGame(); // 게임판 엎고 새로 세팅!
        return "SUCCESS";
    }

    @PostMapping("/move")
    @ResponseBody
    public Map<String, Object> move(@RequestParam("source") String source,
                                    @RequestParam("target") String target,
                                    @RequestParam(value = "promotion", required = false) String promotionStr) {

        Map<String, Object> response = new HashMap<>();
        try {
            Type promotionType = null;
            if (promotionStr != null && !promotionStr.isEmpty()) {
                promotionType = Type.valueOf(promotionStr);
            }

            // 이동 실행
            chessGame.move(source, target, promotionType);

            response.put("code", "SUCCESS");
            response.put("turn", chessGame.getCurrentTurn());

            // 🆕 [승패 확인] 게임이 끝났나요?
            if (!chessGame.isRunning()) {
                response.put("gameOver", true);
                // 50수 무승부인 경우 winner는 null
                if (chessGame.isFiftyMoveDraw()) {
                    response.put("winner", "DRAW");
                } else {
                    // 현재 턴인 사람이 체크메이트 당한 것이므로, 승자는 그 반대편!
                    String winner = chessGame.getCurrentTurn().isWhite() ? "BLACK" : "WHITE";
                    response.put("winner", winner);
                }
            } else {
                response.put("gameOver", false);
            }

            // 보드 상태 그리기
            Map<String, String> boardMap = new HashMap<>();
            Map<String, String> teamMap = new HashMap<>();
            for (int x = 0; x < 8; x++) {
                for (int y = 0; y < 8; y++) {
                    Position pos = new Position(x, y);
                    Piece piece = chessGame.getBoard().getPiece(pos);
                    String coord = pos.toString();
                    if (piece != null) {
                        boardMap.put(coord, getPieceSymbol(piece));
                        teamMap.put(coord, piece.getColor().toString());
                    } else {
                        boardMap.put(coord, "");
                        teamMap.put(coord, "NONE");
                    }
                }
            }
            response.put("board", boardMap);
            response.put("team", teamMap);

        } catch (IllegalArgumentException e) {
            response.put("code", "ERROR");
            response.put("message", "잘못된 요청입니다.");
        } catch (Exception e) {
            response.put("code", "ERROR");
            response.put("message", e.getMessage());
        }
        return response;
    }

    @GetMapping("/movable")
    @ResponseBody
    public List<String> movable(@RequestParam("source") String sourceStr) {
        try {
            Position source = toPosition(sourceStr);
            List<Position> paths = chessGame.calculateMovablePositions(source);
            return paths.stream()
                    .map(Position::toString)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private Position toPosition(String str) {
        int x = str.charAt(0) - 'a';
        int y = str.charAt(1) - '1';
        return new Position(x, y);
    }

    private String getPieceSymbol(Piece piece) {
        if (piece.is(Type.PAWN)) return piece.isWhite() ? "♙" : "♟︎";
        if (piece.is(Type.ROOK)) return piece.isWhite() ? "♖" : "♜";
        if (piece.is(Type.KNIGHT)) return piece.isWhite() ? "♘" : "♞";
        if (piece.is(Type.BISHOP)) return piece.isWhite() ? "♗" : "♝";
        if (piece.is(Type.QUEEN)) return piece.isWhite() ? "♕" : "♛";
        if (piece.is(Type.KING)) return piece.isWhite() ? "♔" : "♚";
        return "";
    }
}
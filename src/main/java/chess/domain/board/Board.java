package chess.domain.board;

import chess.domain.piece.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 체스판 상태를 관리하는 클래스
 * 기물 저장소 역할과 초기화를 담당하며, 실제 검증 로직은 다른 클래스에 위임
 */
public class Board {
    private final Map<Position, Piece> pieces = new HashMap<>();
    private Position whiteKingPosition;
    private Position blackKingPosition;
    
    // 앙파상: 상대 폰이 2칸 이동했을 때 잡을 수 있는 위치
    private Position enPassantTarget = null;
    
    // 50수 무승부 규칙: 폰 이동이나 기물 포획이 없이 지난 수
    private int halfMoveClock = 0;

    // 분리된 책임 클래스들
    private final PathChecker pathChecker;
    private final MoveValidator moveValidator;
    private final CastlingHandler castlingHandler;
    private final PromotionHandler promotionHandler;

    public Board() {
        this.pathChecker = new PathChecker(pieces);
        this.moveValidator = new MoveValidator(pieces, pathChecker);
        this.castlingHandler = new CastlingHandler(pieces);
        this.promotionHandler = new PromotionHandler();
    }

    public Position getEnPassantTarget() {
        return enPassantTarget;
    }

    /**
     * 현재 왕 위치로 GameRuleChecker 생성 (필요할 때마다 생성)
     */
    private GameRuleChecker createGameRuleChecker() {
        return new GameRuleChecker(pieces, whiteKingPosition, blackKingPosition, pathChecker, moveValidator, castlingHandler);
    }

    public void initialize() {
        pieces.clear();
        addBlackPieces();
        addWhitePieces();
    }

    private void addBlackPieces() {
        // 검은색 폰 (y=6, 위에서 두 번째 줄)
        for (int i = 0; i < 8; i++) {
            pieces.put(new Position(i, 6), new Pawn(Color.BLACK));
        }

        // 검은색 주요 기물 (y=7, 맨 윗줄)
        pieces.put(new Position(0, 7), new Rook(Color.BLACK));
        pieces.put(new Position(1, 7), new Knight(Color.BLACK));
        pieces.put(new Position(2, 7), new Bishop(Color.BLACK));
        pieces.put(new Position(3, 7), new Queen(Color.BLACK));
        Position pos = new Position(4, 7);
        pieces.put(pos, new King(Color.BLACK));
        blackKingPosition = pos;
        pieces.put(new Position(5, 7), new Bishop(Color.BLACK));
        pieces.put(new Position(6, 7), new Knight(Color.BLACK));
        pieces.put(new Position(7, 7), new Rook(Color.BLACK));
    }

    private void addWhitePieces() {
        // 흰색 폰 (y=1, 아래서 두 번째 줄)
        for (int i = 0; i < 8; i++) {
            pieces.put(new Position(i, 1), new Pawn(Color.WHITE));
        }

        // 흰색 주요 기물 (y=0, 맨 아랫줄)
        pieces.put(new Position(0, 0), new Rook(Color.WHITE));
        pieces.put(new Position(1, 0), new Knight(Color.WHITE));
        pieces.put(new Position(2, 0), new Bishop(Color.WHITE));
        pieces.put(new Position(3, 0), new Queen(Color.WHITE));
        Position pos = new Position(4, 0);
        pieces.put(pos, new King(Color.WHITE));
        whiteKingPosition = pos;
        pieces.put(new Position(5, 0), new Bishop(Color.WHITE));
        pieces.put(new Position(6, 0), new Knight(Color.WHITE));
        pieces.put(new Position(7, 0), new Rook(Color.WHITE));
    }


    private Piece findPiece(Position position) {
        Piece piece = pieces.get(position);
        if (piece == null) {
            throw new IllegalArgumentException("해당 위치에는 기물이 없습니다.");
        }
        return piece;
    }

    public Piece getPiece(Position position) {
        return pieces.get(position);
    }

    public void move(Position source, Position target, Color currentTurn, Type promotionType) {
        Piece sourcePiece = findPiece(source);

        // 1. 턴 검증
        if (sourcePiece.getColor() != currentTurn) {
            throw new IllegalArgumentException("상대방의 기물은 건드릴 수 없습니다!");
        }

        // 앙파상 타겟 업데이트 (매 턴마다)
        moveValidator.setEnPassantTarget(enPassantTarget);

        // 2. 기본 이동 규칙 검증
        if (!moveValidator.isValidMove(source, target)) {
            throw new IllegalArgumentException("그 기물은 거기로 갈 수 없습니다! 규칙 위반 삐-! 🚨");
        }

        // 3. 자살수 방지 검증
        Position newWhiteKingPos = whiteKingPosition;
        Position newBlackKingPos = blackKingPosition;
        if (sourcePiece.isKing()) {
            if (sourcePiece.getColor().isWhite()) {
                newWhiteKingPos = target;
            } else {
                newBlackKingPos = target;
            }
        }

        GameRuleChecker ruleChecker = createGameRuleChecker();
        if (!ruleChecker.isMoveSafe(source, target, newWhiteKingPos, newBlackKingPos)) {
            throw new IllegalArgumentException("왕이 체크 상태에 빠지게 되는 수는 둘 수 없습니다! 🛡️");
        }

        // 4. 캐슬링 검증 및 처리
        boolean isCastling = sourcePiece.is(Type.KING) && Math.abs(source.xDiff(target)) == 2;
        if (isCastling) {
            castlingHandler.validateCastling(source, target, sourcePiece);
        }

        // 5. 앙파상 처리 (폰이 대각선으로 빈 칸으로 이동하는 경우)
        boolean isEnPassant = sourcePiece.is(Type.PAWN) && 
                              pieces.get(target) == null && 
                              source.getX() != target.getX() &&
                              enPassantTarget != null && 
                              target.equals(enPassantTarget);
        
        if (isEnPassant) {
            // 앙파상으로 잡힌 폰의 위치
            // 앙파상 타겟은 2칸 이동한 폰의 중간 위치이므로,
            // 잡힌 폰은 앙파상 타겟에서 sourcePiece의 진행 방향으로 1칸 더 간 위치
            // 백색은 위로 가므로 (Y 증가), 흑색은 아래로 가므로 (Y 감소)
            int capturedY = sourcePiece.getColor().isWhite() ? 
                           enPassantTarget.getY() - 1 :  // 백색이 위로 가면 타겟 아래
                           enPassantTarget.getY() + 1;    // 흑색이 아래로 가면 타겟 위
            Position capturedPawnPos = new Position(enPassantTarget.getX(), capturedY);
            pieces.remove(capturedPawnPos);
            System.out.println(">>> ⚔️ 앙파상! " + capturedPawnPos + "의 폰이 잡혔습니다.");
        }

        // 6. 기물 이동
        Piece capturedPiece = pieces.get(target);
        pieces.put(target, sourcePiece);
        pieces.remove(source);
        sourcePiece.moved();
        
        // 7. 50수 무승부 카운터 업데이트
        if (sourcePiece.is(Type.PAWN) || capturedPiece != null || isEnPassant) {
            // 폰 이동, 기물 포획, 앙파상이면 카운터 리셋
            halfMoveClock = 0;
        } else {
            // 그 외의 경우 카운터 증가
            halfMoveClock++;
        }

        // 7. 앙파상 타겟 업데이트 (폰이 2칸 이동했을 때만)
        enPassantTarget = null; // 기본적으로는 null
        if (sourcePiece.is(Type.PAWN) && Math.abs(source.getY() - target.getY()) == 2) {
            // 2칸 이동한 폰의 바로 뒤 위치가 앙파상 타겟
            int enPassantY = (source.getY() + target.getY()) / 2;
            enPassantTarget = new Position(target.getX(), enPassantY);
        }

        // 8. 캐슬링 시 룩 이동
        if (isCastling) {
            castlingHandler.moveRookForCastling(source, target);
        }

        // 9. 왕 위치 업데이트
        if (sourcePiece.is(Type.KING, Color.WHITE)) {
            whiteKingPosition = target;
        }
        if (sourcePiece.is(Type.KING, Color.BLACK)) {
            blackKingPosition = target;
        }

        // 10. 프로모션 처리
        if (sourcePiece.is(Type.PAWN)) {
            if (promotionHandler.canPromote(target, sourcePiece.getColor())) {
                if (promotionType == null) {
                    promotionType = Type.QUEEN; // 기본값
                }
                Piece promotedPiece = promotionHandler.createPromotedPiece(promotionType, sourcePiece.getColor());
                pieces.put(target, promotedPiece);
            }
        }
    }

    public boolean isChecked(Color kingColor) {
        return createGameRuleChecker().isChecked(kingColor);
    }

    public boolean isCheckMate(Color color) {
        return createGameRuleChecker().isCheckMate(color);
    }

    public boolean isStaleMate(Color color) {
        return createGameRuleChecker().isStaleMate(color);
    }

    public boolean isValidMove(Position source, Position target) {
        return moveValidator.isValidMove(source, target);
    }

    public List<Position> calculateMovablePositions(Position source) {
        // 앙파상 타겟을 MoveValidator에 설정
        moveValidator.setEnPassantTarget(enPassantTarget);
        return createGameRuleChecker().calculateMovablePositions(source);
    }

    /**
     * 50수 무승부 규칙 체크
     * @return 50수(100턴)가 지났으면 true
     */
    public boolean isFiftyMoveRule() {
        return halfMoveClock >= 100; // 50수 = 100턴 (흑백 각각 1턴씩)
    }

    public int getHalfMoveClock() {
        return halfMoveClock;
    }
}

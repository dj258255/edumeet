package com.edu.edumeet.board.domain;

public enum BoardType {
    NORMAL("일반 게시글") {
        @Override
        public BoardType getRecommendedType(long favoriteCount, int threshold) {
            return favoriteCount >= threshold ? RECOMMENDED : NORMAL;
        }

        @Override
        public boolean canTransitionTo(BoardType targetType) {
            return targetType == RECOMMENDED || targetType == NOTICE;
        }
    },

    NOTICE("공지사항") {
        @Override
        public BoardType getRecommendedType(long favoriteCount, int threshold) {
            // 공지사항은 추천 수와 관계없이 공지사항 유지
            return NOTICE;
        }

        @Override
        public boolean canTransitionTo(BoardType targetType) {
            return targetType == NORMAL; // 공지사항에서 일반으로만 전환 가능
        }
    },

    RECOMMENDED("추천 게시글") {
        @Override
        public BoardType getRecommendedType(long favoriteCount, int threshold) {
            return favoriteCount >= threshold ? RECOMMENDED : NORMAL;
        }

        @Override
        public boolean canTransitionTo(BoardType targetType) {
            return targetType == NORMAL || targetType == NOTICE;
        }
    };

    private final String description;

    BoardType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 좋아요 수에 따라 적절한 게시글 타입을 반환
     * @param favoriteCount 현재 좋아요 수
     * @param threshold 추천 기준값
     * @return 적절한 게시글 타입
     */
    public abstract BoardType getRecommendedType(long favoriteCount, int threshold);

    /**
     * 특정 타입으로 전환 가능한지 확인
     * @param targetType 전환하려는 타입
     * @return 전환 가능 여부
     */
    public abstract boolean canTransitionTo(BoardType targetType);

    /**
     * 문자열을 BoardType으로 안전하게 변환
     * @param typeString 변환할 문자열
     * @param defaultType 변환 실패 시 기본값
     * @return BoardType
     */
    public static BoardType safeValueOf(String typeString, BoardType defaultType) {
        if (typeString == null || typeString.trim().isEmpty()) {
            return defaultType;
        }

        try {
            return BoardType.valueOf(typeString.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return defaultType;
        }
    }

    /**
     * 유효한 BoardType 문자열인지 확인
     * @param typeString 확인할 문자열
     * @return 유효 여부
     */
    public static boolean isValid(String typeString) {
        if (typeString == null || typeString.trim().isEmpty()) {
            return false;
        }

        try {
            BoardType.valueOf(typeString.trim().toUpperCase());
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
package com.moment.domain.safety;

/**
 * 신고 사유 열거형 (기능정의서 F-06).
 */
public enum ReportReason {
    OFFENSIVE("욕설/혐오"),
    SPAM("스팸"),
    OTHER("기타");

    private final String displayName;

    ReportReason(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}

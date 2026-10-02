package com.college.noticeboard.entity;

/** Which wing a public notice is for. BOTH shows under Engineering and under Polytechnic. */
public enum InstitutionScope {
    ENGINEERING,
    POLYTECHNIC,
    BOTH;

    public static InstitutionScope of(InstitutionType type) {
        return type == InstitutionType.POLYTECHNIC ? POLYTECHNIC : ENGINEERING;
    }

    public boolean covers(InstitutionType type) {
        return this == BOTH || this == of(type);
    }
}

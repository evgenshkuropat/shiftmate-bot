package cz.shiftmate.domain;

public enum ShiftType {
    EARLY, NIGHT, DAY;

    public ShiftType nextWeek() {
        return switch (this) {
            case EARLY -> NIGHT;
            case NIGHT -> DAY;
            case DAY -> EARLY;
        };
    }
}
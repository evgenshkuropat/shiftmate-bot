package cz.shiftmate.domain;

public enum ShiftType {
    EARLY,
    NIGHT,
    DAY;

    /** Цикл по кругу: ранняя -> ночная -> дневная -> ранняя */
    public ShiftType nextWeek() {
        return switch (this) {
            case EARLY -> NIGHT;
            case NIGHT -> DAY;
            case DAY -> EARLY;
        };
    }
}
package cz.shiftmate.storage;

import cz.shiftmate.domain.ShiftType;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ShiftStorage {

    public static class WeekShift {
        private final LocalDate monday;
        private final ShiftType shiftType;

        public WeekShift(LocalDate monday, ShiftType shiftType) {
            this.monday = monday;
            this.shiftType = shiftType;
        }

        public LocalDate getMonday() {
            return monday;
        }

        public ShiftType getShiftType() {
            return shiftType;
        }
    }

    private final Map<Long, WeekShift> data = new ConcurrentHashMap<>();

    public void setWeekShift(long chatId, LocalDate monday, ShiftType shiftType) {
        data.put(chatId, new WeekShift(monday, shiftType));
    }

    public WeekShift getWeekShift(long chatId) {
        return data.get(chatId);
    }

    public void clear(long chatId) {
        data.remove(chatId);
    }
}
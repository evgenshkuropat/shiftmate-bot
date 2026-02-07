package cz.shiftmate.storage;

import cz.shiftmate.domain.ShiftType;
import jakarta.annotation.PostConstruct;
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

    private final AppDataStore store;
    private final Map<Long, WeekShift> map = new ConcurrentHashMap<>();

    public ShiftStorage(AppDataStore store) {
        this.store = store;
    }

    @PostConstruct
    public void loadFromDisk() {
        var data = store.load();
        for (var e : data.shifts.entrySet()) {
            Long chatId = e.getKey();
            var r = e.getValue();
            try {
                ShiftType type = ShiftType.valueOf(r.shiftType);
                map.put(chatId, new WeekShift(r.monday, type));
            } catch (Exception ignored) {
            }
        }
        System.out.println("✅ Shifts loaded: " + map.size() + " (" + store.getFilePath() + ")");
    }

    public void setWeekShift(long chatId, LocalDate monday, ShiftType shiftType) {
        map.put(chatId, new WeekShift(monday, shiftType));
        persist();
    }

    public WeekShift getWeekShift(long chatId) {
        return map.get(chatId);
    }

    public void clear(long chatId) {
        map.remove(chatId);
        persist();
    }

    private void persist() {
        var data = store.load(); // safe simple approach
        data.shifts.clear();

        for (var e : map.entrySet()) {
            AppDataStore.ShiftRecord r = new AppDataStore.ShiftRecord();
            r.monday = e.getValue().getMonday();
            r.shiftType = e.getValue().getShiftType().name();
            data.shifts.put(e.getKey(), r);
        }

        store.save(data);
    }
}
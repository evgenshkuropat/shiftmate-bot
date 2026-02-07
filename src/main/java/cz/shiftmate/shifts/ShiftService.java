package cz.shiftmate.shifts;

import cz.shiftmate.domain.ShiftType;
import cz.shiftmate.storage.ShiftStorage;
import cz.shiftmate.weather.WeatherFacade;
import org.springframework.stereotype.Service;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;

@Service
public class ShiftService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM");

    private final ShiftStorage storage;
    private final WeatherFacade weather;

    public ShiftService(ShiftStorage storage, WeatherFacade weather) {
        this.storage = storage;
        this.weather = weather;
    }

    public void saveWeekShift(long chatId, ShiftType shiftType) {
        LocalDate monday = effectiveMonday(LocalDate.now());
        storage.setWeekShift(chatId, monday, shiftType);
    }

    public String weekInfo(long chatId) {
        ShiftStorage.WeekShift ws = storage.getWeekShift(chatId);
        if (ws == null) return "Смена не настроена.";
        return "Неделя с " + ws.getMonday().format(DATE_FMT) + " — " + pretty(ws.getShiftType());
    }

    public String currentShiftText(long chatId) {
        ShiftStorage.WeekShift ws = storage.getWeekShift(chatId);
        if (ws == null) return "Смена не настроена. Открой «Моя смена» и выбери смену недели 👇";

        LocalDate today = LocalDate.now();
        ShiftType weekShift = shiftForDate(ws.getMonday(), ws.getShiftType(), today);

        if (!isWorkingDay(today, weekShift)) {
            return "Сегодня выходной 💤\n(" + weekInfo(chatId) + ")";
        }

        if (weekShift == ShiftType.NIGHT) {
            if (today.getDayOfWeek() == DayOfWeek.SUNDAY) {
                LocalTime now = LocalTime.now();
                if (now.isBefore(LocalTime.of(21, 0))) {
                    return "Сегодня ночная, старт в 21:00 ⏳\n(" + weekInfo(chatId) + ")";
                }
                return "Сейчас идёт ночная 🌙 (21:00–06:00)\n(" + weekInfo(chatId) + ")";
            }
            return "Сегодня: Ночная (22:00–06:00)\n(" + weekInfo(chatId) + ")";
        }

        return "Сегодня: " + pretty(weekShift) + "\n(" + weekInfo(chatId) + ")";
    }

    public String scheduleNDays(long chatId, int days, boolean includeWeather) {
        ShiftStorage.WeekShift ws = storage.getWeekShift(chatId);
        if (ws == null) return "Сначала выбери смену недели: «Моя смена» 👇";

        LocalDate baseMonday = ws.getMonday();
        ShiftType baseShift = ws.getShiftType();

        LocalDate today = LocalDate.now();
        LocalDate end = today.plusDays(days - 1);

        StringBuilder sb = new StringBuilder();

        if (includeWeather) {
            sb.append(weather.weatherBlock(today, end)).append("\n");
        }

        sb.append("📅 Расписание на ").append(days).append(" дней:\n\n");

        for (int i = 0; i < days; i++) {
            LocalDate date = today.plusDays(i);

            String label =
                    (i == 0) ? "Сегодня" :
                            (i == 1) ? "Завтра" :
                                    dayName(date.getDayOfWeek());

            ShiftType weekShift = shiftForDate(baseMonday, baseShift, date);

            String shiftText;
            if (!isWorkingDay(date, weekShift)) {
                shiftText = "Выходной";
            } else if (weekShift == ShiftType.NIGHT && date.getDayOfWeek() == DayOfWeek.SUNDAY) {
                shiftText = "Ночная (старт 21:00)";
            } else if (weekShift == ShiftType.NIGHT) {
                shiftText = "Ночная (22:00–06:00)";
            } else {
                shiftText = pretty(weekShift);
            }

            sb.append(label)
                    .append(" (").append(date.format(DATE_FMT)).append(") — ")
                    .append(shiftText)
                    .append("\n");
        }

        String result = sb.toString();
        if (result.length() > 3900) result = result.substring(0, 3900) + "\n…(укорочено)";
        return result;
    }

    // ===== shift math =====

    private ShiftType shiftForDate(LocalDate baseMonday, ShiftType baseShift, LocalDate date) {
        LocalDate anchorMonday = anchorMonday(date);
        long weeksBetween = ChronoUnit.WEEKS.between(baseMonday, anchorMonday);

        long steps = Math.floorMod(weeksBetween, 3);
        ShiftType shift = baseShift;
        for (int i = 0; i < steps; i++) shift = shift.nextWeek();
        return shift;
    }

    private LocalDate anchorMonday(LocalDate date) {
        if (date.getDayOfWeek() == DayOfWeek.SUNDAY) {
            return date.with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        }
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private boolean isWorkingDay(LocalDate date, ShiftType weekShift) {
        DayOfWeek dow = date.getDayOfWeek();

        if (weekShift == ShiftType.EARLY || weekShift == ShiftType.DAY) {
            return dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY;
        }
        // NIGHT: Вс–Пт (6 ночей), суббота выходной
        return dow != DayOfWeek.SATURDAY;
    }

    /** если сегодня сб/вс — сохраняем как следующий понедельник */
    private LocalDate effectiveMonday(LocalDate today) {
        LocalDate thisMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        if (today.getDayOfWeek() == DayOfWeek.SATURDAY || today.getDayOfWeek() == DayOfWeek.SUNDAY) {
            return thisMonday.plusWeeks(1);
        }
        return thisMonday;
    }

    private String dayName(DayOfWeek day) {
        return switch (day) {
            case MONDAY -> "Пн";
            case TUESDAY -> "Вт";
            case WEDNESDAY -> "Ср";
            case THURSDAY -> "Чт";
            case FRIDAY -> "Пт";
            case SATURDAY -> "Сб";
            case SUNDAY -> "Вс";
        };
    }

    private String pretty(ShiftType s) {
        return switch (s) {
            case EARLY -> "Ранняя (6-14)";
            case NIGHT -> "Ночная (22-06)";
            case DAY -> "Дневная (14-22)";
        };
    }
}
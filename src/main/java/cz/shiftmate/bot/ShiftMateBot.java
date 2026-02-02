package cz.shiftmate.bot;

import cz.shiftmate.domain.ShiftType;
import cz.shiftmate.reminders.Reminder;
import cz.shiftmate.reminders.ReminderDraft;
import cz.shiftmate.reminders.ReminderStorage;
import cz.shiftmate.storage.ShiftStorage;
import cz.shiftmate.weather.WeatherService;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

@Component
public class ShiftMateBot implements LongPollingSingleThreadUpdateConsumer {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM");
    private static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter.ofPattern("dd.MM HH:mm");
    private static final DateTimeFormatter DATE_BTN_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final ShiftStorage storage;
    private final WeatherService weatherService;
    private final ReminderStorage reminderStorage;

    private final String token;
    private final String username;

    private TelegramBotsLongPollingApplication app;
    private TelegramClient client;

    public ShiftMateBot(
            ShiftStorage storage,
            WeatherService weatherService,
            ReminderStorage reminderStorage,
            @Value("${telegram.bot.token}") String token,
            @Value("${telegram.bot.username}") String username
    ) {
        this.storage = storage;
        this.weatherService = weatherService;
        this.reminderStorage = reminderStorage;
        this.token = token;
        this.username = username;
    }

    @PostConstruct
    public void init() throws TelegramApiException {
        this.app = new TelegramBotsLongPollingApplication();
        app.registerBot(token, this);
        this.client = new OkHttpTelegramClient(token);
        System.out.println("✅ Bot started: @" + username);
    }

    // ====== Scheduler: checks reminders every 30 seconds ======
    @Scheduled(fixedRate = 30_000)
    public void tickReminders() {
        if (client == null) return;

        LocalDateTime now = LocalDateTime.now();
        var due = reminderStorage.dueNow(now);

        for (Reminder r : due) {
            try {
                String text = "🔔 Напоминание:\n"
                        + r.getTitle() + "\n"
                        + "🗓 " + r.getEventAt().format(DATE_TIME_FMT);

                sendTextOnly(r.getChatId(), text);
                r.markSent();
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public void consume(Update update) {
        if (update.getMessage() == null || update.getMessage().getText() == null) return;

        long chatId = update.getMessage().getChatId();
        String text = update.getMessage().getText().trim();

        try {
            // приоритет: flow добавления напоминания
            if (handleReminderFlow(chatId, text)) return;

            if (text.equals("/start")) {
                send(chatId, "Привет! Выбери действие 👇", mainMenu());
                return;
            }
            if (text.equals("/help")) {
                send(chatId, helpText(), mainMenu());
                return;
            }

            switch (text) {
                // смены
                case "Ранняя (6-14)" -> {
                    saveWeekShift(chatId, ShiftType.EARLY);
                    send(chatId, "Сохранено ✅\n" + weekInfo(chatId), mainMenu());
                }
                case "Ночная (22-06)" -> {
                    saveWeekShift(chatId, ShiftType.NIGHT);
                    send(chatId, "Сохранено ✅\n" + weekInfo(chatId), mainMenu());
                }
                case "Дневная (14-22)" -> {
                    saveWeekShift(chatId, ShiftType.DAY);
                    send(chatId, "Сохранено ✅\n" + weekInfo(chatId), mainMenu());
                }

                case "Моя смена" -> send(chatId, currentShiftText(chatId), mainMenu());
                case "Расписание 7 дней" -> send(chatId, scheduleNDays(chatId, 7), mainMenu());
                case "Расписание 14 дней" -> send(chatId, scheduleNDays(chatId, 14), mainMenu());

                // напоминания
                case "➕ Добавить напоминание" -> send(chatId, "Выбери тип напоминания 👇", reminderTypeMenu());
                case "📋 Мои напоминания" -> send(chatId, listReminders(chatId), remindersMenu());
                case "↩️ Назад" -> send(chatId, "Ок 👇", mainMenu());

                // помощь/сброс
                case "Помощь" -> send(chatId, helpText(), mainMenu());
                case "Сбросить настройку" -> {
                    storage.clear(chatId);
                    send(chatId, "Настройка смены сброшена 🧹\nВыбери смену заново 👇", mainMenu());
                }

                default -> send(chatId, "Не понял команду. Нажми кнопки 👇", mainMenu());
            }
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    // ====== Reminder flow (DATE -> TIME -> OFFSET) ======

    private boolean handleReminderFlow(long chatId, String text) throws TelegramApiException {
        ReminderDraft draft = reminderStorage.draft(chatId);

        // отмена в любой момент
        if ("❌ Отмена".equals(text)) {
            reminderStorage.clearDraft(chatId);
            send(chatId, "Отменил ✅", mainMenu());
            return true;
        }

        // тип напоминания
        if (isReminderType(text)) {
            draft.setTitle(text);
            draft.setDate(null);
            draft.setTime(null);
            draft.setEventAt(null);

            draft.setStep(ReminderDraft.Step.WAIT_DATE);
            send(chatId,
                    "Ок: **" + text + "**\n\n" +
                            "Выбери дату кнопкой 👇\n" +
                            "или введи вручную: `23.02` (или `23.02.2026`)",
                    dateMenu());
            return true;
        }

        // ждём дату
        if (draft.getStep() == ReminderDraft.Step.WAIT_DATE) {
            LocalDate date = parseDateOnly(text);
            if (date == null) {
                send(chatId,
                        "Не понял дату 😅\n" +
                                "Выбери кнопку или введи так:\n" +
                                "• `23.02`\n" +
                                "• `23.02.2026`",
                        dateMenu());
                return true;
            }

            draft.setDate(date);
            draft.setStep(ReminderDraft.Step.WAIT_TIME);

            send(chatId,
                    "Дата: **" + date.format(DATE_BTN_FMT) + "** ✅\n\n" +
                            "Теперь введи время (или выбери):\n" +
                            "• `14:00` или `14.00` или `14`",
                    timeMenu());
            return true;
        }

        // ждём время
        if (draft.getStep() == ReminderDraft.Step.WAIT_TIME) {
            LocalTime time = parseTimeOnly(text);
            if (time == null) {
                send(chatId,
                        "Не понял время 😅\n" +
                                "Попробуй так:\n" +
                                "• `14:00`\n" +
                                "• `14.00`\n" +
                                "• `14`",
                        timeMenu());
                return true;
            }

            draft.setTime(time);

            LocalDate date = draft.getDate();
            if (date == null) {
                // на всякий случай, если draft потерялся
                draft.setStep(ReminderDraft.Step.WAIT_DATE);
                send(chatId, "Давай сначала выберем дату 👇", dateMenu());
                return true;
            }

            LocalDateTime eventAt = LocalDateTime.of(date, time);

            // если уже в прошлом — переносим на следующий год (чтобы “23.02 14:00” работало круглый год)
            if (eventAt.isBefore(LocalDateTime.now().minusMinutes(1))) {
                eventAt = eventAt.plusYears(1);
                draft.setDate(eventAt.toLocalDate());
            }

            draft.setEventAt(eventAt);
            draft.setStep(ReminderDraft.Step.WAIT_NOTIFY_OFFSET);

            send(chatId,
                    "Ок ✅\n🗓 " + eventAt.format(DATE_TIME_FMT) + "\n\nКогда напомнить? 👇",
                    notifyMenu());
            return true;
        }

        // ждём оффсет
        if (draft.getStep() == ReminderDraft.Step.WAIT_NOTIFY_OFFSET) {
            Duration offset = parseOffset(text);
            if (offset == null) {
                send(chatId, "Выбери кнопкой, пожалуйста 👇", notifyMenu());
                return true;
            }

            LocalDateTime eventAt = draft.getEventAt();
            if (eventAt == null) {
                draft.setStep(ReminderDraft.Step.WAIT_DATE);
                send(chatId, "Похоже, дата/время не выбраны. Выбери дату 👇", dateMenu());
                return true;
            }

            LocalDateTime notifyAt = eventAt.minus(offset);

            // если notifyAt уже в прошлом — напомним почти сразу
            if (notifyAt.isBefore(LocalDateTime.now())) {
                notifyAt = LocalDateTime.now().plusSeconds(5);
            }

            Reminder reminder = new Reminder(chatId, draft.getTitle(), eventAt, notifyAt);
            reminderStorage.add(reminder);

            String confirm = "✅ Напоминание сохранено!\n"
                    + reminder.getTitle() + "\n"
                    + "🗓 " + eventAt.format(DATE_TIME_FMT) + "\n"
                    + "🔔 Напомню: " + humanOffset(offset) + " (в " + notifyAt.format(DATE_TIME_FMT) + ")";

            reminderStorage.clearDraft(chatId);
            send(chatId, confirm, remindersMenu());
            return true;
        }

        return false;
    }

    private boolean isReminderType(String text) {
        return "Поход к врачу".equals(text)
                || "Тренировка".equals(text)
                || "Забрать ребёнка".equals(text)
                || "Другое".equals(text);
    }

    /**
     * Принимает дату:
     *  - 04.02.2026 (кнопка)
     *  - 04.02
     *  - 4.2
     *  - 04.02.2026
     *
     * Если ввели без года — используем текущий год, а если уже прошла — переносим на следующий год.
     */
    private LocalDate parseDateOnly(String text) {
        String s = text.trim();

        try {
            LocalDate today = LocalDate.now();

            if (s.matches("^\\d{1,2}\\.\\d{1,2}\\.\\d{4}$")) {
                DateTimeFormatter f = DateTimeFormatter.ofPattern("d.M.yyyy");
                return LocalDate.parse(s, f);
            }

            if (s.matches("^\\d{1,2}\\.\\d{1,2}$")) {
                int year = today.getYear();
                LocalDate d = LocalDate.parse(s + "." + year, DateTimeFormatter.ofPattern("d.M.yyyy"));
                if (d.isBefore(today)) d = d.plusYears(1);
                return d;
            }

            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Принимает время:
     *  - 14:00
     *  - 14.00
     *  - 14
     */
    private LocalTime parseTimeOnly(String text) {
        String s = text.trim().toLowerCase();

        // 14.00 -> 14:00
        s = s.replaceAll("\\b(\\d{1,2})\\.(\\d{2})\\b", "$1:$2");

        // "14" -> "14:00"
        if (s.matches("^\\d{1,2}$")) s = s + ":00";

        try {
            DateTimeFormatter f = DateTimeFormatter.ofPattern("H:mm");
            return LocalTime.parse(s, f);
        } catch (Exception e) {
            return null;
        }
    }

    private Duration parseOffset(String btnText) {
        return switch (btnText) {
            case "🔔 За 30 минут" -> Duration.ofMinutes(30);
            case "🔔 За 1 час" -> Duration.ofHours(1);
            case "🔔 За 3 часа" -> Duration.ofHours(3);
            case "🔔 За 1 день" -> Duration.ofDays(1);
            case "🔔 Без напоминания" -> Duration.ZERO;
            default -> null;
        };
    }

    private String humanOffset(Duration d) {
        if (d.isZero()) return "без напоминания";
        long minutes = d.toMinutes();
        if (minutes == 30) return "за 30 минут";
        if (minutes == 60) return "за 1 час";
        if (minutes == 180) return "за 3 часа";
        if (minutes == 1440) return "за 1 день";
        return "за " + minutes + " минут";
    }

    private String listReminders(long chatId) {
        var list = reminderStorage.list(chatId);
        if (list.isEmpty()) return "Пока нет напоминаний 🙂\nНажми «➕ Добавить напоминание»";

        StringBuilder sb = new StringBuilder("📋 Мои напоминания:\n\n");
        int i = 1;
        for (Reminder r : list) {
            sb.append(i++).append(") ")
                    .append(r.getTitle())
                    .append("\n   🗓 ").append(r.getEventAt().format(DATE_TIME_FMT))
                    .append("\n   🔔 ").append(r.isSent() ? "уже отправлено" : "ожидается")
                    .append("\n\n");
        }
        String s = sb.toString();
        if (s.length() > 3900) s = s.substring(0, 3900) + "\n…(укорочено)";
        return s;
    }

    // ====== Help ======

    private String helpText() {
        return """
                ℹ️ Помощь

                ✅ Смены:
                • выбери смену недели
                • смотри расписание 7/14 дней (с погодой Kolín)

                ✅ Напоминания:
                • «➕ Добавить напоминание»
                • выбери тип
                • выбери дату кнопкой (или введи вручную)
                • выбери/введи время
                • выбери, когда напомнить
                """;
    }

    // ====== Shifts + schedule + weather ======

    private void saveWeekShift(long chatId, ShiftType shiftType) {
        LocalDate monday = effectiveMonday(LocalDate.now());
        storage.setWeekShift(chatId, monday, shiftType);
    }

    private String weekInfo(long chatId) {
        ShiftStorage.WeekShift ws = storage.getWeekShift(chatId);
        if (ws == null) return "Смена не настроена.";
        return "Неделя с " + ws.getMonday().format(DATE_FMT) + " — " + pretty(ws.getShiftType());
    }

    private String currentShiftText(long chatId) {
        ShiftStorage.WeekShift ws = storage.getWeekShift(chatId);
        if (ws == null) return "Смена не настроена. Выбери Ранняя/Ночная/Дневная 👇";

        LocalDate today = LocalDate.now();
        ShiftType weekShift = shiftForDate(ws.getMonday(), ws.getShiftType(), today);

        if (!isWorkingDay(today, weekShift)) {
            return "Сегодня выходной 💤\n(настройка: " + weekInfo(chatId) + ")";
        }

        if (weekShift == ShiftType.NIGHT) {
            if (today.getDayOfWeek() == DayOfWeek.SUNDAY) {
                LocalTime now = LocalTime.now();
                if (now.isBefore(LocalTime.of(21, 0))) {
                    return "Сегодня ночная, старт в 21:00 ⏳\n(настройка: " + weekInfo(chatId) + ")";
                }
                return "Сейчас идёт ночная смена 🌙 (21:00–06:00)\n(настройка: " + weekInfo(chatId) + ")";
            }
            return "Сегодня: Ночная (22:00–06:00)\n(настройка: " + weekInfo(chatId) + ")";
        }

        return "Сегодня: " + pretty(weekShift) + "\n(настройка: " + weekInfo(chatId) + ")";
    }

    private String scheduleNDays(long chatId, int days) {
        ShiftStorage.WeekShift ws = storage.getWeekShift(chatId);
        if (ws == null) return "Сначала выбери смену (Ранняя/Ночная/Дневная) 👇";

        LocalDate baseMonday = ws.getMonday();
        ShiftType baseShift = ws.getShiftType();
        LocalDate today = LocalDate.now();

        LocalDate end = today.plusDays(days - 1);
        String weatherBlock = buildWeatherBlock(today, end);

        StringBuilder sb = new StringBuilder();
        sb.append(weatherBlock).append("\n");
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

    // weather
    private String buildWeatherBlock(LocalDate start, LocalDate end) {
        try {
            var fc = weatherService.getDaily(start, end); // Forecast
            return formatWeather(fc);
        } catch (Exception e) {
            System.err.println("❌ WEATHER ERROR ❌ start=" + start + " end=" + end);
            e.printStackTrace();
            System.err.println("❌ END WEATHER ERROR ❌");
            return "📍Kolín\n🌦 Погода: недоступна сейчас";
        }
    }

    private String formatWeather(cz.shiftmate.weather.Forecast fc) {
        if (fc == null || fc.daily == null || fc.daily.time == null || fc.daily.time.isEmpty()) {
            return "📍Kolín\n🌦 Погода: недоступна сейчас";
        }

        StringBuilder w = new StringBuilder();
        w.append("📍Kolín\n🌦 Погода:\n");

        for (int i = 0; i < fc.daily.time.size(); i++) {
            LocalDate d = LocalDate.parse(fc.daily.time.get(i));
            double tMax = fc.daily.temperature_2m_max.get(i);
            double tMin = fc.daily.temperature_2m_min.get(i);
            int code = fc.daily.weathercode.get(i);

            w.append(d.format(DATE_FMT))
                    .append("  ")
                    .append(Math.round(tMin)).append("°/").append(Math.round(tMax)).append("°  ")
                    .append(weatherIcon(code))
                    .append("\n");
        }
        return w.toString();
    }

    private String weatherIcon(int code) {
        if (code == 0) return "☀️";
        if (code == 1 || code == 2) return "🌤";
        if (code == 3) return "☁️";
        if (code >= 45 && code <= 48) return "🌫";
        if (code >= 51 && code <= 67) return "🌧";
        if (code >= 71 && code <= 77) return "🌨";
        if (code >= 80 && code <= 82) return "🌧";
        if (code >= 85 && code <= 86) return "🌨";
        if (code >= 95) return "⛈";
        return "🌡";
    }

    // shift core
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
        // NIGHT: Вс–Пт (6 ночей), Сб выходной
        return dow != DayOfWeek.SATURDAY;
    }

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

    // ====== Telegram helpers ======

    private void send(long chatId, String text, ReplyKeyboardMarkup keyboard) throws TelegramApiException {
        SendMessage msg = new SendMessage(String.valueOf(chatId), text);
        msg.enableMarkdown(true);
        msg.setReplyMarkup(keyboard);
        client.execute(msg);
    }

    private void sendTextOnly(long chatId, String text) throws TelegramApiException {
        SendMessage msg = new SendMessage(String.valueOf(chatId), text);
        client.execute(msg);
    }

    // ====== menus ======

    private ReplyKeyboardMarkup mainMenu() {
        KeyboardRow row1 = new KeyboardRow(List.of(
                new KeyboardButton("Ранняя (6-14)"),
                new KeyboardButton("Ночная (22-06)")
        ));
        KeyboardRow row2 = new KeyboardRow(List.of(
                new KeyboardButton("Дневная (14-22)"),
                new KeyboardButton("Моя смена")
        ));
        KeyboardRow row3 = new KeyboardRow(List.of(
                new KeyboardButton("Расписание 7 дней"),
                new KeyboardButton("Расписание 14 дней")
        ));
        KeyboardRow row4 = new KeyboardRow(List.of(
                new KeyboardButton("➕ Добавить напоминание"),
                new KeyboardButton("📋 Мои напоминания")
        ));
        KeyboardRow row5 = new KeyboardRow(List.of(
                new KeyboardButton("Помощь"),
                new KeyboardButton("Сбросить настройку")
        ));

        ReplyKeyboardMarkup kb = new ReplyKeyboardMarkup(List.of(row1, row2, row3, row4, row5));
        kb.setResizeKeyboard(true);
        kb.setOneTimeKeyboard(false);
        return kb;
    }

    private ReplyKeyboardMarkup remindersMenu() {
        KeyboardRow row1 = new KeyboardRow(List.of(
                new KeyboardButton("➕ Добавить напоминание"),
                new KeyboardButton("📋 Мои напоминания")
        ));
        KeyboardRow row2 = new KeyboardRow(List.of(
                new KeyboardButton("↩️ Назад")
        ));
        ReplyKeyboardMarkup kb = new ReplyKeyboardMarkup(List.of(row1, row2));
        kb.setResizeKeyboard(true);
        return kb;
    }

    private ReplyKeyboardMarkup reminderTypeMenu() {
        KeyboardRow row1 = new KeyboardRow(List.of(
                new KeyboardButton("Поход к врачу"),
                new KeyboardButton("Тренировка")
        ));
        KeyboardRow row2 = new KeyboardRow(List.of(
                new KeyboardButton("Забрать ребёнка"),
                new KeyboardButton("Другое")
        ));
        KeyboardRow row3 = new KeyboardRow(List.of(
                new KeyboardButton("❌ Отмена")
        ));

        ReplyKeyboardMarkup kb = new ReplyKeyboardMarkup(List.of(row1, row2, row3));
        kb.setResizeKeyboard(true);
        return kb;
    }

    private ReplyKeyboardMarkup notifyMenu() {
        KeyboardRow row1 = new KeyboardRow(List.of(
                new KeyboardButton("🔔 За 30 минут"),
                new KeyboardButton("🔔 За 1 час")
        ));
        KeyboardRow row2 = new KeyboardRow(List.of(
                new KeyboardButton("🔔 За 3 часа"),
                new KeyboardButton("🔔 За 1 день")
        ));
        KeyboardRow row3 = new KeyboardRow(List.of(
                new KeyboardButton("🔔 Без напоминания"),
                new KeyboardButton("❌ Отмена")
        ));

        ReplyKeyboardMarkup kb = new ReplyKeyboardMarkup(List.of(row1, row2, row3));
        kb.setResizeKeyboard(true);
        return kb;
    }

    private ReplyKeyboardMarkup dateMenu() {
        LocalDate today = LocalDate.now();

        // 3 даты как в примере (можно расширить на 7 — скажешь)
        LocalDate d1 = today.plusDays(0);
        LocalDate d2 = today.plusDays(1);
        LocalDate d3 = today.plusDays(3);

        KeyboardRow row1 = new KeyboardRow(List.of(
                new KeyboardButton(d1.format(DATE_BTN_FMT)),
                new KeyboardButton(d2.format(DATE_BTN_FMT))
        ));
        KeyboardRow row2 = new KeyboardRow(List.of(
                new KeyboardButton(d3.format(DATE_BTN_FMT))
        ));
        KeyboardRow row3 = new KeyboardRow(List.of(
                new KeyboardButton("❌ Отмена")
        ));

        ReplyKeyboardMarkup kb = new ReplyKeyboardMarkup(List.of(row1, row2, row3));
        kb.setResizeKeyboard(true);
        return kb;
    }

    private ReplyKeyboardMarkup timeMenu() {
        KeyboardRow row1 = new KeyboardRow(List.of(
                new KeyboardButton("09:00"),
                new KeyboardButton("14:00"),
                new KeyboardButton("18:00")
        ));
        KeyboardRow row2 = new KeyboardRow(List.of(
                new KeyboardButton("20:00"),
                new KeyboardButton("21:00"),
                new KeyboardButton("22:00")
        ));
        KeyboardRow row3 = new KeyboardRow(List.of(
                new KeyboardButton("❌ Отмена")
        ));

        ReplyKeyboardMarkup kb = new ReplyKeyboardMarkup(List.of(row1, row2, row3));
        kb.setResizeKeyboard(true);
        return kb;
    }
}
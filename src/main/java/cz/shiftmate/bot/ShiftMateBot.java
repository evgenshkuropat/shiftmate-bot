package cz.shiftmate.bot;

import cz.shiftmate.domain.ShiftType;
import cz.shiftmate.storage.ShiftStorage;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
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

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

@Component
public class ShiftMateBot implements LongPollingSingleThreadUpdateConsumer {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM");

    private final ShiftStorage storage;
    private final String token;
    private final String username;

    private TelegramBotsLongPollingApplication app;
    private TelegramClient client;

    public ShiftMateBot(
            ShiftStorage storage,
            @Value("${telegram.bot.token}") String token,
            @Value("${telegram.bot.username}") String username
    ) {
        this.storage = storage;
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

    @Override
    public void consume(Update update) {
        if (update.getMessage() == null || update.getMessage().getText() == null) return;

        long chatId = update.getMessage().getChatId();
        String text = update.getMessage().getText().trim();

        try {
            if (text.equals("/start")) {
                send(chatId, "Привет! Выбери смену 👇", mainMenu());
                return;
            }
            if (text.equals("/help")) {
                send(chatId, helpText(), mainMenu());
                return;
            }

            switch (text) {
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

                case "Помощь" -> send(chatId, helpText(), mainMenu());

                case "Сбросить настройку" -> {
                    storage.clear(chatId);
                    send(chatId, "Настройка сброшена 🧹\nВыбери смену заново 👇", mainMenu());
                }

                default -> send(chatId, "Не понял команду. Нажми кнопки 👇", mainMenu());
            }
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private String helpText() {
        return """
                ℹ️ Помощь

                Выбери смену недели:
                • Ранняя (6-14)
                • Ночная (22-06)
                • Дневная (14-22)

                Кнопки:
                • Моя смена — что сегодня
                • Расписание 7/14 дней — график с датами
                • Сбросить настройку — очистить выбор

                ⚙️ Ночная:
                • Вс–Пт рабочие (6 ночей)
                • Вс старт 21:00, Пн–Пт старт 22:00 (конец 06:00)
                """;
    }

    // ====== storage ======

    private void saveWeekShift(long chatId, ShiftType shiftType) {
        LocalDate monday = effectiveMonday(LocalDate.now());
        storage.setWeekShift(chatId, monday, shiftType);
    }

    private String weekInfo(long chatId) {
        ShiftStorage.WeekShift ws = storage.getWeekShift(chatId);
        if (ws == null) return "Смена не настроена.";
        return "Неделя с " + ws.getMonday().format(DATE_FMT) + " — " + pretty(ws.getShiftType());
    }

    // ====== main features ======

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
                // Воскресенье: старт 21:00
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

        StringBuilder sb = new StringBuilder("📅 Расписание на " + days + " дней:\n\n");

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

        return sb.toString();
    }

    // ====== core logic ======

    /**
     * ВАЖНО: воскресенье относим к следующему понедельнику (потому что ночная начинается в вс вечером).
     */
    private ShiftType shiftForDate(LocalDate baseMonday, ShiftType baseShift, LocalDate date) {
        LocalDate anchorMonday = anchorMonday(date); // <-- ключевая правка
        long weeksBetween = ChronoUnit.WEEKS.between(baseMonday, anchorMonday);

        long steps = Math.floorMod(weeksBetween, 3);
        ShiftType shift = baseShift;
        for (int i = 0; i < steps; i++) shift = shift.nextWeek();
        return shift;
    }

    private LocalDate anchorMonday(LocalDate date) {
        // если воскресенье — считаем его частью следующей недели (следующий понедельник)
        if (date.getDayOfWeek() == DayOfWeek.SUNDAY) {
            return date.with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        }
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    /**
     * Рабочие дни:
     * - EARLY/DAY: Пн–Пт работа, Сб/Вс выходные
     * - NIGHT: Вс–Пт работа (6 ночей), Сб выходной
     *
     * Здесь воскресенье может быть рабочим (для NIGHT), но оно относится к следующей неделе — это учитывает shiftForDate().
     */
    private boolean isWorkingDay(LocalDate date, ShiftType weekShift) {
        DayOfWeek dow = date.getDayOfWeek();

        if (weekShift == ShiftType.EARLY || weekShift == ShiftType.DAY) {
            return dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY;
        }

        // NIGHT: всё кроме субботы
        return dow != DayOfWeek.SATURDAY;
    }

    /**
     * Выбор смены делаем со следующего понедельника, если сейчас Сб или Вс.
     * Это решает твой кейс: выбрал Ночную в воскресенье — неделя будет с завтрашнего понедельника,
     * но расписание всё равно покажет, что сегодня (вс) старт 21:00.
     */
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
        msg.setReplyMarkup(keyboard);
        client.execute(msg);
    }

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
                new KeyboardButton("Помощь"),
                new KeyboardButton("Сбросить настройку")
        ));

        ReplyKeyboardMarkup kb = new ReplyKeyboardMarkup(List.of(row1, row2, row3, row4));
        kb.setResizeKeyboard(true);
        kb.setOneTimeKeyboard(false);
        return kb;
    }
}
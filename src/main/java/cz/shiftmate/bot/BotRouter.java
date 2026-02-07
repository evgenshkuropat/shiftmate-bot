package cz.shiftmate.bot;

import cz.shiftmate.domain.ShiftType;
import cz.shiftmate.reminders.ReminderFlowHandler;
import cz.shiftmate.shifts.ShiftService;
import cz.shiftmate.storage.ShiftStorage;
import cz.shiftmate.ui.MenuFactory;
import cz.shiftmate.weather.WeatherFacade;
import org.springframework.stereotype.Component;

@Component
public class BotRouter {

    private final BotSender sender;
    private final MenuFactory menus;
    private final ShiftService shifts;
    private final WeatherFacade weather;
    private final ReminderFlowHandler reminders;
    private final ShiftStorage shiftStorage;

    public BotRouter(
            BotSender sender,
            MenuFactory menus,
            ShiftService shifts,
            WeatherFacade weather,
            ReminderFlowHandler reminders,
            ShiftStorage shiftStorage
    ) {
        this.sender = sender;
        this.menus = menus;
        this.shifts = shifts;
        this.weather = weather;
        this.reminders = reminders;
        this.shiftStorage = shiftStorage;
    }

    public void onText(long chatId, String text) {
        if (!sender.isReady()) return;

        // 1) reminder flow has priority
        if (reminders.handle(chatId, text)) return;

        // 2) basic commands
        if ("/start".equals(text)) {
            sender.send(chatId, "Привет! Выбери действие 👇", menus.mainMenu());
            return;
        }
        if ("/help".equals(text) || "Помощь".equals(text)) {
            sender.send(chatId, helpText(), menus.mainMenu());
            return;
        }

        // 3) navigation
        if ("↩️ Назад".equals(text)) {
            sender.send(chatId, "Ок 👇", menus.mainMenu());
            return;
        }

        // 4) main menu actions
        switch (text) {
            case "Моя смена" -> {
                // если нет настроек — покажем подсказку + только выбор смены
                ShiftStorage.WeekShift ws = shiftStorage.getWeekShift(chatId);
                if (ws == null) {
                    sender.send(chatId,
                            "Смена ещё не выбрана.\n\n" +
                                    "Выбери смену недели (понедельник–пятница).\n" +
                                    "Для ночной: Вс–Пт (6 ночей), в воскресенье старт 21:00.",
                            menus.shiftMenu(false));
                } else {
                    sender.send(chatId, shifts.weekInfo(chatId) + "\nВыбери действие 👇", menus.shiftMenu(true));
                }
            }

            case "Погода" -> sender.send(chatId, "Выбери период погоды 👇", menus.weatherMenu());

            case "➕ Добавить напоминание" ->
                    sender.send(chatId, "Выбери тип напоминания 👇", menus.reminderTypeMenu());

            case "🗒 Мои напоминания" ->
                    sender.send(chatId, reminders.listReminders(chatId), menus.remindersMenu());

            case "Сбросить настройку" -> {
                shiftStorage.clear(chatId);
                sender.send(chatId, "Настройка смены сброшена 🧹\nОткрой «Моя смена» и выбери заново.", menus.mainMenu());
            }

            default -> routeSubmenus(chatId, text);
        }
    }

    private void routeSubmenus(long chatId, String text) {
        // SHIFT submenu commands
        switch (text) {
            case "Ранняя (6-14)" -> {
                shifts.saveWeekShift(chatId, ShiftType.EARLY);
                sender.send(chatId, "Сохранено ✅\n" + shifts.weekInfo(chatId), menus.shiftMenu(true));
                return;
            }
            case "Ночная (22-06)" -> {
                shifts.saveWeekShift(chatId, ShiftType.NIGHT);
                sender.send(chatId, "Сохранено ✅\n" + shifts.weekInfo(chatId), menus.shiftMenu(true));
                return;
            }
            case "Дневная (14-22)" -> {
                shifts.saveWeekShift(chatId, ShiftType.DAY);
                sender.send(chatId, "Сохранено ✅\n" + shifts.weekInfo(chatId), menus.shiftMenu(true));
                return;
            }
            case "Текущая смена" -> {
                sender.send(chatId, shifts.currentShiftText(chatId), menus.shiftMenu(true));
                return;
            }
            case "Смена 7 дней" -> {
                sender.send(chatId, shifts.scheduleNDays(chatId, 7, true), menus.shiftMenu(true));
                return;
            }
            case "Смена 14 дней" -> {
                sender.send(chatId, shifts.scheduleNDays(chatId, 14, true), menus.shiftMenu(true));
                return;
            }
        }

        // WEATHER submenu commands
        switch (text) {
            case "Погода сегодня" -> {
                sender.send(chatId, weather.weatherOnly(1), menus.weatherMenu());
                return;
            }
            case "Погода 7 дней" -> {
                sender.send(chatId, weather.weatherOnly(7), menus.weatherMenu());
                return;
            }
            case "Погода 14 дней" -> {
                sender.send(chatId, weather.weatherOnly(14), menus.weatherMenu());
                return;
            }
        }

        sender.send(chatId, "Не понял команду. Нажми кнопки 👇", menus.mainMenu());
    }

    private String helpText() {
        return """
                ℹ️ Помощь

                ✅ Смены:
                • «Моя смена» → выбери смену недели
                • затем доступны: текущая смена / расписание 7/14 дней

                ✅ Погода:
                • «Погода» → сегодня / 7 / 14 дней (Kolín)

                ✅ Напоминания:
                • «➕ Добавить напоминание»
                • выбери тип → выбери дату кнопкой или введи
                • затем выбери время (кнопка или ввод)
                • затем выбери, когда напомнить
                """;
    }
}
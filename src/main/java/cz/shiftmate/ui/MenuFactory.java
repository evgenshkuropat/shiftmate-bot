package cz.shiftmate.ui;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Component
public class MenuFactory {

    private static final DateTimeFormatter DATE_FULL = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    public ReplyKeyboardMarkup mainMenu() {
        List<KeyboardRow> rows = new ArrayList<>();

        rows.add(new KeyboardRow(List.of(
                new KeyboardButton("Моя смена"),
                new KeyboardButton("Погода")
        )));
        rows.add(new KeyboardRow(List.of(
                new KeyboardButton("➕ Добавить напоминание"),
                new KeyboardButton("🗒 Мои напоминания")
        )));
        rows.add(new KeyboardRow(List.of(
                new KeyboardButton("Помощь"),
                new KeyboardButton("Сбросить настройку")
        )));

        ReplyKeyboardMarkup kb = new ReplyKeyboardMarkup(rows);
        kb.setResizeKeyboard(true);
        kb.setOneTimeKeyboard(false);
        return kb;
    }

    /**
     * shiftChosen=false -> показываем только выбор смены + назад
     * shiftChosen=true  -> показываем выбор смены + текущая/7/14 + назад
     */
    public ReplyKeyboardMarkup shiftMenu(boolean shiftChosen) {
        List<KeyboardRow> rows = new ArrayList<>();

        rows.add(new KeyboardRow(List.of(
                new KeyboardButton("Ранняя (6-14)"),
                new KeyboardButton("Ночная (22-06)")
        )));
        rows.add(new KeyboardRow(List.of(
                new KeyboardButton("Дневная (14-22)")
        )));

        if (shiftChosen) {
            rows.add(new KeyboardRow(List.of(
                    new KeyboardButton("Текущая смена")
            )));
            rows.add(new KeyboardRow(List.of(
                    new KeyboardButton("Смена 7 дней"),
                    new KeyboardButton("Смена 14 дней")
            )));
        }

        rows.add(new KeyboardRow(List.of(new KeyboardButton("↩️ Назад"))));

        ReplyKeyboardMarkup kb = new ReplyKeyboardMarkup(rows);
        kb.setResizeKeyboard(true);
        return kb;
    }

    public ReplyKeyboardMarkup weatherMenu() {
        List<KeyboardRow> rows = new ArrayList<>();
        rows.add(new KeyboardRow(List.of(
                new KeyboardButton("Погода сегодня"),
                new KeyboardButton("Погода 7 дней")
        )));
        rows.add(new KeyboardRow(List.of(
                new KeyboardButton("Погода 14 дней")
        )));
        rows.add(new KeyboardRow(List.of(new KeyboardButton("↩️ Назад"))));

        ReplyKeyboardMarkup kb = new ReplyKeyboardMarkup(rows);
        kb.setResizeKeyboard(true);
        return kb;
    }

    public ReplyKeyboardMarkup remindersMenu() {
        List<KeyboardRow> rows = new ArrayList<>();
        rows.add(new KeyboardRow(List.of(
                new KeyboardButton("➕ Добавить напоминание"),
                new KeyboardButton("🗒 Мои напоминания")
        )));
        rows.add(new KeyboardRow(List.of(new KeyboardButton("↩️ Назад"))));

        ReplyKeyboardMarkup kb = new ReplyKeyboardMarkup(rows);
        kb.setResizeKeyboard(true);
        return kb;
    }

    public ReplyKeyboardMarkup reminderTypeMenu() {
        List<KeyboardRow> rows = new ArrayList<>();

        rows.add(new KeyboardRow(List.of(
                new KeyboardButton("Поход к врачу"),
                new KeyboardButton("Тренировка")
        )));
        rows.add(new KeyboardRow(List.of(
                new KeyboardButton("Забрать ребёнка"),
                new KeyboardButton("Другое")
        )));
        rows.add(new KeyboardRow(List.of(
                new KeyboardButton("❌ Отмена")
        )));

        ReplyKeyboardMarkup kb = new ReplyKeyboardMarkup(rows);
        kb.setResizeKeyboard(true);
        return kb;
    }

    /** Пример выбора даты кнопками (следующие 10 дней) */
    public ReplyKeyboardMarkup datePickMenu() {
        List<KeyboardRow> rows = new ArrayList<>();
        LocalDate today = LocalDate.now();

        // 5 рядов по 2 даты = 10 дней
        for (int i = 0; i < 10; i += 2) {
            String d1 = today.plusDays(i).format(DATE_FULL);
            String d2 = today.plusDays(i + 1).format(DATE_FULL);
            rows.add(new KeyboardRow(List.of(new KeyboardButton(d1), new KeyboardButton(d2))));
        }

        rows.add(new KeyboardRow(List.of(
                new KeyboardButton("❌ Отмена")
        )));

        ReplyKeyboardMarkup kb = new ReplyKeyboardMarkup(rows);
        kb.setResizeKeyboard(true);
        return kb;
    }

    /** Популярные времена + возможность ввести вручную */
    public ReplyKeyboardMarkup timePickMenu() {
        List<KeyboardRow> rows = new ArrayList<>();

        rows.add(new KeyboardRow(List.of(
                new KeyboardButton("09:00"),
                new KeyboardButton("12:00")
        )));
        rows.add(new KeyboardRow(List.of(
                new KeyboardButton("14:00"),
                new KeyboardButton("18:00")
        )));
        rows.add(new KeyboardRow(List.of(
                new KeyboardButton("20:00"),
                new KeyboardButton("❌ Отмена")
        )));

        ReplyKeyboardMarkup kb = new ReplyKeyboardMarkup(rows);
        kb.setResizeKeyboard(true);
        return kb;
    }

    public ReplyKeyboardMarkup notifyMenu() {
        List<KeyboardRow> rows = new ArrayList<>();
        rows.add(new KeyboardRow(List.of(
                new KeyboardButton("🔔 За 30 минут"),
                new KeyboardButton("🔔 За 1 час")
        )));
        rows.add(new KeyboardRow(List.of(
                new KeyboardButton("🔔 За 3 часа"),
                new KeyboardButton("🔔 За 1 день")
        )));
        rows.add(new KeyboardRow(List.of(
                new KeyboardButton("🔔 Без напоминания"),
                new KeyboardButton("❌ Отмена")
        )));

        ReplyKeyboardMarkup kb = new ReplyKeyboardMarkup(rows);
        kb.setResizeKeyboard(true);
        return kb;
    }
}
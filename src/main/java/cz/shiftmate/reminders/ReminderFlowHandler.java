package cz.shiftmate.reminders;

import cz.shiftmate.bot.BotSender;
import cz.shiftmate.ui.MenuFactory;
import org.springframework.stereotype.Component;

import java.time.*;
import java.time.format.DateTimeFormatter;

@Component
public class ReminderFlowHandler {

    private static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter.ofPattern("dd.MM HH:mm");
    private static final DateTimeFormatter DATE_FULL = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final ReminderStorage storage;
    private final BotSender sender;
    private final MenuFactory menus;

    public ReminderFlowHandler(ReminderStorage storage, BotSender sender, MenuFactory menus) {
        this.storage = storage;
        this.sender = sender;
        this.menus = menus;
    }

    public boolean handle(long chatId, String text) {
        if ("❌ Отмена".equals(text)) {
            storage.clearDraft(chatId);
            return false; // пусть router обработает (вернёт в main menu)
        }

        ReminderDraft draft = storage.draft(chatId);

        // 1) выбираем тип напоминания (кнопкой)
        if (isReminderType(text)) {
            draft.reset();
            draft.setTitle(text);
            draft.setStep(ReminderDraft.Step.WAIT_DATE);

            sender.send(chatId,
                    "Ок: **" + text + "**\n\n" +
                            "Выбери дату кнопкой ниже 👇\n" +
                            "или введи вручную в формате:\n" +
                            "• `23.02`\n" +
                            "• `23.02.2026`",
                    menus.datePickMenu());
            return true;
        }

        // 2) ждём дату
        if (draft.getStep() == ReminderDraft.Step.WAIT_DATE) {
            LocalDate date = parseDate(text);
            if (date == null) {
                sender.send(chatId,
                        "Не понял дату 😅\n" +
                                "Выбери дату кнопкой или введи:\n" +
                                "• `23.02`\n" +
                                "• `23.02.2026`",
                        menus.datePickMenu());
                return true;
            }

            draft.setDate(date);
            draft.setStep(ReminderDraft.Step.WAIT_TIME);

            sender.send(chatId,
                    "Дата ок ✅: **" + date.format(DATE_FULL) + "**\n\n" +
                            "Теперь выбери время кнопкой 👇\n" +
                            "или введи вручную `14:00`",
                    menus.timePickMenu());
            return true;
        }

        // 3) ждём время
        if (draft.getStep() == ReminderDraft.Step.WAIT_TIME) {
            LocalTime time = parseTime(text);
            if (time == null) {
                sender.send(chatId,
                        "Не понял время 😅\n" +
                                "Выбери кнопкой или введи `14:00`",
                        menus.timePickMenu());
                return true;
            }

            draft.setTime(time);

            LocalDateTime eventAt = LocalDateTime.of(draft.getDate(), time);
            // если пользователь ввёл дату без года и она уже прошла — переносим на следующий год
            if (eventAt.isBefore(LocalDateTime.now().minusMinutes(1))) {
                eventAt = eventAt.plusYears(1);
            }
            draft.setEventAt(eventAt);

            draft.setStep(ReminderDraft.Step.WAIT_NOTIFY_OFFSET);

            sender.send(chatId,
                    "Отлично ✅\n" +
                            "Событие: " + draft.getTitle() + "\n" +
                            "🗓 " + eventAt.format(DATE_TIME_FMT) + "\n\n" +
                            "Когда напомнить? 👇",
                    menus.notifyMenu());
            return true;
        }

        // 4) ждём оффсет
        if (draft.getStep() == ReminderDraft.Step.WAIT_NOTIFY_OFFSET) {
            Duration offset = parseOffset(text);
            if (offset == null) {
                sender.send(chatId, "Выбери кнопкой, пожалуйста 👇", menus.notifyMenu());
                return true;
            }

            LocalDateTime eventAt = draft.getEventAt();
            LocalDateTime notifyAt = eventAt.minus(offset);

            if (notifyAt.isBefore(LocalDateTime.now())) {
                notifyAt = LocalDateTime.now().plusSeconds(5);
            }

            Reminder reminder = new Reminder(chatId, draft.getTitle(), eventAt, notifyAt);
            storage.add(reminder);

            String confirm = "✅ Напоминание сохранено!\n"
                    + reminder.getTitle() + "\n"
                    + "🗓 " + eventAt.format(DATE_TIME_FMT) + "\n"
                    + "🔔 Напомню: " + humanOffset(offset) + " (в " + notifyAt.format(DATE_TIME_FMT) + ")";

            storage.clearDraft(chatId);
            sender.send(chatId, confirm, menus.remindersMenu());
            return true;
        }

        return false;
    }

    public String listReminders(long chatId) {
        var list = storage.list(chatId);
        if (list.isEmpty()) return "Пока нет напоминаний 🙂\nНажми «➕ Добавить напоминание»";

        StringBuilder sb = new StringBuilder("🗒 Мои напоминания:\n\n");
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

    private boolean isReminderType(String text) {
        return "Поход к врачу".equals(text)
                || "Тренировка".equals(text)
                || "Забрать ребёнка".equals(text)
                || "Другое".equals(text);
    }

    private LocalDate parseDate(String text) {
        String s = text.trim();

        // кнопки формата dd.MM.yyyy
        if (s.matches("^\\d{2}\\.\\d{2}\\.\\d{4}$")) {
            try {
                return LocalDate.parse(s, DATE_FULL);
            } catch (Exception ignored) {
            }
        }

        // ввод dd.MM (год берём текущий; если уже прошло — переносим на следующий год)
        if (s.matches("^\\d{1,2}\\.\\d{1,2}$")) {
            try {
                int year = LocalDate.now().getYear();
                DateTimeFormatter fmt = DateTimeFormatter.ofPattern("d.M.yyyy");
                LocalDate d = LocalDate.parse(s + "." + year, fmt);
                if (d.isBefore(LocalDate.now().minusDays(1))) {
                    d = d.plusYears(1);
                }
                return d;
            } catch (Exception ignored) {
            }
        }

        return null;
    }

    private LocalTime parseTime(String text) {
        String s = text.trim();

        // "14.00" -> "14:00"
        s = s.replaceAll("^(\\d{1,2})\\.(\\d{2})$", "$1:$2");

        // "14" -> "14:00"
        if (s.matches("^\\d{1,2}$")) s = s + ":00";

        if (!s.matches("^\\d{1,2}:\\d{2}$")) return null;

        try {
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("H:mm");
            return LocalTime.parse(s, fmt);
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
}
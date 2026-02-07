package cz.shiftmate.reminders;

import cz.shiftmate.bot.BotSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component
public class ReminderScheduler {

    private static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter.ofPattern("dd.MM HH:mm");

    private final ReminderStorage storage;
    private final BotSender sender;

    public ReminderScheduler(ReminderStorage storage, BotSender sender) {
        this.storage = storage;
        this.sender = sender;
    }

    @Scheduled(fixedRate = 30_000)
    public void tick() {
        if (!sender.isReady()) return;

        LocalDateTime now = LocalDateTime.now();
        var due = storage.dueNow(now);

        boolean changed = false;

        for (Reminder r : due) {
            try {
                String text = "🔔 Напоминание:\n"
                        + r.getTitle() + "\n"
                        + "🗓 " + r.getEventAt().format(DATE_TIME_FMT);

                sender.sendTextOnly(r.getChatId(), text);
                r.markSent();
                changed = true;
            } catch (Exception ignored) {
            }
        }

        if (changed) storage.persist();
    }
}
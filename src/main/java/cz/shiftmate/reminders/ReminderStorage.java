package cz.shiftmate.reminders;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
public class ReminderStorage {

    // черновик на пользователя
    private final Map<Long, ReminderDraft> drafts = new ConcurrentHashMap<>();

    // список напоминаний на пользователя
    private final Map<Long, List<Reminder>> reminders = new ConcurrentHashMap<>();

    public ReminderDraft draft(long chatId) {
        return drafts.computeIfAbsent(chatId, id -> new ReminderDraft());
    }

    public void clearDraft(long chatId) {
        ReminderDraft d = drafts.get(chatId);
        if (d != null) d.reset();
    }

    public void add(Reminder reminder) {
        reminders.computeIfAbsent(reminder.getChatId(), id -> Collections.synchronizedList(new ArrayList<>()))
                .add(reminder);
    }

    public List<Reminder> list(long chatId) {
        return reminders.getOrDefault(chatId, Collections.emptyList())
                .stream()
                .sorted(Comparator.comparing(Reminder::getEventAt))
                .collect(Collectors.toList());
    }

    /**
     * Возвращает напоминания, которые пора отправить (notifyAt <= now), и которые ещё не отправлены.
     */
    public List<Reminder> dueNow(LocalDateTime now) {
        List<Reminder> result = new ArrayList<>();
        for (var entry : reminders.entrySet()) {
            for (Reminder r : entry.getValue()) {
                if (!r.isSent() && !r.getNotifyAt().isAfter(now)) {
                    result.add(r);
                }
            }
        }
        // чтоб отправлялись по времени
        result.sort(Comparator.comparing(Reminder::getNotifyAt));
        return result;
    }
}
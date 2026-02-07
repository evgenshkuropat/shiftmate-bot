package cz.shiftmate.reminders;

import cz.shiftmate.storage.AppDataStore;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ReminderStorage {

    private final AppDataStore store;

    private final Map<Long, List<Reminder>> reminders = new ConcurrentHashMap<>();
    private final Map<Long, ReminderDraft> drafts = new ConcurrentHashMap<>();

    public ReminderStorage(AppDataStore store) {
        this.store = store;
    }

    @PostConstruct
    public void loadFromDisk() {
        var data = store.load();
        int count = 0;

        for (var entry : data.reminders.entrySet()) {
            long chatId = entry.getKey();
            List<Reminder> list = new ArrayList<>();

            for (var rr : entry.getValue()) {
                Reminder r = new Reminder(rr.chatId, rr.title, rr.eventAt, rr.notifyAt);
                if (rr.sent) r.markSent();
                list.add(r);
                count++;
            }
            reminders.put(chatId, list);
        }

        System.out.println("✅ Reminders loaded: " + count + " (" + store.getFilePath() + ")");
    }

    public ReminderDraft draft(long chatId) {
        return drafts.computeIfAbsent(chatId, id -> new ReminderDraft());
    }

    public void clearDraft(long chatId) {
        drafts.remove(chatId);
    }

    public void add(Reminder r) {
        reminders.computeIfAbsent(r.getChatId(), id -> new ArrayList<>()).add(r);
        persist();
    }

    public List<Reminder> list(long chatId) {
        return reminders.getOrDefault(chatId, List.of());
    }

    public List<Reminder> dueNow(LocalDateTime now) {
        List<Reminder> out = new ArrayList<>();
        for (var entry : reminders.entrySet()) {
            for (Reminder r : entry.getValue()) {
                if (!r.isSent() && !r.getNotifyAt().isAfter(now)) {
                    out.add(r);
                }
            }
        }
        return out;
    }

    /** Важно: после markSent() нужно вызвать это */
    public void persist() {
        var data = store.load();
        data.reminders.clear();

        for (var e : reminders.entrySet()) {
            long chatId = e.getKey();
            List<AppDataStore.ReminderRecord> list = new ArrayList<>();

            for (Reminder r : e.getValue()) {
                AppDataStore.ReminderRecord rr = new AppDataStore.ReminderRecord();
                rr.chatId = r.getChatId();
                rr.title = r.getTitle();
                rr.eventAt = r.getEventAt();
                rr.notifyAt = r.getNotifyAt();
                rr.sent = r.isSent();
                list.add(rr);
            }
            data.reminders.put(chatId, list);
        }

        store.save(data);
    }
}

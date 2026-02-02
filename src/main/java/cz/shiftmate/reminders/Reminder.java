package cz.shiftmate.reminders;

import java.time.LocalDateTime;

public class Reminder {
    private final long chatId;
    private final String title;
    private final LocalDateTime eventAt;
    private final LocalDateTime notifyAt;

    private boolean sent;

    public Reminder(long chatId, String title, LocalDateTime eventAt, LocalDateTime notifyAt) {
        this.chatId = chatId;
        this.title = title;
        this.eventAt = eventAt;
        this.notifyAt = notifyAt;
        this.sent = false;
    }

    public long getChatId() {
        return chatId;
    }

    public String getTitle() {
        return title;
    }

    public LocalDateTime getEventAt() {
        return eventAt;
    }

    public LocalDateTime getNotifyAt() {
        return notifyAt;
    }

    public boolean isSent() {
        return sent;
    }

    public void markSent() {
        this.sent = true;
    }
}
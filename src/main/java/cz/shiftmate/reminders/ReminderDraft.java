package cz.shiftmate.reminders;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public class ReminderDraft {

    public enum Step {
        NONE,
        WAIT_DATE,
        WAIT_TIME,
        WAIT_NOTIFY_OFFSET
    }

    private Step step = Step.NONE;

    private String title;
    private LocalDate date;
    private LocalTime time;
    private LocalDateTime eventAt;

    public Step getStep() {
        return step;
    }

    public void setStep(Step step) {
        this.step = step;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public LocalTime getTime() {
        return time;
    }

    public void setTime(LocalTime time) {
        this.time = time;
    }

    public LocalDateTime getEventAt() {
        return eventAt;
    }

    public void setEventAt(LocalDateTime eventAt) {
        this.eventAt = eventAt;
    }

    public void reset() {
        this.step = Step.NONE;
        this.title = null;
        this.date = null;
        this.time = null;
        this.eventAt = null;
    }
}
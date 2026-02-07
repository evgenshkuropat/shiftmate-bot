package cz.shiftmate.bot;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

@Component
public class BotSender {

    private volatile TelegramClient client;

    public void setClient(TelegramClient client) {
        this.client = client;
    }

    public boolean isReady() {
        return client != null;
    }

    public void send(long chatId, String text, ReplyKeyboardMarkup keyboard) {
        if (client == null) return;
        try {
            SendMessage msg = new SendMessage(String.valueOf(chatId), text);
            msg.enableMarkdown(true);
            msg.setReplyMarkup(keyboard);
            client.execute(msg);
        } catch (TelegramApiException e) {
            System.out.println("❌ SEND ERROR chatId=" + chatId);
            e.printStackTrace();
        }
    }

    public void sendTextOnly(long chatId, String text) {
        if (client == null) return;
        try {
            SendMessage msg = new SendMessage(String.valueOf(chatId), text);
            client.execute(msg);
        } catch (TelegramApiException e) {
            System.out.println("❌ SEND TEXT ERROR chatId=" + chatId);
            e.printStackTrace();
        }
    }
}
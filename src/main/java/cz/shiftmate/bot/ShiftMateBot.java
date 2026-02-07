package cz.shiftmate.bot;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

@Component
public class ShiftMateBot implements LongPollingSingleThreadUpdateConsumer {

    private final BotRouter router;
    private final BotSender sender;

    private final String token;
    private final String username;

    private TelegramBotsLongPollingApplication app;

    public ShiftMateBot(
            BotRouter router,
            BotSender sender,
            @Value("${telegram.bot.token}") String token,
            @Value("${telegram.bot.username}") String username
    ) {
        this.router = router;
        this.sender = sender;
        this.token = token;
        this.username = username;
    }

    @PostConstruct
    public void init() throws TelegramApiException {
        this.app = new TelegramBotsLongPollingApplication();
        app.registerBot(token, this);

        TelegramClient client = new OkHttpTelegramClient(token);
        sender.setClient(client);

        System.out.println("✅ Bot started: @" + username);
    }

    @Override
    public void consume(Update update) {
        if (update.getMessage() == null || update.getMessage().getText() == null) return;

        long chatId = update.getMessage().getChatId();
        String text = update.getMessage().getText().trim();

        router.onText(chatId, text);
    }
}
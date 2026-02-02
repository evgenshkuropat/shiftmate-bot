package cz.shiftmate.weather;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;

@Service
public class WeatherService {

    private static final double LAT = 50.028;
    private static final double LON = 15.200;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();

    private final ObjectMapper om = new ObjectMapper();

    public Forecast getDaily(LocalDate start, LocalDate end) throws IOException, InterruptedException {
        // Open-Meteo обычно отдаёт daily прогноз на ограниченное число дней вперёд.
        // На всякий случай: если end слишком далеко — можно обрезать (опционально).
        // end = start.plusDays(Math.min(14, (int) ChronoUnit.DAYS.between(start, end)));

        String url = "https://api.open-meteo.com/v1/forecast"
                + "?latitude=" + LAT
                + "&longitude=" + LON
                + "&daily=temperature_2m_max,temperature_2m_min,weathercode"
                + "&timezone=Europe%2FPrague"
                + "&start_date=" + start
                + "&end_date=" + end;

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(12))
                .header("User-Agent", "ShiftMateBot/1.0")
                .GET()
                .build();

        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());

        if (resp.statusCode() != 200) {
            throw new IOException("Weather API HTTP " + resp.statusCode() + ": " + resp.body());
        }

        Forecast fc = om.readValue(resp.body(), Forecast.class);

        // Если API вернуло без daily — тоже считаем ошибкой, чтобы было видно
        if (fc == null || fc.daily == null || fc.daily.time == null || fc.daily.time.isEmpty()) {
            throw new IOException("Weather API returned empty daily: " + resp.body());
        }

        return fc;
    }
}
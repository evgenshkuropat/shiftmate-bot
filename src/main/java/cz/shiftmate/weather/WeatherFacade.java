package cz.shiftmate.weather;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Component
public class WeatherFacade {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM");

    private final WeatherService weatherService;

    public WeatherFacade(WeatherService weatherService) {
        this.weatherService = weatherService;
    }

    public String weatherOnly(int days) {
        LocalDate start = LocalDate.now();
        LocalDate end = start.plusDays(days - 1);
        return weatherBlock(start, end);
    }

    public String weatherBlock(LocalDate start, LocalDate end) {
        try {
            Forecast fc = weatherService.getDaily(start, end);
            return formatWeather(fc);
        } catch (Exception e) {
            System.out.println("❌ WEATHER ERROR ❌ start=" + start + " end=" + end);
            e.printStackTrace();
            System.out.println("❌ END WEATHER ERROR ❌");
            return "📍Kolín\n🌦 Погода: недоступна сейчас";
        }
    }

    private String formatWeather(Forecast fc) {
        if (fc == null || fc.daily == null || fc.daily.time == null || fc.daily.time.isEmpty()) {
            return "📍Kolín\n🌦 Погода: недоступна сейчас";
        }

        StringBuilder w = new StringBuilder();
        w.append("📍Kolín\n🌦 Погода:\n");

        for (int i = 0; i < fc.daily.time.size(); i++) {
            LocalDate d = LocalDate.parse(fc.daily.time.get(i));
            double tMax = fc.daily.temperatureMax.get(i);
            double tMin = fc.daily.temperatureMin.get(i);
            int code = fc.daily.weatherCode.get(i);

            w.append(d.format(DATE_FMT))
                    .append("  ")
                    .append(Math.round(tMin)).append("°/").append(Math.round(tMax)).append("°  ")
                    .append(weatherIcon(code))
                    .append("\n");
        }
        return w.toString();
    }

    private String weatherIcon(int code) {
        if (code == 0) return "☀️";
        if (code == 1 || code == 2) return "🌤";
        if (code == 3) return "☁️";
        if (code >= 45 && code <= 48) return "🌫";
        if (code >= 51 && code <= 67) return "🌧";
        if (code >= 71 && code <= 77) return "🌨";
        if (code >= 80 && code <= 82) return "🌧";
        if (code >= 85 && code <= 86) return "🌨";
        if (code >= 95) return "⛈";
        return "🌡";
    }
}
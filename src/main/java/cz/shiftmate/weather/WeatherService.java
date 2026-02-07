package cz.shiftmate.weather;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDate;

@Service
public class WeatherService {

    private final WebClient webClient;
    private final ObjectMapper mapper;

    // Kolín fixed
    private static final double LAT = 50.0286;
    private static final double LON = 15.2006;

    public WeatherService(ObjectMapper mapper) {
        this.webClient = WebClient.builder()
                .baseUrl("https://api.open-meteo.com")
                .build();
        this.mapper = mapper;
    }

    public Forecast getDaily(LocalDate start, LocalDate end) throws Exception {
        // Важно: переменная называется weather_code (не weathercode)
        String json = webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/v1/forecast")
                        .queryParam("latitude", LAT)
                        .queryParam("longitude", LON)
                        .queryParam("daily", "weather_code,temperature_2m_max,temperature_2m_min")
                        .queryParam("timezone", "Europe/Prague")
                        .queryParam("start_date", start.toString())
                        .queryParam("end_date", end.toString())
                        .build())
                .retrieve()
                .bodyToMono(String.class)
                .block();

        return mapper.readValue(json, Forecast.class);
    }
}

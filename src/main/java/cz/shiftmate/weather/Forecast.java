package cz.shiftmate.weather;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Forecast {

    public Daily daily;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Daily {
        public List<String> time;

        @JsonProperty("weather_code")
        public List<Integer> weatherCode;

        @JsonProperty("temperature_2m_max")
        public List<Double> temperatureMax;

        @JsonProperty("temperature_2m_min")
        public List<Double> temperatureMin;
    }
}
package cz.shiftmate.weather;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Forecast {

    public Daily daily;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Daily {
        public List<String> time;
        public List<Double> temperature_2m_max;
        public List<Double> temperature_2m_min;
        public List<Integer> weathercode;
    }
}

package cz.shiftmate.storage;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Component
public class AppDataStore {

    private final ObjectMapper mapper;
    private final Path filePath;

    public AppDataStore(ObjectMapper mapper) {
        this.mapper = mapper;

        String home = System.getProperty("user.home");
        Path dir = Path.of(home, ".shiftmate-bot");
        this.filePath = dir.resolve("shiftmate-data.json");

        try {
            Files.createDirectories(dir);
        } catch (Exception e) {
            System.out.println("❌ DATA DIR CREATE ERROR: " + dir);
            e.printStackTrace();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Data {
        public Map<Long, ShiftRecord> shifts = new HashMap<>();
        public Map<Long, List<ReminderRecord>> reminders = new HashMap<>();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ShiftRecord {
        public LocalDate monday;
        public String shiftType; // EARLY/NIGHT/DAY
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ReminderRecord {
        public long chatId;
        public String title;
        public LocalDateTime eventAt;
        public LocalDateTime notifyAt;
        public boolean sent;
    }

    public synchronized Data load() {
        try {
            File f = filePath.toFile();
            if (!f.exists() || f.length() == 0) return new Data();
            return mapper.readValue(f, Data.class);
        } catch (Exception e) {
            System.out.println("❌ DATA LOAD ERROR: " + filePath);
            e.printStackTrace();
            return new Data();
        }
    }

    public synchronized void save(Data data) {
        try {
            mapper.writerWithDefaultPrettyPrinter().writeValue(filePath.toFile(), data);
        } catch (Exception e) {
            System.out.println("❌ DATA SAVE ERROR: " + filePath);
            e.printStackTrace();
        }
    }

    public Path getFilePath() {
        return filePath;
    }
}
package org;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/*
Owns weather search, fetch, and rendering behavior for the weather tab/pane.
*/
public class WeatherPaneController {
    private static final String GEOCODING_URL = "https://geocoding-api.open-meteo.com/v1/search";
    private static final double DEFAULT_WEATHER_LATITUDE = 40.7128;
    private static final double DEFAULT_WEATHER_LONGITUDE = -74.0060;

    private final Settings settings;
    private final ComboBox<String> weatherUnitBox;
    private final ComboBox<String> weatherPlaceBox;
    private final TextField weatherLatitudeField;
    private final TextField weatherLongitudeField;
    private final Label weatherHeaderLabel;
    private final VBox weatherCardsBox;

    private final WeatherService weatherService;
    private final Map<String, WeatherLocation> weatherLocationLookup = new HashMap<>();

    private String currentWeatherLocationLabel = "";
    private double currentWeatherLatitude;
    private double currentWeatherLongitude;
    private List<WeatherService.ForecastEntry> currentWeatherForecast = new ArrayList<>();

    public WeatherPaneController(
        Settings settings,
        ComboBox<String> weatherUnitBox,
        ComboBox<String> weatherPlaceBox,
        TextField weatherLatitudeField,
        TextField weatherLongitudeField,
        Label weatherHeaderLabel,
        VBox weatherCardsBox
    ) {
        this.settings = settings;
        this.weatherUnitBox = weatherUnitBox;
        this.weatherPlaceBox = weatherPlaceBox;
        this.weatherLatitudeField = weatherLatitudeField;
        this.weatherLongitudeField = weatherLongitudeField;
        this.weatherHeaderLabel = weatherHeaderLabel;
        this.weatherCardsBox = weatherCardsBox;

        this.weatherService = new WeatherService(
            parseDoubleOrDefault(weatherLatitudeField.getText(), DEFAULT_WEATHER_LATITUDE),
            parseDoubleOrDefault(weatherLongitudeField.getText(), DEFAULT_WEATHER_LONGITUDE)
        );
    }

    public void fetchWeatherForecast() {
        String placeQuery = weatherPlaceBox.getEditor().getText();
        double fallbackLatitude = parseDoubleOrDefault(weatherLatitudeField.getText(), DEFAULT_WEATHER_LATITUDE);
        double fallbackLongitude = parseDoubleOrDefault(weatherLongitudeField.getText(), DEFAULT_WEATHER_LONGITUDE);

        setWeatherStatus("Loading forecast...");

        Task<WeatherForecastResult> weatherTask = new Task<>() {
            @Override
            protected WeatherForecastResult call() {
                WeatherLocation location = resolveWeatherLocation(placeQuery, fallbackLatitude, fallbackLongitude);
                weatherService.setLocation(location.latitude, location.longitude);
                weatherService.getWeather();
                return new WeatherForecastResult(location, weatherService.getHourlyForecast());
            }
        };

        weatherTask.setOnSucceeded(e -> {
            WeatherForecastResult result = weatherTask.getValue();
            weatherLatitudeField.setText(String.format(Locale.US, "%.4f", result.location.latitude));
            weatherLongitudeField.setText(String.format(Locale.US, "%.4f", result.location.longitude));
            weatherPlaceBox.getEditor().setText(result.location.label);
            if (!weatherPlaceBox.getItems().contains(result.location.label)) {
                weatherPlaceBox.getItems().add(0, result.location.label);
            }
            weatherLocationLookup.put(result.location.label, result.location);
            currentWeatherLocationLabel = result.location.label;
            currentWeatherLatitude = result.location.latitude;
            currentWeatherLongitude = result.location.longitude;
            currentWeatherForecast = new ArrayList<>(result.forecast);
            settings.changeWeatherLocation(result.location.label, result.location.latitude, result.location.longitude);
            renderWeatherForecast(result.location.label, result.location.latitude, result.location.longitude, result.forecast);
        });

        weatherTask.setOnFailed(e -> setWeatherStatus("Unable to load forecast: " + weatherTask.getException().getMessage()));

        Thread weatherThread = new Thread(weatherTask, "weather-fetch");
        weatherThread.setDaemon(true);
        weatherThread.start();
    }

    public void fetchPlaceSuggestions() {
        String query = weatherPlaceBox.getEditor().getText();
        if (query == null || query.isBlank()) {
            setWeatherStatus("Enter a city or place name first.");
            return;
        }

        setWeatherStatus("Looking up places...");

        Task<List<WeatherLocation>> suggestionTask = new Task<>() {
            @Override
            protected List<WeatherLocation> call() {
                return geocodePlaceSuggestions(query.trim(), 5);
            }
        };

        suggestionTask.setOnSucceeded(e -> {
            List<WeatherLocation> suggestions = suggestionTask.getValue();
            weatherLocationLookup.clear();
            weatherPlaceBox.getItems().clear();

            for (WeatherLocation suggestion : suggestions) {
                weatherLocationLookup.put(suggestion.label, suggestion);
                weatherPlaceBox.getItems().add(suggestion.label);
            }

            if (suggestions.isEmpty()) {
                setWeatherStatus("No matching places found. You can still use latitude and longitude.");
                return;
            }

            weatherPlaceBox.getSelectionModel().select(0);
            applySelectedWeatherPlace();
            weatherPlaceBox.show();
            setWeatherStatus("Choose a place from the dropdown, then click Fetch Weather.");
        });

        suggestionTask.setOnFailed(e -> setWeatherStatus("Place lookup failed: " + suggestionTask.getException().getMessage()));

        Thread suggestionThread = new Thread(suggestionTask, "place-suggestions");
        suggestionThread.setDaemon(true);
        suggestionThread.start();
    }

    public void applySelectedWeatherPlace() {
        String selected = weatherPlaceBox.getSelectionModel().getSelectedItem();
        if (selected == null || selected.isBlank()) {
            selected = weatherPlaceBox.getEditor().getText();
        }

        WeatherLocation location = weatherLocationLookup.get(selected);
        if (location == null) {
            return;
        }

        weatherLatitudeField.setText(String.format(Locale.US, "%.4f", location.latitude));
        weatherLongitudeField.setText(String.format(Locale.US, "%.4f", location.longitude));
        weatherPlaceBox.getEditor().setText(location.label);
        settings.changeWeatherLocation(location.label, location.latitude, location.longitude);
    }

    public void rerenderCurrentWeatherForecast() {
        if (currentWeatherForecast == null || currentWeatherForecast.isEmpty()) {
            return;
        }
        renderWeatherForecast(currentWeatherLocationLabel, currentWeatherLatitude, currentWeatherLongitude, currentWeatherForecast);
    }

    public void clearCurrentWeatherState() {
        currentWeatherLocationLabel = "";
        currentWeatherForecast = new ArrayList<>();
    }

    public void setWeatherStatus(String message) {
        weatherHeaderLabel.setText("Weather (" + weatherUnitSuffix() + ")");
        weatherCardsBox.getChildren().setAll(buildWeatherStatusCard(message));
    }

    public void populateSavedWeatherLocation(String label, double latitude, double longitude) {
        // Pre-populate the location lookup with saved coordinates so fetchWeatherForecast doesn't need to re-geocode
        weatherLocationLookup.put(label, new WeatherLocation(latitude, longitude, label));
    }

    private void renderWeatherForecast(String locationLabel, double latitude, double longitude, List<WeatherService.ForecastEntry> forecast) {
        weatherHeaderLabel.setText("Weather (" + weatherUnitSuffix() + ") - " + locationLabel);

        if (forecast == null || forecast.isEmpty()) {
            setWeatherStatus("No forecast data returned.");
            return;
        }

        List<WeatherService.ForecastEntry> todayForecast = forecastForDate(forecast, LocalDate.now());
        List<WeatherService.ForecastEntry> entriesToShow = todayForecast.isEmpty() ? forecast : todayForecast;

        weatherCardsBox.getChildren().clear();

        Label meta = new Label("Coords: " + String.format(Locale.US, "%.4f", latitude) + ", " + String.format(Locale.US, "%.4f", longitude)
            + "  |  " + (todayForecast.isEmpty() ? "Upcoming" : "Today") + " forecast"
            + "  |  Unit: " + (isFahrenheitWeatherUnit() ? "°F" : "°C"));
        meta.setStyle(settings.isDarkTheme()
            ? "-fx-text-fill: #cbd5e1; -fx-font-size: 11px;"
            : "-fx-text-fill: #475569; -fx-font-size: 11px;");
        weatherCardsBox.getChildren().add(meta);
        weatherCardsBox.getChildren().add(buildWeatherLegend());

        int limit = Math.min(8, entriesToShow.size());
        for (int i = 0; i < limit; i++) {
            WeatherService.ForecastEntry entry = entriesToShow.get(i);
            weatherCardsBox.getChildren().add(buildWeatherCard(entry));
        }

        if (entriesToShow.size() > limit) {
            Label more = new Label("Showing first " + limit + " hours.");
            more.setStyle(settings.isDarkTheme()
                ? "-fx-text-fill: #94a3b8; -fx-font-size: 11px;"
                : "-fx-text-fill: #64748b; -fx-font-size: 11px;");
            weatherCardsBox.getChildren().add(more);
        }
    }

    private HBox buildWeatherLegend() {
        HBox legend = new HBox(8,
            buildLegendItem("☀ Clear", "#fff8db", "#f2d37a"),
            buildLegendItem("☁ Cloud/Fog", "#eef2f7", "#c7d2e0"),
            buildLegendItem("🌧 Rain", "#e6f2ff", "#8ec0f4"),
            buildLegendItem("❄ Snow", "#edf5ff", "#bcd7f5"),
            buildLegendItem("⛈ Storm", "#fff1d6", "#f4b860")
        );
        legend.setAlignment(Pos.CENTER_LEFT);
        return legend;
    }

    private Label buildLegendItem(String text, String bgColor, String borderColor) {
        String textColor = settings.isDarkTheme() ? "#e2e8f0" : "#334155";
        Label chip = new Label(text);
        chip.setStyle("-fx-font-size: 10px; -fx-padding: 3 8 3 8; -fx-background-color: " + bgColor
            + "; -fx-border-color: " + borderColor
            + "; -fx-border-radius: 999; -fx-background-radius: 999; -fx-text-fill: " + textColor + ";");
        return chip;
    }

    private HBox buildWeatherStatusCard(String message) {
        Label icon = new Label("i");
        icon.setStyle(settings.isDarkTheme()
            ? "-fx-font-size: 16px; -fx-text-fill: #e2e8f0;"
            : "-fx-font-size: 16px; -fx-text-fill: #0f172a;");
        Label text = new Label(message);
        text.setWrapText(true);
        text.setStyle(settings.isDarkTheme()
            ? "-fx-text-fill: #cbd5e1;"
            : "-fx-text-fill: #334155;");

        HBox card = new HBox(10, icon, text);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setPadding(new Insets(10));
        card.setStyle(settings.isDarkTheme()
            ? "-fx-background-color: #0f172a; -fx-border-color: #334155; -fx-border-radius: 6; -fx-background-radius: 6;"
            : "-fx-background-color: #f8fafc; -fx-border-color: #e2e8f0; -fx-border-radius: 6; -fx-background-radius: 6;");
        HBox.setHgrow(text, Priority.ALWAYS);
        return card;
    }

    private HBox buildWeatherCard(WeatherService.ForecastEntry entry) {
        String condition = shortWeatherLabel(entry.getWeatherCode());
        Label icon = new Label(weatherIcon(entry.getWeatherCode()));
        icon.setStyle("-fx-font-size: 20px; -fx-text-fill: " + weatherAccentColor(entry.getWeatherCode()) + ";");

        Label time = new Label(entry.getTime().toLocalTime().format(activeEventTimeFormatter()));
        time.setStyle(settings.isDarkTheme()
            ? "-fx-font-weight: bold; -fx-text-fill: #f8fafc;"
            : "-fx-font-weight: bold; -fx-text-fill: #0f172a;");

        Label details = new Label(condition + "  |  "
            + formatWeatherTemperature(entry)
            + "  |  rain " + entry.getPrecipitationChance() + "%");
        details.setStyle(settings.isDarkTheme()
            ? "-fx-text-fill: #cbd5e1;"
            : "-fx-text-fill: #334155;");

        VBox textCol = new VBox(3, time, details);
        HBox.setHgrow(textCol, Priority.ALWAYS);

        HBox card = new HBox(10, icon, textCol);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setPadding(new Insets(9));
        card.setStyle(weatherCardStyle(entry.getWeatherCode()));
        return card;
    }

    private String formatWeatherTemperature(WeatherService.ForecastEntry entry) {
        double value = entry.getTemperatureCelsius();
        String suffix = "°C";

        if (isFahrenheitWeatherUnit()) {
            value = entry.getTemperatureFahrenheit();
            suffix = "°F";
        }

        return String.format(Locale.US, "%.1f%s", value, suffix);
    }

    private boolean isFahrenheitWeatherUnit() {
        return weatherUnitBox != null && "Fahrenheit (°F)".equals(weatherUnitBox.getValue());
    }

    private String weatherUnitSuffix() {
        return isFahrenheitWeatherUnit() ? "°F" : "°C";
    }

    private String weatherCardStyle(int weatherCode) {
        String colors;
        if (settings.isDarkTheme()) {
            colors = switch (weatherCode) {
                case 0, 1, 2 -> "#3a2f14|#8b6b1a";
                case 3, 45, 48 -> "#252a34|#4b5563";
                case 51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 -> "#162a2d|#0f766e";
                case 71, 73, 75, 77, 85, 86 -> "#1f2937|#64748b";
                case 95, 96, 99 -> "#3b2512|#d97706";
                default -> "#0f172a|#334155";
            };
        } else {
            colors = switch (weatherCode) {
                case 0, 1, 2 -> "#fff8db|#f2d37a";
                case 3, 45, 48 -> "#eef2f7|#c7d2e0";
                case 51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 -> "#e6f2ff|#8ec0f4";
                case 71, 73, 75, 77, 85, 86 -> "#edf5ff|#bcd7f5";
                case 95, 96, 99 -> "#fff1d6|#f4b860";
                default -> "#f8fafc|#dbe3ee";
            };
        }

        String[] parts = colors.split("\\|");
        return "-fx-background-color: " + parts[0]
            + "; -fx-border-color: " + parts[1]
            + "; -fx-border-radius: 6; -fx-background-radius: 6;";
    }

    private String weatherAccentColor(int weatherCode) {
        if (settings.isDarkTheme()) {
            return switch (weatherCode) {
                case 0, 1, 2 -> "#fcd34d";
                case 3, 45, 48 -> "#cbd5e1";
                case 51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 -> "#5eead4";
                case 71, 73, 75, 77, 85, 86 -> "#cbd5e1";
                case 95, 96, 99 -> "#fb923c";
                default -> "#cbd5e1";
            };
        }

        return switch (weatherCode) {
            case 0, 1, 2 -> "#b45309";
            case 3, 45, 48 -> "#475569";
            case 51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 -> "#1d4ed8";
            case 71, 73, 75, 77, 85, 86 -> "#0369a1";
            case 95, 96, 99 -> "#b45309";
            default -> "#334155";
        };
    }

    private String weatherIcon(int weatherCode) {
        return switch (weatherCode) {
            case 0 -> "☀";
            case 1, 2 -> "🌤";
            case 3 -> "☁";
            case 45, 48 -> "🌫";
            case 51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 -> "🌧";
            case 71, 73, 75, 77, 85, 86 -> "❄";
            case 95, 96, 99 -> "⛈";
            default -> "•";
        };
    }

    private WeatherLocation resolveWeatherLocation(String placeQuery, double fallbackLatitude, double fallbackLongitude) {
        if (placeQuery == null || placeQuery.isBlank()) {
            return new WeatherLocation(fallbackLatitude, fallbackLongitude, "Custom coordinates");
        }

        WeatherLocation cached = weatherLocationLookup.get(placeQuery.trim());
        if (cached != null) {
            return cached;
        }

        return geocodePlace(placeQuery.trim());
    }

    private WeatherLocation geocodePlace(String placeQuery) {
        List<WeatherLocation> results = geocodePlaceSuggestions(placeQuery, 1);
        if (results.isEmpty()) {
            throw new IllegalStateException("No matching place found for: " + placeQuery);
        }
        return results.get(0);
    }

    private List<WeatherLocation> geocodePlaceSuggestions(String placeQuery, int count) {
        try {
            String query = "name=" + URLEncoder.encode(placeQuery, StandardCharsets.UTF_8)
                + "&count=" + count + "&language=en&format=json";

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(GEOCODING_URL + "?" + query))
                .GET()
                .build();

            HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("Location lookup failed with status code: " + response.statusCode());
            }

            return parseGeocodingResults(response.body());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Location lookup interrupted.", ex);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to look up location.", ex);
        }
    }

    private List<WeatherLocation> parseGeocodingResults(String json) {
        List<WeatherLocation> suggestions = new ArrayList<>();
        int index = 0;

        while (true) {
            int nameIndex = json.indexOf("\"name\":\"", index);
            if (nameIndex < 0) {
                break;
            }

            int objectStart = json.lastIndexOf('{', nameIndex);
            int objectEnd = json.indexOf('}', nameIndex);
            if (objectStart < 0 || objectEnd < 0 || objectEnd <= objectStart) {
                break;
            }

            String objectJson = json.substring(objectStart, objectEnd + 1);
            String name = extractFirstString(objectJson, "name");
            String state = extractFirstString(objectJson, "admin1");
            String country = extractFirstString(objectJson, "country");
            double latitude = extractFirstDouble(objectJson, "latitude");
            double longitude = extractFirstDouble(objectJson, "longitude");
            String label = buildLocationLabel(name, state, country);
            suggestions.add(new WeatherLocation(latitude, longitude, label));

            index = objectEnd + 1;
        }

        return suggestions;
    }

    private String buildLocationLabel(String name, String state, String country) {
        String cleanedName = name == null ? "" : name.trim();
        String cleanedState = state == null ? "" : state.trim();
        String cleanedCountry = country == null ? "" : country.trim();

        StringBuilder label = new StringBuilder(cleanedName);
        if (!cleanedState.isBlank() && !cleanedState.equalsIgnoreCase(cleanedName)) {
            label.append(", ").append(cleanedState);
        }
        if (!cleanedCountry.isBlank() && !cleanedCountry.equalsIgnoreCase(cleanedState)) {
            label.append(", ").append(cleanedCountry);
        }

        if (label.isEmpty()) {
            return "Unknown location";
        }

        return label.toString();
    }

    private String extractFirstString(String json, String key) {
        Matcher matcher = Pattern.compile("\\\"" + Pattern.quote(key) + "\\\"\\s*:\\s*\\\"(.*?)\\\"").matcher(json);
        if (!matcher.find()) {
            return "";
        }
        return matcher.group(1);
    }

    private double extractFirstDouble(String json, String key) {
        Matcher matcher = Pattern.compile("\\\"" + Pattern.quote(key) + "\\\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)").matcher(json);
        if (!matcher.find()) {
            throw new IllegalStateException("Location not found: " + key + " is missing.");
        }
        return Double.parseDouble(matcher.group(1));
    }

    private List<WeatherService.ForecastEntry> forecastForDate(List<WeatherService.ForecastEntry> forecast, LocalDate date) {
        List<WeatherService.ForecastEntry> filtered = new ArrayList<>();

        for (WeatherService.ForecastEntry entry : forecast) {
            if (entry.getTime().toLocalDate().equals(date)) {
                filtered.add(entry);
            }
        }

        return filtered;
    }

    private String shortWeatherLabel(int weatherCode) {
        return switch (weatherCode) {
            case 0 -> "Clear";
            case 1, 2 -> "Mostly clear";
            case 3 -> "Cloudy";
            case 45, 48 -> "Fog";
            case 51, 53, 55 -> "Drizzle";
            case 56, 57 -> "Freezing drizzle";
            case 61, 63, 65 -> "Rain";
            case 66, 67 -> "Freezing rain";
            case 71, 73, 75 -> "Snow";
            case 77 -> "Snow grains";
            case 80, 81, 82 -> "Rain showers";
            case 85, 86 -> "Snow showers";
            case 95 -> "Thunderstorm";
            case 96, 99 -> "Thunderstorm + hail";
            default -> "Weather code " + weatherCode;
        };
    }

    private DateTimeFormatter activeEventTimeFormatter() {
        if (Settings.TIME_FORMAT_24.equals(settings.getTimeFormat())) {
            return DateTimeFormatter.ofPattern("HH:mm");
        }
        return DateTimeFormatter.ofPattern("h:mm a");
    }

    private double parseDoubleOrDefault(String raw, double fallback) {
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static class WeatherLocation {
        private final double latitude;
        private final double longitude;
        private final String label;

        private WeatherLocation(double latitude, double longitude, String label) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.label = label;
        }
    }

    private static class WeatherForecastResult {
        private final WeatherLocation location;
        private final List<WeatherService.ForecastEntry> forecast;

        private WeatherForecastResult(WeatherLocation location, List<WeatherService.ForecastEntry> forecast) {
            this.location = location;
            this.forecast = forecast;
        }
    }
}

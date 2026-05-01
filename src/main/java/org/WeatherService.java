package org;/*
Interfaces with weather API and stores forecast information for display.
 */

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WeatherService {

    // Open meteo 
    private static final String WEATHER_BASE_URL = "https://api.open-meteo.com/v1/forecast";

    private double longitude;
    private double latitude;

    // Stores temperatures for quick graphing/display use.
    private double[] forecastData;
    private List<ForecastEntry> hourlyForecast;

    public WeatherService(double latitude, double longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.forecastData = new double[0];
        this.hourlyForecast = new ArrayList<>();
    }

    // Lets the app reuse one org.WeatherService object while changing the selected location.
    public void setLocation(double latitude, double longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public double getLatitude() {
        return latitude;
    }

    /*
    Fetches a 3-day hourly forecast and stores parsed results in memory.
    After calling this, use getForecastData() or getHourlyForecast().
     */
    public void getWeather() {
        try {
            // Request temperature, rain probability, and weather code for each hour.
            String query = "latitude=" + latitude
                    + "&longitude=" + longitude
                    + "&hourly=" + encode("temperature_2m,precipitation_probability,weather_code")
                    + "&forecast_days=3"
                    + "&timezone=auto";

            URI weatherUri = URI.create(WEATHER_BASE_URL + "?" + query);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(weatherUri)
                    .GET()
                    .build();

            HttpResponse<String> response = HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new IllegalStateException("Weather API request failed with status code: " + response.statusCode());
            }

            parseForecastResponse(response.body());
        } catch (InterruptedException e) {
            // Restore interrupt status so higher-level code can react correctly.
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Unable to fetch weather data.", e);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to fetch weather data.", e);
        }
    }

    // Returns a copy so callers cannot mutate org.WeatherService internal state.
    public double[] getForecastData() {
        return forecastData.clone();
    }

    // Convenience helper for screens that prefer Fahrenheit.
    public double[] getForecastDataFahrenheit() {
        double[] fahrenheitData = new double[forecastData.length];

        for (int i = 0; i < forecastData.length; i++) {
            fahrenheitData[i] = celsiusToFahrenheit(forecastData[i]);
        }

        return fahrenheitData;
    }

    // Read-only list of fully structured forecast rows.
    public List<ForecastEntry> getHourlyForecast() {
        return Collections.unmodifiableList(hourlyForecast);
    }

    private void parseForecastResponse(String json) {
        // Pull each needed field from JSON and align by index.
        List<String> times = extractStringArray(json, "time");
        List<Double> temperatures = extractDoubleArray(json, "temperature_2m");
        List<Integer> rainProbability = extractIntArray(json, "precipitation_probability");
        List<Integer> weatherCodes = extractIntArray(json, "weather_code");

        // If one list is shorter, only consume the safe shared length.
        int entries = minSize(times.size(), temperatures.size(), rainProbability.size(), weatherCodes.size());
        this.forecastData = new double[entries];
        this.hourlyForecast = new ArrayList<>(entries);

        for (int i = 0; i < entries; i++) {
            double temp = temperatures.get(i);
            this.forecastData[i] = temp;

            ForecastEntry entry = new ForecastEntry(
                    LocalDateTime.parse(times.get(i)),
                    temp,
                    rainProbability.get(i),
                    weatherCodes.get(i)
            );

            this.hourlyForecast.add(entry);
        }
    }

    // Extracts numeric arrays such as "temperature_2m".
    private List<Double> extractDoubleArray(String json, String key) {
        List<String> rawValues = extractRawArrayValues(json, key);
        List<Double> values = new ArrayList<>(rawValues.size());

        for (String raw : rawValues) {
            values.add(Double.parseDouble(raw));
        }

        return values;
    }

    // Extracts integer-like arrays; doubles are rounded just in case API returns decimals.
    private List<Integer> extractIntArray(String json, String key) {
        List<String> rawValues = extractRawArrayValues(json, key);
        List<Integer> values = new ArrayList<>(rawValues.size());

        for (String raw : rawValues) {
            values.add((int) Math.round(Double.parseDouble(raw)));
        }

        return values;
    }

    // Extracts string arrays such as timestamps.
    private List<String> extractStringArray(String json, String key) {
        List<String> rawValues = extractRawArrayValues(json, key);
        List<String> values = new ArrayList<>(rawValues.size());

        for (String raw : rawValues) {
            values.add(trimQuotes(raw));
        }

        return values;
    }

    private List<String> extractRawArrayValues(String json, String key) {
        // Finds "key": [ ... ] and captures the array body.
        Pattern pattern = Pattern.compile("\\\"" + Pattern.quote(key) + "\\\"\\s*:\\s*\\[(.*?)\\]", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(json);

        if (!matcher.find()) {
            throw new IllegalStateException("Missing weather field in API response: " + key);
        }

        String arraySection = matcher.group(1).trim();
        if (arraySection.isEmpty()) {
            return new ArrayList<>();
        }

        List<String> values = new ArrayList<>();
        StringBuilder token = new StringBuilder();
        boolean inQuotes = false;

        // Split on commas that are not inside quoted text.
        for (int i = 0; i < arraySection.length(); i++) {
            char current = arraySection.charAt(i);

            if (current == '"') {
                inQuotes = !inQuotes;
                token.append(current);
            } else if (current == ',' && !inQuotes) {
                values.add(token.toString().trim());
                token.setLength(0);
            } else {
                token.append(current);
            }
        }

        if (token.length() > 0) {
            values.add(token.toString().trim());
        }

        return values;
    }

    private String trimQuotes(String value) {
        if (value.length() >= 2 && value.charAt(0) == '"' && value.charAt(value.length() - 1) == '"') {
            return value.substring(1, value.length() - 1);
        }

        return value;
    }

    private int minSize(int first, int second, int third, int fourth) {
        return Math.min(Math.min(first, second), Math.min(third, fourth));
    }

    private double celsiusToFahrenheit(double celsius) {
        return (celsius * 9.0 / 5.0) + 32.0;
    }

    // URL-encode comma-separated field lists and other query values safely.
    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    // One row of hourly forecast data used by UI/table/chart code.
    public static class ForecastEntry {
        private final LocalDateTime time;
        private final double temperatureCelsius;
        private final int precipitationChance;
        private final int weatherCode;

        public ForecastEntry(LocalDateTime time, double temperatureCelsius, int precipitationChance, int weatherCode) {
            this.time = time;
            this.temperatureCelsius = temperatureCelsius;
            this.precipitationChance = precipitationChance;
            this.weatherCode = weatherCode;
        }

        public LocalDateTime getTime() {
            return time;
        }

        public double getTemperatureCelsius() {
            return temperatureCelsius;
        }

        public double getTemperatureFahrenheit() {
            return (temperatureCelsius * 9.0 / 5.0) + 32.0;
        }

        public int getPrecipitationChance() {
            return precipitationChance;
        }

        public int getWeatherCode() {
            return weatherCode;
        }
    }
}

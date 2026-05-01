package org;

import java.util.prefs.Preferences;

/*
Singleton for global application settings management. Stores current settings allowing other classes to refer to them to
change their behavior. Allows the settings to be changed by other classes, usually due to user input.
 */
public class Settings {
    public static final int THEME_LIGHT = 0;
    public static final int THEME_DARK = 1;
    public static final String TIME_FORMAT_12 = "12-hour (AM/PM)";
    public static final String TIME_FORMAT_24 = "24-hour";
    public static final String SECTION_EVENT = "event";
    public static final String SECTION_REMINDER = "reminder";
    public static final String SECTION_WEATHER = "weather";
    public static final String SECTION_GOAL = "goal";
    public static final String SECTION_DISPLAY = "display";
    public static final String WEATHER_UNIT_CELSIUS = "celsius";
    public static final String WEATHER_UNIT_FAHRENHEIT = "fahrenheit";

    private static final Preferences PREFS = Preferences.userNodeForPackage(Settings.class);
    private static final String PREF_KEY_WEATHER_UNIT = "weatherUnit";
    private static final String PREF_KEY_WEATHER_LOCATION_LABEL = "weatherLocationLabel";
    private static final String PREF_KEY_WEATHER_LATITUDE = "weatherLatitude";
    private static final String PREF_KEY_WEATHER_LONGITUDE = "weatherLongitude";
    private static final Settings INSTANCE = new Settings();

    private int theme;
    private String windowSize;
    private String timeFormat;
    private boolean sideBarOpen;
    private String expandedSection;
    private double sidePanelDivider;
    private String weatherUnit;
    private String weatherLocationLabel;
    private double weatherLatitude;
    private double weatherLongitude;
    private boolean weatherLocationSaved;

    private Settings() {
        this.theme = THEME_LIGHT;
        this.windowSize = "regular";
        this.timeFormat = TIME_FORMAT_12;
        this.sideBarOpen = true;
        this.expandedSection = SECTION_EVENT;
        this.sidePanelDivider = 0.64;
        String storedWeatherUnit = PREFS.get(PREF_KEY_WEATHER_UNIT, WEATHER_UNIT_CELSIUS);
        if (WEATHER_UNIT_FAHRENHEIT.equals(storedWeatherUnit)) {
            this.weatherUnit = WEATHER_UNIT_FAHRENHEIT;
        } else {
            this.weatherUnit = WEATHER_UNIT_CELSIUS;
        }

        String storedLocationLabel = PREFS.get(PREF_KEY_WEATHER_LOCATION_LABEL, "").trim();
        double storedLatitude = PREFS.getDouble(PREF_KEY_WEATHER_LATITUDE, Double.NaN);
        double storedLongitude = PREFS.getDouble(PREF_KEY_WEATHER_LONGITUDE, Double.NaN);

        if (isValidLatitude(storedLatitude) && isValidLongitude(storedLongitude)) {
            this.weatherLocationSaved = true;
            this.weatherLatitude = storedLatitude;
            this.weatherLongitude = storedLongitude;
            this.weatherLocationLabel = storedLocationLabel.isBlank() ? "Custom coordinates" : storedLocationLabel;
        } else {
            this.weatherLocationSaved = false;
            this.weatherLatitude = 0.0;
            this.weatherLongitude = 0.0;
            this.weatherLocationLabel = "";
        }
    }

    public static Settings getInstance() {
        return INSTANCE;
    }

    public int getTheme() {
        return theme;
    }

    public boolean isDarkTheme() {
        return theme == THEME_DARK;
    }

    public String getWindowSize() {
        return windowSize;
    }

    public String getTimeFormat() {
        return timeFormat;
    }

    public boolean isSideBarOpen() {
        return sideBarOpen;
    }

    public String getExpandedSection() {
        return expandedSection;
    }

    public double getSidePanelDivider() {
        return sidePanelDivider;
    }

    public String getWeatherUnit() {
        return weatherUnit;
    }

    public boolean hasSavedWeatherLocation() {
        return weatherLocationSaved;
    }

    public String getWeatherLocationLabel() {
        return weatherLocationLabel;
    }

    public double getWeatherLatitude() {
        return weatherLatitude;
    }

    public double getWeatherLongitude() {
        return weatherLongitude;
    }

    public void changeTheme(int theme) {
        if (theme != THEME_LIGHT && theme != THEME_DARK) {
            return;
        }
        this.theme = theme;
    }

    public void changeWindowSize(String windowSize) {
        if (windowSize == null || windowSize.isBlank()) {
            return;
        }
        this.windowSize = windowSize;
    }

    public void changeTimeFormat(String timeFormat) {
        if (!TIME_FORMAT_12.equals(timeFormat) && !TIME_FORMAT_24.equals(timeFormat)) {
            return;
        }
        this.timeFormat = timeFormat;
    }

    public void changeSideBarOpen(boolean sideBarOpen) {
        this.sideBarOpen = sideBarOpen;
    }

    public void changeExpandedSection(String expandedSection) {
        if (!SECTION_EVENT.equals(expandedSection)
            && !SECTION_REMINDER.equals(expandedSection)
            && !SECTION_WEATHER.equals(expandedSection)
            && !SECTION_GOAL.equals(expandedSection)
            && !SECTION_DISPLAY.equals(expandedSection)) {
            return;
        }
        this.expandedSection = expandedSection;
    }

    public void changeSidePanelDivider(double sidePanelDivider) {
        if (sidePanelDivider < 0.2 || sidePanelDivider > 0.9) {
            return;
        }
        this.sidePanelDivider = sidePanelDivider;
    }

    public void changeWeatherUnit(String weatherUnit) {
        if (!WEATHER_UNIT_CELSIUS.equals(weatherUnit) && !WEATHER_UNIT_FAHRENHEIT.equals(weatherUnit)) {
            return;
        }
        this.weatherUnit = weatherUnit;
        PREFS.put(PREF_KEY_WEATHER_UNIT, weatherUnit);
    }

    public void changeWeatherLocation(String locationLabel, double latitude, double longitude) {
        if (!isValidLatitude(latitude) || !isValidLongitude(longitude)) {
            return;
        }

        String normalizedLabel = locationLabel == null ? "" : locationLabel.trim();
        if (normalizedLabel.isBlank()) {
            normalizedLabel = "Custom coordinates";
        }

        this.weatherLocationSaved = true;
        this.weatherLocationLabel = normalizedLabel;
        this.weatherLatitude = latitude;
        this.weatherLongitude = longitude;

        PREFS.put(PREF_KEY_WEATHER_LOCATION_LABEL, normalizedLabel);
        PREFS.putDouble(PREF_KEY_WEATHER_LATITUDE, latitude);
        PREFS.putDouble(PREF_KEY_WEATHER_LONGITUDE, longitude);
    }

    private boolean isValidLatitude(double latitude) {
        return !Double.isNaN(latitude) && latitude >= -90.0 && latitude <= 90.0;
    }

    private boolean isValidLongitude(double longitude) {
        return !Double.isNaN(longitude) && longitude >= -180.0 && longitude <= 180.0;
    }

}

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A simple CLI weather application.
 *
 * Usage:
 *   java WeatherApp "Nairobi"
 */
public class WeatherApp {
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("Usage: java WeatherApp \"City Name\"");
            return;
        }

        String city = String.join(" ", args);

        try {
            CityCoordinates coordinates = findCoordinates(city);
            CurrentWeather weather = fetchCurrentWeather(coordinates.latitude, coordinates.longitude);

            System.out.println("Weather for " + coordinates.displayName);
            System.out.println("--------------------------------");
            System.out.println("Temperature: " + weather.temperatureC + " °C");
            System.out.println("Wind Speed : " + weather.windSpeedKmh + " km/h");
            System.out.println("Condition  : " + weather.description);
            System.out.println("Observed   : " + weather.time.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        } catch (Exception e) {
            System.err.println("Failed to get weather data: " + e.getMessage());
        }
    }

    private static CityCoordinates findCoordinates(String city) throws IOException, InterruptedException {
        String encodedCity = URLEncoder.encode(city, StandardCharsets.UTF_8);
        String url = "https://geocoding-api.open-meteo.com/v1/search?name=" + encodedCity + "&count=1&language=en&format=json";

        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).GET().build();
        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("Geocoding API returned HTTP " + response.statusCode());
        }

        String body = response.body();
        Double lat = extractDouble(body, "\\\"latitude\\\":(-?\\d+(?:\\.\\d+)?)");
        Double lon = extractDouble(body, "\\\"longitude\\\":(-?\\d+(?:\\.\\d+)?)");
        String name = extractString(body, "\\\"name\\\":\\\"([^\\\"]+)\\\"");
        String country = extractString(body, "\\\"country\\\":\\\"([^\\\"]+)\\\"");

        if (lat == null || lon == null || name == null) {
            throw new IllegalArgumentException("Could not find city: " + city);
        }

        String displayName = country == null ? name : name + ", " + country;
        return new CityCoordinates(lat, lon, displayName);
    }

    private static CurrentWeather fetchCurrentWeather(double lat, double lon) throws IOException, InterruptedException {
        String url = String.format(
                "https://api.open-meteo.com/v1/forecast?latitude=%s&longitude=%s&current=temperature_2m,wind_speed_10m,weather_code",
                lat,
                lon
        );

        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).GET().build();
        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("Weather API returned HTTP " + response.statusCode());
        }

        String body = response.body();

        Double temperature = extractDouble(body, "\\\"temperature_2m\\\":(-?\\d+(?:\\.\\d+)?)");
        Double windSpeed = extractDouble(body, "\\\"wind_speed_10m\\\":(-?\\d+(?:\\.\\d+)?)");
        Integer weatherCode = extractInt(body, "\\\"weather_code\\\":(\\d+)");
        String timeString = extractString(body, "\\\"time\\\":\\\"([^\\\"]+)\\\"");

        if (temperature == null || windSpeed == null || weatherCode == null || timeString == null) {
            throw new IllegalStateException("Unexpected response format from weather API.");
        }

        return new CurrentWeather(
                temperature,
                windSpeed,
                weatherCodeToText(weatherCode),
                LocalDateTime.parse(timeString)
        );
    }

    private static Double extractDouble(String text, String regex) {
        Matcher matcher = Pattern.compile(regex).matcher(text);
        return matcher.find() ? Double.valueOf(matcher.group(1)) : null;
    }

    private static Integer extractInt(String text, String regex) {
        Matcher matcher = Pattern.compile(regex).matcher(text);
        return matcher.find() ? Integer.valueOf(matcher.group(1)) : null;
    }

    private static String extractString(String text, String regex) {
        Matcher matcher = Pattern.compile(regex).matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static String weatherCodeToText(int code) {
        return switch (code) {
            case 0 -> "Clear sky";
            case 1, 2, 3 -> "Mainly clear / partly cloudy / overcast";
            case 45, 48 -> "Fog";
            case 51, 53, 55 -> "Drizzle";
            case 61, 63, 65 -> "Rain";
            case 71, 73, 75 -> "Snow";
            case 80, 81, 82 -> "Rain showers";
            case 95 -> "Thunderstorm";
            default -> "Unknown condition (code " + code + ")";
        };
    }

    private record CityCoordinates(double latitude, double longitude, String displayName) {
    }

    private record CurrentWeather(double temperatureC, double windSpeedKmh, String description, LocalDateTime time) {
    }
}

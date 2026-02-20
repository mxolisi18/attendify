# Java Weather Application

A simple command-line weather application written in Java.

## Features

- Looks up a city name and resolves coordinates using Open-Meteo Geocoding API.
- Fetches current weather (temperature, wind speed, and weather condition).
- Prints a clear summary in the terminal.

## Requirements

- Java 17 or later
- Internet connection (for API calls)

## Compile

```bash
javac src/WeatherApp.java -d out
```

## Run

```bash
java -cp out WeatherApp "Nairobi"
```

You can replace `"Nairobi"` with any city name.

package com.tonic.bootstrap;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

public class Properties
{
    private final static String PROPS_URL = "https://raw.githubusercontent.com/runelite/launcher/master/src/main/resources/net/runelite/launcher/launcher.properties";
    private final static String GRADLE_URL = "https://raw.githubusercontent.com/runelite/launcher/master/build.gradle.kts";

    public static Map<String,String> fetch() throws IOException, InterruptedException {
        String propsContent = fetch(PROPS_URL);
        String gradleContent = fetch(GRADLE_URL);
        String version = GradleUtils.extractVersion(gradleContent);
        final String VERSION_KEY = "runelite.launcher.version";

        return Arrays.stream(propsContent.split("\\R"))
                .map(line -> line.split("=", 2))
                .filter(parts -> parts.length == 2)
                .collect(Collectors.toMap(
                        parts -> parts[0],
                        parts -> VERSION_KEY.equals(parts[0]) && version != null
                                ? version
                                : parts[1],
                        (oldVal, newVal) -> newVal
                ));
    }

    private static String fetch(String rawUrl) throws IOException, InterruptedException
    {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(rawUrl))
                .header("User-Agent", "VitaLite/1.0.0 (+https://github.com/Tonic-Box)")
                .GET()
                .build();

        HttpResponse<String> res = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build()
                .send(req, HttpResponse.BodyHandlers.ofString());

        if (res.statusCode() != 200) {
            throw new IOException("Failed to fetch raw file, HTTP " + res.statusCode());
        }
        return res.body();
    }
}

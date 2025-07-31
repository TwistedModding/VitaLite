package com.tonic.bootstrap;

import com.google.gson.Gson;
import com.tonic.bootstrap.beans.Artifact;
import com.tonic.bootstrap.beans.Bootstrap;
import com.tonic.bootstrap.beans.Diff;
import com.tonic.runelite.jvm.JvmParams;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;

import static com.tonic.Main.RUNELITE_REPOSITORY_DIR;

public class RLUpdater
{
    private static Map<String, String> properties;
    private static HttpClient httpClient;

    public static void run() throws IOException, InterruptedException, NoSuchAlgorithmException
    {
        properties = Properties.fetch();

        JvmParams.set(properties);

        httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();

        HttpRequest bootstrapReq = HttpRequest.newBuilder()
                .uri(URI.create(properties.get("runelite.bootstrap")))
                .header("User-Agent", "RuneLite/" + properties.get("runelite.launcher.version"))
                .GET()
                .build();

        HttpResponse<String> bootstrapRes = httpClient.send(bootstrapReq,
                HttpResponse.BodyHandlers.ofString());

        if (bootstrapRes.statusCode() != 200) {
            throw new IOException("Failed to fetch bootstrap JSON (status=" + bootstrapRes.statusCode() + ")");
        }

        Bootstrap bootstrap = new Gson().fromJson(bootstrapRes.body(), Bootstrap.class);
        Artifact[] artifacts = bootstrap.getArtifacts();

        if (!Files.exists(RUNELITE_REPOSITORY_DIR)) {
            Files.createDirectories(RUNELITE_REPOSITORY_DIR);
        }

        for (Artifact art : artifacts)
        {
            if (art.getDiffs() != null) {
                for (Diff diff : art.getDiffs()) {
                    Path oldFile = RUNELITE_REPOSITORY_DIR.resolve(diff.getFrom());
                    Files.deleteIfExists(oldFile);
                }
            }

            Path localFile = RUNELITE_REPOSITORY_DIR.resolve(art.getName());
            boolean needsDownload = true;

            if (Files.exists(localFile)) {
                String localHash = computeSha256(localFile);
                if (localHash.equalsIgnoreCase(art.getHash())) {
                    needsDownload = false;
                } else {
                    Files.delete(localFile);
                }
            }

            if (needsDownload) {
                System.out.println("Downloading " + art.getName());
                downloadFile(art.getPath(), localFile);

                // verify
                String downloadedHash = computeSha256(localFile);
                if (!downloadedHash.equalsIgnoreCase(art.getHash())) {
                    throw new IOException("Hash mismatch for " + art.getName()
                            + " (expected " + art.getHash()
                            + ", got " + downloadedHash + ")");
                }
            }
        }

        System.out.println("Repository is up to date!");
    }

    private static void downloadFile(String url, Path destination)
            throws IOException, InterruptedException
    {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "RuneLite/" + properties.get("runelite.launcher.version"))
                .GET()
                .build();

        HttpResponse<InputStream> res = httpClient.send(req,
                HttpResponse.BodyHandlers.ofInputStream());

        if (res.statusCode() != 200) {
            throw new IOException("Failed to download " + url
                    + " (status=" + res.statusCode() + ")");
        }

        // Stream directly to disk
        try (InputStream in = res.body();
             OutputStream out = Files.newOutputStream(destination)) {
            in.transferTo(out);
        }
    }

    private static String computeSha256(Path path)
            throws IOException, NoSuchAlgorithmException
    {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream is = Files.newInputStream(path);
             DigestInputStream dis = new DigestInputStream(is, md))
        {
            byte[] buffer = new byte[8192];
            while (dis.read(buffer) != -1) {}
        }
        byte[] digest = md.digest();
        // convert to hex
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}

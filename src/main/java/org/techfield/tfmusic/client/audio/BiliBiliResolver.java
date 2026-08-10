package org.techfield.tfmusic.client.audio;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.techfield.tfmusic.music.MusicSource;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class BiliBiliResolver {
    private static final String VIEW_API = "https://api.bilibili.com/x/web-interface/view";
    private static final String PLAY_URL_API = "https://api.bilibili.com/x/player/playurl";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64)";
    private static final String REFERER = "https://www.bilibili.com/";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20L);

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15L))
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build();

    private BiliBiliResolver() {
    }

    static List<String> resolveAudioUrls(String bvid, int requestedPage) throws IOException, InterruptedException {
        if (MusicSource.BILIBILI.validateLocator(bvid) != null) {
            throw new IOException("Invalid BiliBili BV id");
        }

        JsonObject viewData = getJson(VIEW_API + "?bvid=" + bvid).getAsJsonObject("data");
        if (viewData == null || !viewData.has("pages")) {
            throw new IOException("BiliBili view response has no pages");
        }
        JsonArray pages = viewData.getAsJsonArray("pages");
        if (pages.isEmpty()) {
            throw new IOException("BiliBili video has no pages");
        }
        int pageIndex = Math.max(1, Math.min(requestedPage, pages.size())) - 1;
        long cid = pages.get(pageIndex).getAsJsonObject().get("cid").getAsLong();

        JsonObject playData = getJson(
                PLAY_URL_API + "?bvid=" + bvid + "&cid=" + cid + "&qn=0&fnval=16"
        ).getAsJsonObject("data");
        if (playData == null) {
            throw new IOException("BiliBili play response has no data");
        }

        Set<String> urls = new LinkedHashSet<>();
        JsonObject dash = playData.has("dash") && playData.get("dash").isJsonObject()
                ? playData.getAsJsonObject("dash")
                : null;
        if (dash != null && dash.has("audio") && dash.get("audio").isJsonArray()) {
            List<JsonObject> audioStreams = new ArrayList<>();
            for (JsonElement element : dash.getAsJsonArray("audio")) {
                if (element.isJsonObject()) {
                    audioStreams.add(element.getAsJsonObject());
                }
            }
            for (JsonObject audioStream : audioStreams) {
                collectUrls(audioStream, "baseUrl", "backupUrl", urls);
            }
        }
        if (playData.has("durl") && playData.get("durl").isJsonArray()) {
            for (JsonElement element : playData.getAsJsonArray("durl")) {
                collectUrls(element.getAsJsonObject(), "url", "backup_url", urls);
            }
        }
        if (urls.isEmpty()) {
            throw new IOException("BiliBili returned no playable audio URL");
        }
        return new ArrayList<>(urls);
    }

    static HttpRequest.Builder request(String url) {
        return HttpRequest.newBuilder(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .header("User-Agent", USER_AGENT)
                .header("Referer", REFERER);
    }

    static HttpClient httpClient() {
        return HTTP_CLIENT;
    }

    private static JsonObject getJson(String url) throws IOException, InterruptedException {
        HttpResponse<String> response = HTTP_CLIENT.send(
                request(url).GET().build(),
                HttpResponse.BodyHandlers.ofString()
        );
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("BiliBili API returned HTTP " + response.statusCode());
        }
        JsonElement parsed = JsonParser.parseString(response.body());
        if (!parsed.isJsonObject()) {
            throw new IOException("BiliBili API returned invalid JSON");
        }
        return parsed.getAsJsonObject();
    }

    private static void collectUrls(
            JsonObject object,
            String primaryName,
            String backupName,
            Set<String> output
    ) {
        if (object.has(primaryName) && object.get(primaryName).isJsonPrimitive()) {
            output.add(object.get(primaryName).getAsString());
        }
        if (object.has(backupName) && object.get(backupName).isJsonArray()) {
            for (JsonElement backup : object.getAsJsonArray(backupName)) {
                if (backup.isJsonPrimitive()) {
                    output.add(backup.getAsString());
                }
            }
        }
    }
}

package org.lootgrems.bot.manager;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Iterator;
import java.util.Map;

public class HypixelAPIAccess {

    private final HttpClient httpClient;
    private final ObjectMapper mapper;

    public HypixelAPIAccess() {
        this.mapper = new ObjectMapper();
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public record SkyblockStats(
            String profileId,
            String cuteName,
            int sbLevel,
            double totalNetworth,
            double purse,
            double bank
    ) {}

    public SkyblockStats getSkyCryptStats(String ignOrUuid) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://sky.shiiyu.moe/api/v2/profile/" + ignOrUuid))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();

        HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        String body = res.body();

        if (res.statusCode() != 200 || body == null || body.trim().startsWith("<")) {
            throw new RuntimeException("SkyCrypt API returned status " + res.statusCode() + ". Profile may not exist or is blocked.");
        }

        JsonNode root = mapper.readTree(body);
        JsonNode profilesNode = root.path("profiles");

        if (profilesNode.isMissingNode() || profilesNode.isEmpty()) {
            throw new IllegalArgumentException("No SkyBlock profiles found for `" + ignOrUuid + "`.");
        }

        JsonNode activeProfile = null;
        Iterator<Map.Entry<String, JsonNode>> fields = profilesNode.fields();
        while (fields.hasNext()) {
            JsonNode candidate = fields.next().getValue();
            if (candidate.path("current").asBoolean(false)) {
                activeProfile = candidate;
                break;
            }
        }
        if (activeProfile == null) {
            activeProfile = profilesNode.elements().next();
        }

        String profileId = activeProfile.path("profile_id").asText();
        String cuteName = activeProfile.path("cute_name").asText();

        int sbLevel = activeProfile.path("data").path("skyblock_level").path("level").asInt(
                activeProfile.path("leveling").path("level").asInt(0)
        );

        JsonNode nwNode = activeProfile.path("data").path("networth");
        double totalNetworth = nwNode.path("networth").asDouble(0.0);
        double purse = nwNode.path("purse").asDouble(0.0);
        double bank = nwNode.path("bank").asDouble(0.0);

        return new SkyblockStats(profileId, cuteName, sbLevel, totalNetworth, purse, bank);
    }
}
package org.lootgrems.bot.manager;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class HypixelAPIAccess {

    private final String apiKey;
    private final HttpClient httpClient;
    private final ObjectMapper mapper;

    private static final String HYPIXEL_BASE_URL = "https://api.hypixel.net/v2";
    private static final String LOCAL_NW_URL = "http://127.0.0.1:3000/calculate";

    public HypixelAPIAccess(String apiKey) {
        this.apiKey = apiKey;
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

    public String getUuidFromUsername(String username) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://api.mojang.com/users/profiles/minecraft/" + username))
                .header("User-Agent", "LootGremlinsBot/1.0")
                .GET()
                .build();

        HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        String body = res.body();

        if (res.statusCode() != 200 || body == null || body.trim().startsWith("<")) {
            throw new IllegalArgumentException("Player username not found: " + username);
        }

        JsonNode json = mapper.readTree(body);
        return json.path("id").asText();
    }

    public JsonNode getSkyblockProfiles(String uuid) throws IOException, InterruptedException {
        if (this.apiKey == null || this.apiKey.isBlank()) {
            throw new IllegalStateException("HYPIXEL_API_KEY is missing or empty. Please set it in your .env file.");
        }

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(HYPIXEL_BASE_URL + "/skyblock/profiles?uuid=" + uuid))
                .header("API-Key", this.apiKey)
                .header("User-Agent", "LootGremlinsBot/1.0")
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        String body = res.body();

        if (body == null || body.trim().startsWith("<")) {
            throw new RuntimeException("Hypixel API returned HTML status " + res.statusCode() + ". Verify your API key.");
        }

        if (res.statusCode() != 200) {
            throw new RuntimeException("Hypixel API error (Status " + res.statusCode() + "): " + body);
        }

        JsonNode root = mapper.readTree(body);
        if (!root.path("success").asBoolean(false)) {
            throw new RuntimeException("Hypixel API returned success: false");
        }

        return root;
    }

    public JsonNode getMuseumData(String profileId) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(HYPIXEL_BASE_URL + "/skyblock/museum?profile=" + profileId))
                .header("API-Key", this.apiKey)
                .header("User-Agent", "LootGremlinsBot/1.0")
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        String body = res.body();

        if (res.statusCode() != 200 || body == null || body.trim().startsWith("<")) {
            return null;
        }

        return mapper.readTree(body);
    }

    public JsonNode calculateNetworth(JsonNode profileMember, JsonNode museumData, double bankBalance)
            throws IOException, InterruptedException {

        ObjectNode payload = mapper.createObjectNode();
        payload.set("profileData", profileMember);
        if (museumData != null && !museumData.isMissingNode() && !museumData.isNull()) {
            payload.set("museumData", museumData);
        }
        payload.put("bankBalance", bankBalance);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(LOCAL_NW_URL))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(15))
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
                .build();

        HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        String body = res.body();

        if (res.statusCode() != 200 || body == null) {
            throw new RuntimeException("Local Networth error (" + res.statusCode() + "): " + body);
        }

        return mapper.readTree(body);
    }

    public SkyblockStats getActiveProfileStats(String uuid) throws Exception {
        JsonNode profilesResponse = getSkyblockProfiles(uuid);
        JsonNode profilesArray = profilesResponse.path("profiles");

        if (!profilesArray.isArray() || profilesArray.isEmpty()) {
            throw new IllegalStateException("No SkyBlock profiles found for UUID: " + uuid);
        }

        JsonNode activeProfile = null;
        for (JsonNode profile : profilesArray) {
            if (profile.path("selected").asBoolean(false)) {
                activeProfile = profile;
                break;
            }
        }
        if (activeProfile == null) {
            activeProfile = profilesArray.get(0);
        }

        String profileId = activeProfile.path("profile_id").asText("unknown");
        String cuteName = activeProfile.path("cute_name").asText("Unknown");

        String trimmedUuid = uuid.replace("-", "");
        JsonNode memberNode = activeProfile.path("members").path(trimmedUuid);

        if (memberNode.isMissingNode() || memberNode.isNull()) {
            throw new IllegalStateException("Could not find member data for UUID: " + uuid);
        }

        double exp = memberNode.path("leveling").path("experience").asDouble(0.0);
        int sbLevel = (int) (exp / 100.0);

        double bankBalance = activeProfile.path("banking").path("balance").asDouble(0.0);
        JsonNode museumRoot = getMuseumData(profileId);
        JsonNode museumMember = null;
        if (museumRoot != null && museumRoot.path("success").asBoolean(false)) {
            museumMember = museumRoot.path("members").path(trimmedUuid);
        }

        JsonNode nwResponse = calculateNetworth(memberNode, museumRoot, bankBalance);

        double totalNetworth = nwResponse.path("networth").asDouble(0.0);
        double purse = nwResponse.path("purse").asDouble(0.0);
        double bank = nwResponse.path("bank").asDouble(0.0);

        return new SkyblockStats(profileId, cuteName, sbLevel, totalNetworth, purse, bank);
    }
}
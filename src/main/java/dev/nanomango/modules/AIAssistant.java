package dev.nanomango.modules;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.nanomango.NanoFutureAddon;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AIAssistant extends Module {
    public enum Provider {
        OpenAI,
        Anthropic,
        Gemini,
        Custom
    }

    private record Message(String role, String content) {}

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "NanoFuture-AI");
        thread.setDaemon(true);
        return thread;
    });

    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgProvider = settings.createGroup("Provider");
    private final SettingGroup sgPrompt = settings.createGroup("Prompt");

    private final Setting<Provider> provider = sgGeneral.add(new EnumSetting.Builder<Provider>()
        .name("provider")
        .description("Which provider to use.")
        .defaultValue(Provider.OpenAI)
        .build()
    );

    private final Setting<String> openaiKey = sgProvider.add(new StringSetting.Builder()
        .name("openai-key")
        .description("OpenAI API key.")
        .defaultValue("")
        .visible(() -> provider.get() == Provider.OpenAI)
        .build()
    );

    private final Setting<String> openaiModel = sgProvider.add(new StringSetting.Builder()
        .name("openai-model")
        .description("OpenAI model.")
        .defaultValue("gpt-4o-mini")
        .visible(() -> provider.get() == Provider.OpenAI)
        .build()
    );

    private final Setting<String> anthropicKey = sgProvider.add(new StringSetting.Builder()
        .name("anthropic-key")
        .description("Anthropic API key.")
        .defaultValue("")
        .visible(() -> provider.get() == Provider.Anthropic)
        .build()
    );

    private final Setting<String> anthropicModel = sgProvider.add(new StringSetting.Builder()
        .name("anthropic-model")
        .description("Anthropic model.")
        .defaultValue("claude-3-5-sonnet-latest")
        .visible(() -> provider.get() == Provider.Anthropic)
        .build()
    );

    private final Setting<String> geminiKey = sgProvider.add(new StringSetting.Builder()
        .name("gemini-key")
        .description("Google AI Studio API key.")
        .defaultValue("")
        .visible(() -> provider.get() == Provider.Gemini)
        .build()
    );

    private final Setting<String> geminiModel = sgProvider.add(new StringSetting.Builder()
        .name("gemini-model")
        .description("Gemini model.")
        .defaultValue("gemini-2.0-flash")
        .visible(() -> provider.get() == Provider.Gemini)
        .build()
    );

    private final Setting<String> customUrl = sgProvider.add(new StringSetting.Builder()
        .name("custom-url")
        .description("OpenAI-compatible chat completions URL.")
        .defaultValue("https://api.openai.com/v1/chat/completions")
        .visible(() -> provider.get() == Provider.Custom)
        .build()
    );

    private final Setting<String> customKey = sgProvider.add(new StringSetting.Builder()
        .name("custom-key")
        .description("Bearer token for the custom endpoint.")
        .defaultValue("")
        .visible(() -> provider.get() == Provider.Custom)
        .build()
    );

    private final Setting<String> customModel = sgProvider.add(new StringSetting.Builder()
        .name("custom-model")
        .description("Model name for the custom endpoint.")
        .defaultValue("gpt-4o-mini")
        .visible(() -> provider.get() == Provider.Custom)
        .build()
    );

    private final Setting<String> systemPrompt = sgPrompt.add(new StringSetting.Builder()
        .name("system-prompt")
        .description("Instructions sent with every request.")
        .defaultValue("You are a helpful Minecraft assistant. Keep answers short and plain.")
        .build()
    );

    private final Setting<Integer> maxTokens = sgPrompt.add(new IntSetting.Builder()
        .name("max-tokens")
        .description("Maximum response length.")
        .defaultValue(500)
        .range(1, 4000)
        .build()
    );

    private final Setting<Double> temperature = sgPrompt.add(new DoubleSetting.Builder()
        .name("temperature")
        .description("Response randomness.")
        .defaultValue(0.7)
        .range(0, 2)
        .build()
    );

    private final Setting<Integer> historyLimit = sgPrompt.add(new IntSetting.Builder()
        .name("history-limit")
        .description("Messages kept in context.")
        .defaultValue(20)
        .range(2, 100)
        .build()
    );

    private final List<Message> messages = new ArrayList<>();
    private boolean pending;
    private int contextVersion;

    public AIAssistant() {
        super(NanoFutureAddon.CATEGORY, "ai-assistant",
            "Chat with an AI provider using .ai. Context clears when you leave or join a world.");
    }

    @Override
    public void onActivate() {
        clearContext();
    }

    @Override
    public void onDeactivate() {
        pending = false;
        contextVersion++;
    }

    @EventHandler
    private void onJoin(GameJoinedEvent event) {
        clearContext();
    }

    @EventHandler
    private void onLeave(GameLeftEvent event) {
        clearContext();
    }

    public void ask(String prompt) {
        if (!isActive()) {
            error("Enable ai-assistant first.");
            return;
        }

        if (pending) {
            warning("Still waiting for the previous response.");
            return;
        }

        if (prompt.isBlank()) {
            warning("Usage: .ai <message>");
            return;
        }

        Provider provider = this.provider.get();
        String key = key(provider);
        String model = model(provider);
        String url = url(provider);

        if (key.isBlank()) {
            error("Set an API key for %s.", provider);
            return;
        }

        if (model.isBlank()) {
            error("Set a model for %s.", provider);
            return;
        }

        if (url.isBlank()) {
            error("Set a URL for %s.", provider);
            return;
        }

        messages.add(new Message("user", prompt));
        trimHistory();

        List<Message> history = List.copyOf(messages);
        String system = systemPrompt.get();
        int tokens = maxTokens.get();
        double temp = temperature.get();
        int version = contextVersion;
        pending = true;

        EXECUTOR.execute(() -> {
            try {
                String answer = request(provider, url, key, model, system, tokens, temp, history);

                mc.execute(() -> {
                    pending = false;
                    if (version != contextVersion) return;

                    if (answer == null || answer.isBlank()) {
                        error("The provider returned an empty response.");
                        return;
                    }

                    messages.add(new Message("assistant", answer));
                    trimHistory();
                    send(answer);
                });
            } catch (Exception e) {
                mc.execute(() -> {
                    pending = false;
                    if (version != contextVersion) return;
                    error("Request failed: %s", e.getMessage());
                });
            }
        });
    }

    public void clearContext() {
        messages.clear();
        contextVersion++;
        pending = false;
    }

    private void trimHistory() {
        while (messages.size() > historyLimit.get()) messages.remove(0);
        while (!messages.isEmpty() && messages.get(0).role().equals("assistant")) messages.remove(0);
    }

    private void send(String text) {
        String[] lines = text.split("\\R");
        int sent = 0;

        for (String line : lines) {
            if (line.isBlank()) continue;
            if (line.length() > 500) line = line.substring(0, 500) + "...";

            ChatUtils.sendMsg(Text.literal("[AI] ").formatted(Formatting.AQUA)
                .append(Text.literal(line)));

            if (++sent >= 12) {
                ChatUtils.sendMsg(Text.literal("[AI] ...").formatted(Formatting.AQUA));
                break;
            }
        }
    }

    private String request(Provider provider, String url, String key, String model,
                           String system, int tokens, double temp, List<Message> history) throws Exception {
        return switch (provider) {
            case OpenAI -> openai(url, key, model, system, tokens, temp, history);
            case Anthropic -> anthropic(url, key, model, system, tokens, temp, history);
            case Gemini -> gemini(url, key, model, system, tokens, temp, history);
            case Custom -> openai(url, key, model, system, tokens, temp, history);
        };
    }

    private String openai(String url, String key, String model, String system,
                          int tokens, double temp, List<Message> history) throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.addProperty("max_tokens", tokens);
        body.addProperty("temperature", temp);

        JsonArray list = new JsonArray();
        if (!system.isBlank()) list.add(message("system", system));
        for (Message message : history) list.add(message(message.role(), message.content()));
        body.add("messages", list);

        HttpResponse<String> response = post(url, body.toString(), "Authorization", "Bearer " + key);
        JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
        JsonArray choices = root.getAsJsonArray("choices");

        if (choices == null || choices.isEmpty()) throw new RuntimeException("No choices in response.");
        return content(choices.get(0).getAsJsonObject().getAsJsonObject("message").get("content"));
    }

    private String anthropic(String url, String key, String model, String system,
                             int tokens, double temp, List<Message> history) throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.addProperty("max_tokens", tokens);
        body.addProperty("temperature", temp);
        if (!system.isBlank()) body.addProperty("system", system);

        JsonArray list = new JsonArray();
        for (Message message : history) list.add(message(message.role(), message.content()));
        body.add("messages", list);

        HttpRequest.Builder builder = base(url)
            .header("x-api-key", key)
            .header("anthropic-version", "2023-06-01")
            .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8));

        HttpResponse<String> response = HTTP.send(builder.build(),
            HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        check(response);

        JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
        JsonArray content = root.getAsJsonArray("content");
        if (content == null) throw new RuntimeException("No content in response.");

        StringBuilder text = new StringBuilder();
        for (JsonElement element : content) {
            JsonObject block = element.getAsJsonObject();
            if (block.has("text")) text.append(block.get("text").getAsString());
        }
        return text.toString();
    }

    private String gemini(String url, String key, String model, String system,
                          int tokens, double temp, List<Message> history) throws Exception {
        JsonObject body = new JsonObject();

        if (!system.isBlank()) {
            JsonObject instruction = new JsonObject();
            JsonArray parts = new JsonArray();
            parts.add(part(system));
            instruction.add("parts", parts);
            body.add("systemInstruction", instruction);
        }

        JsonArray contents = new JsonArray();
        for (Message message : history) {
            JsonObject content = new JsonObject();
            content.addProperty("role", message.role().equals("assistant") ? "model" : "user");
            JsonArray parts = new JsonArray();
            parts.add(part(message.content()));
            content.add("parts", parts);
            contents.add(content);
        }
        body.add("contents", contents);

        JsonObject generation = new JsonObject();
        generation.addProperty("maxOutputTokens", tokens);
        generation.addProperty("temperature", temp);
        body.add("generationConfig", generation);

        String endpoint = url + "/v1beta/models/" + model + ":generateContent";
        HttpRequest.Builder builder = base(endpoint)
            .header("x-goog-api-key", key)
            .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8));

        HttpResponse<String> response = HTTP.send(builder.build(),
            HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        check(response);

        JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
        JsonArray candidates = root.getAsJsonArray("candidates");
        if (candidates == null || candidates.isEmpty()) throw new RuntimeException("No candidates in response.");

        JsonArray parts = candidates.get(0).getAsJsonObject()
            .getAsJsonObject("content").getAsJsonArray("parts");
        if (parts == null) throw new RuntimeException("No content in response.");

        StringBuilder text = new StringBuilder();
        for (JsonElement element : parts) {
            JsonObject part = element.getAsJsonObject();
            if (part.has("text")) text.append(part.get("text").getAsString());
        }
        return text.toString();
    }

    private HttpResponse<String> post(String url, String body, String header, String value) throws Exception {
        HttpRequest.Builder builder = base(url)
            .header(header, value)
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));

        HttpResponse<String> response = HTTP.send(builder.build(),
            HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        check(response);
        return response;
    }

    private HttpRequest.Builder base(String url) {
        return HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(60))
            .header("Content-Type", "application/json");
    }

    private void check(HttpResponse<String> response) {
        if (response.statusCode() >= 200 && response.statusCode() < 300) return;

        String body = response.body();
        if (body != null && body.length() > 300) body = body.substring(0, 300);
        throw new RuntimeException("HTTP " + response.statusCode() + ": " + body);
    }

    private JsonObject message(String role, String content) {
        JsonObject object = new JsonObject();
        object.addProperty("role", role);
        object.addProperty("content", content);
        return object;
    }

    private JsonObject part(String text) {
        JsonObject part = new JsonObject();
        part.addProperty("text", text);
        return part;
    }

    private String content(JsonElement element) {
        if (element == null || element.isJsonNull()) return "";
        if (element.isJsonPrimitive()) return element.getAsString();

        StringBuilder text = new StringBuilder();
        if (element.isJsonArray()) {
            for (JsonElement part : element.getAsJsonArray()) {
                if (part.isJsonObject() && part.getAsJsonObject().has("text")) {
                    text.append(part.getAsJsonObject().get("text").getAsString());
                }
            }
        }
        return text.toString();
    }

    private String key(Provider provider) {
        return switch (provider) {
            case OpenAI -> openaiKey.get();
            case Anthropic -> anthropicKey.get();
            case Gemini -> geminiKey.get();
            case Custom -> customKey.get();
        };
    }

    private String model(Provider provider) {
        return switch (provider) {
            case OpenAI -> openaiModel.get();
            case Anthropic -> anthropicModel.get();
            case Gemini -> geminiModel.get();
            case Custom -> customModel.get();
        };
    }

    private String url(Provider provider) {
        return switch (provider) {
            case OpenAI -> "https://api.openai.com/v1/chat/completions";
            case Anthropic -> "https://api.anthropic.com/v1/messages";
            case Gemini -> "https://generativelanguage.googleapis.com";
            case Custom -> customUrl.get();
        };
    }
}

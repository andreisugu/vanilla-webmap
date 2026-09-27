package xyz.chaonius.webmap;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

public class HttpServerManager {
    private final MinecraftServer server;
    private final ModConfig config;
    private final BinaryTileManager tileManager;
    private HttpServer httpServer;

    public HttpServerManager(MinecraftServer server, ModConfig config) {
        this.server = server;
        this.config = config;
        this.tileManager = new BinaryTileManager(server, config);
    }

    public BinaryTileManager getTileManager() {
        return tileManager;
    }

    public void start() {
        try {
            httpServer = HttpServer.create(new InetSocketAddress(config.bindAddress, config.httpPort), 0);
            httpServer.createContext("/api/config", this::handleConfig);
            httpServer.createContext("/api/players", this::handlePlayers);
            httpServer.createContext("/api/status", this::handleStatus);
            httpServer.createContext("/api/tile", this::handleTile);
            httpServer.createContext("/api/tiles_index", this::handleTilesIndex);
            httpServer.createContext("/api/region", this::handleRegion);
            httpServer.createContext("/api/regions_index", this::handleRegionsIndex);
            httpServer.createContext("/", this::handleStatic);

            httpServer.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
            httpServer.start();
            System.out.println("[VanillaWebMap] High-Speed Binary Tile HTTP server listening on " + config.bindAddress + ":" + config.httpPort);
        } catch (IOException e) {
            System.err.println("[VanillaWebMap-ERROR] Failed to start HTTP server: " + e.getMessage());
        }
    }

    public void stop() {
        if (httpServer != null) {
            httpServer.stop(1);
            System.out.println("[VanillaWebMap] Stopped HTTP server.");
        }
    }

    private void handleConfig(HttpExchange exchange) throws IOException {
        JsonObject json = new JsonObject();
        json.addProperty("title", config.serverTitle);
        json.addProperty("serverIp", config.serverIp);
        json.addProperty("headerUrl", config.headerLinkUrl);
        json.addProperty("showHealth", config.showPlayerHealth);
        json.addProperty("showCoordinates", config.showCoordinates);

        sendJsonResponse(exchange, json.toString(), 300);
    }

    private void handleStatus(HttpExchange exchange) throws IOException {
        JsonObject json = new JsonObject();
        json.addProperty("online", server.getPlayerCount());
        json.addProperty("max", server.getMaxPlayers());
        json.addProperty("dayTime", server.overworld() != null ? server.overworld().getOverworldClockTime() % 24000 : 0);
        json.addProperty("raining", server.overworld() != null && server.overworld().isRaining());
        json.addProperty("tps", 20.0);

        sendJsonResponse(exchange, json.toString(), 1);
    }

    private void handlePlayers(HttpExchange exchange) throws IOException {
        JsonArray array = new JsonArray();
        VanillaWebMapMod mod = VanillaWebMapMod.getInstance();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (mod != null && mod.isPlayerHidden(player.getUUID())) {
                continue;
            }
            if (config.hideSpectators && player.isSpectator()) {
                continue;
            }
            if (config.hideInvisible && player.isInvisible()) {
                continue;
            }
            JsonObject obj = new JsonObject();
            obj.addProperty("name", player.getScoreboardName());
            obj.addProperty("x", Math.round(player.getX() * 100.0) / 100.0);
            obj.addProperty("y", Math.round(player.getY() * 100.0) / 100.0);
            obj.addProperty("z", Math.round(player.getZ() * 100.0) / 100.0);
            obj.addProperty("yaw", Math.round(player.getYRot() * 100.0) / 100.0);
            obj.addProperty("dim", BinaryTileManager.normalizeDim(player.level().dimension().identifier().toString()));
            if (config.showPlayerHealth) {
                obj.addProperty("health", player.getHealth());
            }
            array.add(obj);
        }

        sendJsonResponse(exchange, array.toString(), 1);
    }

    private String getDimParam(HttpExchange exchange) {
        String query = exchange.getRequestURI().getQuery();
        if (query == null) return "overworld";
        for (String param : query.split("&")) {
            String[] pair = param.split("=");
            if (pair.length == 2 && pair[0].equals("dim")) {
                return BinaryTileManager.normalizeDim(pair[1]);
            }
        }
        return "overworld";
    }

    private void handleTilesIndex(HttpExchange exchange) throws IOException {
        String dim = getDimParam(exchange);
        sendJsonResponse(exchange, tileManager.getExploredIndexJson(dim), 2);
    }

    private void handleRegionsIndex(HttpExchange exchange) throws IOException {
        String dim = getDimParam(exchange);
        sendJsonResponse(exchange, tileManager.getExploredRegionsVersionJson(dim), 0);
    }

    private void handleTile(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getQuery();
        if (query == null) {
            exchange.sendResponseHeaders(400, -1);
            return;
        }

        int cx = 0, cz = 0;
        String dim = "overworld";
        try {
            for (String param : query.split("&")) {
                String[] pair = param.split("=");
                if (pair.length == 2) {
                    if (pair[0].equals("cx")) cx = Integer.parseInt(pair[1]);
                    else if (pair[0].equals("cz")) cz = Integer.parseInt(pair[1]);
                    else if (pair[0].equals("dim")) dim = BinaryTileManager.normalizeDim(pair[1]);
                }
            }
        } catch (Exception e) {
            exchange.sendResponseHeaders(400, -1);
            return;
        }

        byte[] tileData = tileManager.getChunkTile(dim, cx, cz);
        if (tileData != null && tileData.length == 256) {
            exchange.getResponseHeaders().set("Content-Type", "application/octet-stream");
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Cache-Control", "public, max-age=86400, immutable");
            exchange.sendResponseHeaders(200, 256);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(tileData);
            }
        } else {
            exchange.sendResponseHeaders(404, -1);
        }
    }

    private void handleRegion(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getQuery();
        if (query == null) {
            exchange.sendResponseHeaders(400, -1);
            return;
        }

        int rx = 0, rz = 0;
        boolean hasVersion = false;
        String dim = "overworld";
        try {
            for (String param : query.split("&")) {
                String[] pair = param.split("=");
                if (pair.length == 2) {
                    if (pair[0].equals("rx")) rx = Integer.parseInt(pair[1]);
                    else if (pair[0].equals("rz")) rz = Integer.parseInt(pair[1]);
                    else if (pair[0].equals("v")) hasVersion = true;
                    else if (pair[0].equals("dim")) dim = BinaryTileManager.normalizeDim(pair[1]);
                }
            }
        } catch (Exception e) {
            exchange.sendResponseHeaders(400, -1);
            return;
        }

        byte[] regionData = tileManager.getRegionTile(dim, rx, rz);
        if (regionData != null && regionData.length == 262144) {
            exchange.getResponseHeaders().set("Content-Type", "application/octet-stream");
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Cache-Control", hasVersion ? "public, max-age=31536000, immutable" : "no-cache, no-store, must-revalidate");
            exchange.sendResponseHeaders(200, 262144);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(regionData);
            }
        } else {
            exchange.sendResponseHeaders(404, -1);
        }
    }

    private void handleStatic(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if (path.equals("/") || path.isEmpty()) {
            path = "/index.html";
        }

        String resourcePath = "/assets/vanilla-webmap/web" + path;
        try (InputStream in = getClass().getResourceAsStream(resourcePath)) {
            if (in == null) {
                try (InputStream indexIn = getClass().getResourceAsStream("/assets/vanilla-webmap/web/index.html")) {
                    if (indexIn != null) {
                        byte[] data = indexIn.readAllBytes();
                        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
                        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                        exchange.getResponseHeaders().set("Cache-Control", "no-cache, no-store, must-revalidate");
                        exchange.sendResponseHeaders(200, data.length);
                        try (OutputStream os = exchange.getResponseBody()) {
                            os.write(data);
                        }
                        return;
                    }
                }
                exchange.sendResponseHeaders(404, -1);
                return;
            }

            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] temp = new byte[8192];
            int read;
            while ((read = in.read(temp)) != -1) {
                buffer.write(temp, 0, read);
            }
            byte[] data = buffer.toByteArray();

            String contentType = "text/plain";
            boolean isHtml = false;
            if (path.endsWith(".html")) { contentType = "text/html; charset=UTF-8"; isHtml = true; }
            else if (path.endsWith(".css")) contentType = "text/css; charset=UTF-8";
            else if (path.endsWith(".js")) contentType = "application/javascript; charset=UTF-8";
            else if (path.endsWith(".png")) contentType = "image/png";

            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Cache-Control", isHtml ? "no-cache, no-store, must-revalidate" : "public, max-age=86400");
            exchange.sendResponseHeaders(200, data.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(data);
            }
        }
    }

    private void sendJsonResponse(HttpExchange exchange, String json, int maxAgeSec) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        if (maxAgeSec > 0) {
            exchange.getResponseHeaders().set("Cache-Control", "public, max-age=" + maxAgeSec);
        } else {
            exchange.getResponseHeaders().set("Cache-Control", "no-cache, no-store, must-revalidate");
        }
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}

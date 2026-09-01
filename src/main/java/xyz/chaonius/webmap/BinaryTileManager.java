package xyz.chaonius.webmap;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class BinaryTileManager {
    private final MinecraftServer server;
    private final ModConfig config;
    private final File baseTilesDir;
    private final Map<String, Map<String, byte[]>> tileMemoryCache = new ConcurrentHashMap<>();
    private final Map<String, Map<String, byte[]>> regionMemoryCache = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> knownDiskTiles = new ConcurrentHashMap<>();
    private final Map<String, Map<String, AtomicInteger>> regionVersions = new ConcurrentHashMap<>();
    private final ExecutorService asyncDiskWriter = Executors.newSingleThreadExecutor();

    public static final String[] SUPPORTED_DIMS = new String[]{"overworld", "the_nether", "the_end"};

    public BinaryTileManager(MinecraftServer server, ModConfig config) {
        this.server = server;
        this.config = config;
        this.baseTilesDir = new File(config.tileCacheDir);
        if (!baseTilesDir.exists()) {
            baseTilesDir.mkdirs();
        }

        for (String dim : SUPPORTED_DIMS) {
            tileMemoryCache.put(dim, new ConcurrentHashMap<>());
            regionMemoryCache.put(dim, new ConcurrentHashMap<>());
            knownDiskTiles.put(dim, ConcurrentHashMap.newKeySet());
            regionVersions.put(dim, new ConcurrentHashMap<>());

            File dimDir = new File(baseTilesDir, dim);
            if (!dimDir.exists()) dimDir.mkdirs();
        }

        migrateAndLoadExistingTiles();
    }

    public void stop() {
        asyncDiskWriter.shutdown();
    }

    public static String normalizeDim(String raw) {
        if (raw == null || raw.isEmpty()) return "overworld";
        String lower = raw.toLowerCase();
        if (lower.contains("nether")) return "the_nether";
        if (lower.contains("end")) return "the_end";
        if (lower.contains("overworld")) return "overworld";
        return lower.replace("minecraft:", "").replace(":", "_");
    }

    private void migrateAndLoadExistingTiles() {
        try {
            // 1. Migrate legacy root tiles into "overworld" directory
            File[] rootFiles = baseTilesDir.listFiles((dir, name) -> name.endsWith(".vmap"));
            if (rootFiles != null && rootFiles.length > 0) {
                File overworldDir = new File(baseTilesDir, "overworld");
                overworldDir.mkdirs();
                for (File f : rootFiles) {
                    File dest = new File(overworldDir, f.getName());
                    Files.move(f.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
                System.out.println("[VanillaWebMap] Migrated " + rootFiles.length + " legacy tiles into overworld cache.");
            }

            // 2. Load disk tiles for all dimensions
            int totalTiles = 0;
            for (String dim : SUPPORTED_DIMS) {
                File dimDir = new File(baseTilesDir, dim);
                File[] files = dimDir.listFiles((dir, name) -> name.endsWith(".vmap"));
                if (files != null) {
                    Map<String, byte[]> tileCache = tileMemoryCache.get(dim);
                    Set<String> diskTiles = knownDiskTiles.get(dim);
                    Map<String, AtomicInteger> versions = regionVersions.get(dim);

                    for (File f : files) {
                        if (f.length() == 256) {
                            String key = f.getName().replace(".vmap", "");
                            diskTiles.add(key);
                            String[] parts = key.split("_");
                            if (parts.length == 2) {
                                int cx = Integer.parseInt(parts[0]);
                                int cz = Integer.parseInt(parts[1]);
                                int rx = cx >> 5;
                                int rz = cz >> 5;
                                String regKey = rx + "_" + rz;
                                versions.computeIfAbsent(regKey, k -> new AtomicInteger()).incrementAndGet();

                                try (FileInputStream fis = new FileInputStream(f)) {
                                    byte[] chunkData = fis.readAllBytes();
                                    if (chunkData.length == 256) {
                                        tileCache.put(key, chunkData);
                                        updateRegionBuffer(dim, rx, rz, cx, cz, chunkData);
                                    }
                                } catch (Exception ignored) {}
                            }
                            totalTiles++;
                        } else {
                            f.delete();
                        }
                    }
                }
            }
            System.out.println("[VanillaWebMap] Initialized with " + totalTiles + " multi-dimension .vmap tiles across " + SUPPORTED_DIMS.length + " dimensions.");
        } catch (Exception e) {
            System.err.println("[VanillaWebMap-ERROR] Error loading existing multi-dimension tiles: " + e.getMessage());
        }
    }

    private void updateRegionBuffer(String dim, int rx, int rz, int cx, int cz, byte[] chunkData) {
        Map<String, byte[]> regionCache = regionMemoryCache.computeIfAbsent(dim, k -> new ConcurrentHashMap<>());
        String regKey = rx + "_" + rz;
        byte[] regionBuf = regionCache.computeIfAbsent(regKey, k -> new byte[262144]);

        int lcx = (cx % 32 + 32) % 32;
        int lcz = (cz % 32 + 32) % 32;

        synchronized (regionBuf) {
            for (int lz = 0; lz < 16; lz++) {
                int regionZ = lcz * 16 + lz;
                int regionOffset = regionZ * 512 + (lcx * 16);
                int chunkOffset = lz * 16;
                System.arraycopy(chunkData, chunkOffset, regionBuf, regionOffset, 16);
            }
        }
    }

    public int getDiskTileCount(String dim) {
        Set<String> set = knownDiskTiles.get(normalizeDim(dim));
        return set != null ? set.size() : 0;
    }

    public int getTotalDiskTileCount() {
        int count = 0;
        for (Set<String> s : knownDiskTiles.values()) {
            count += s.size();
        }
        return count;
    }

    public int getRamTileCount(String dim) {
        Map<String, byte[]> m = tileMemoryCache.get(normalizeDim(dim));
        return m != null ? m.size() : 0;
    }

    public void clearCache() {
        for (String dim : SUPPORTED_DIMS) {
            Map<String, byte[]> tc = tileMemoryCache.get(dim);
            if (tc != null) tc.clear();
            Map<String, byte[]> rc = regionMemoryCache.get(dim);
            if (rc != null) rc.clear();
            Set<String> kd = knownDiskTiles.get(dim);
            if (kd != null) kd.clear();
            Map<String, AtomicInteger> rv = regionVersions.get(dim);
            if (rv != null) rv.clear();

            File dimDir = new File(baseTilesDir, dim);
            File[] files = dimDir.listFiles((dir, name) -> name.endsWith(".vmap"));
            if (files != null) {
                for (File f : files) f.delete();
            }
        }
        System.out.println("[VanillaWebMap] Cleared all multi-dimension cached tiles.");
    }

    public void onChunkLoad(ServerLevel level, LevelChunk chunk) {
        if (chunk == null || level == null) return;
        String dim = normalizeDim(level.dimension().identifier().toString());
        int cx = chunk.getPos().x();
        int cz = chunk.getPos().z();
        String key = cx + "_" + cz;

        Set<String> diskTiles = knownDiskTiles.computeIfAbsent(dim, k -> ConcurrentHashMap.newKeySet());
        if (diskTiles.contains(key)) {
            return;
        }

        byte[] data = renderChunkToBytes(level, chunk, cx, cz, dim);
        if (data != null) {
            saveChunkTile(dim, cx, cz, data);
        }
    }

    // Chunk Unload Finalizer: Captures any distant automated/redstone/TNT/fire changes before leaving RAM
    public void onChunkUnload(ServerLevel level, LevelChunk chunk) {
        if (chunk == null || level == null) return;
        int cx = chunk.getPos().x();
        int cz = chunk.getPos().z();
        updateChunkIfModified(level, chunk, cx, cz);
    }

    // Real-Time Player Proximity Terrain Change Scanner
    public int scanActivePlayers(MinecraftServer server) {
        if (server == null) return 0;
        int modifiedCount = 0;
        int radius = Math.max(1, Math.min(6, config.scanRadius > 0 ? config.scanRadius : 2));

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ServerLevel level = (ServerLevel) player.level();
            int centerCx = ((int) player.getX()) >> 4;
            int centerCz = ((int) player.getZ()) >> 4;

            for (int dz = -radius; dz <= radius; dz++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    int cx = centerCx + dx;
                    int cz = centerCz + dz;

                    if (level.hasChunk(cx, cz)) {
                        LevelChunk chunk = level.getChunk(cx, cz);
                        if (chunk != null) {
                            if (updateChunkIfModified(level, chunk, cx, cz)) {
                                modifiedCount++;
                            }
                        }
                    }
                }
            }
        }
        return modifiedCount;
    }

    // Periodic Spawn Chunks Scanner (For permanent automated spawn farms/chunk loaders)
    public int scanSpawnChunks(MinecraftServer server) {
        if (server == null) return 0;
        int modified = 0;
        for (ServerLevel level : server.getAllLevels()) {
            if (level == null) continue;
            BlockPos spawnPos = (level.getRespawnData() != null && level.getRespawnData().pos() != null) ? level.getRespawnData().pos() : BlockPos.ZERO;
            int spawnCx = spawnPos.getX() >> 4;
            int spawnCz = spawnPos.getZ() >> 4;

            for (int dz = -1; dz <= 1; dz++) {
                for (int dx = -1; dx <= 1; dx++) {
                    int cx = spawnCx + dx;
                    int cz = spawnCz + dz;
                    if (level.hasChunk(cx, cz)) {
                        LevelChunk chunk = level.getChunk(cx, cz);
                        if (chunk != null) {
                            if (updateChunkIfModified(level, chunk, cx, cz)) {
                                modified++;
                            }
                        }
                    }
                }
            }
        }
        return modified;
    }

    public boolean updateChunkIfModified(ServerLevel level, LevelChunk chunk, int cx, int cz) {
        if (chunk == null || level == null) return false;
        String dim = normalizeDim(level.dimension().identifier().toString());
        String key = cx + "_" + cz;

        Map<String, byte[]> tileCache = tileMemoryCache.computeIfAbsent(dim, k -> new ConcurrentHashMap<>());
        byte[] oldData = tileCache.get(key);
        byte[] newData = renderChunkToBytes(level, chunk, cx, cz, dim);
        if (newData == null) return false;

        // If data hasn't changed at all, zero work done
        if (oldData != null && Arrays.equals(oldData, newData)) {
            return false;
        }

        // Terrain changed! Update in-memory tile, region buffer, and version
        saveChunkTile(dim, cx, cz, newData);
        return true;
    }

    public int forceRenderRadius(ServerLevel level, int centerChunkX, int centerChunkZ, int radius) {
        if (level == null) return 0;
        String dim = normalizeDim(level.dimension().identifier().toString());
        int count = 0;

        for (int cz = centerChunkZ - radius; cz <= centerChunkZ + radius; cz++) {
            for (int cx = centerChunkX - radius; cx <= centerChunkX + radius; cx++) {
                if (level.hasChunk(cx, cz)) {
                    LevelChunk chunk = level.getChunk(cx, cz);
                    if (chunk != null) {
                        byte[] data = renderChunkToBytes(level, chunk, cx, cz, dim);
                        if (data != null) {
                            saveChunkTile(dim, cx, cz, data);
                            count++;
                        }
                    }
                }
            }
        }
        return count;
    }

    public byte[] getChunkTile(String dim, int cx, int cz) {
        dim = normalizeDim(dim);
        String key = cx + "_" + cz;
        Map<String, byte[]> tileCache = tileMemoryCache.get(dim);
        if (tileCache != null) {
            byte[] cached = tileCache.get(key);
            if (cached != null) return cached;
        }

        File file = new File(new File(baseTilesDir, dim), key + ".vmap");
        if (file.exists() && file.length() == 256) {
            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] data = fis.readAllBytes();
                if (data.length == 256) {
                    if (tileCache != null) tileCache.put(key, data);
                    Set<String> diskTiles = knownDiskTiles.get(dim);
                    if (diskTiles != null) diskTiles.add(key);
                    int rx = cx >> 5;
                    int rz = cz >> 5;
                    updateRegionBuffer(dim, rx, rz, cx, cz, data);
                    return data;
                }
            } catch (IOException ignored) {}
        }
        return null;
    }

    public byte[] getRegionTile(String dim, int rx, int rz) {
        dim = normalizeDim(dim);
        String regKey = rx + "_" + rz;
        Map<String, byte[]> regionCache = regionMemoryCache.get(dim);
        if (regionCache != null) {
            byte[] regionBuf = regionCache.get(regKey);
            if (regionBuf != null) {
                byte[] copy = new byte[262144];
                synchronized (regionBuf) {
                    System.arraycopy(regionBuf, 0, copy, 0, 262144);
                }
                return copy;
            }
        }

        byte[] newRegion = new byte[262144];
        boolean hasData = false;
        int minCx = rx << 5;
        int minCz = rz << 5;

        for (int lcz = 0; lcz < 32; lcz++) {
            for (int lcx = 0; lcx < 32; lcx++) {
                int cx = minCx + lcx;
                int cz = minCz + lcz;
                byte[] chunkData = getChunkTile(dim, cx, cz);
                if (chunkData != null && chunkData.length == 256) {
                    hasData = true;
                    for (int lz = 0; lz < 16; lz++) {
                        int regionZ = lcz * 16 + lz;
                        int regionOffset = regionZ * 512 + (lcx * 16);
                        System.arraycopy(chunkData, lz * 16, newRegion, regionOffset, 16);
                    }
                }
            }
        }

        if (hasData) {
            if (regionCache != null) regionCache.put(regKey, newRegion);
            return newRegion;
        }

        return null;
    }

    private byte[] renderChunkToBytes(ServerLevel level, LevelChunk chunk, int cx, int cz, String dim) {
        if (chunk == null) return null;

        byte[] tile = new byte[256];
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int chunkBaseX = cx << 4;
        int chunkBaseZ = cz << 4;

        int minY = (level != null) ? level.getMinY() : (dim.equals("the_nether") ? 0 : -64);
        int maxY = (level != null) ? level.getMaxY() : (dim.equals("the_nether") ? 128 : 320);

        int[] heights = new int[256];
        MapColor[] colors = new MapColor[256];
        boolean isNether = dim.equals("the_nether");

        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                int worldX = chunkBaseX + lx;
                int worldZ = chunkBaseZ + lz;
                int idx = lz * 16 + lx;

                try {
                    if (isNether) {
                        // Intelligent Nether Cavern Floor Scanner:
                        // 1. Bore down through the solid nether ceiling (Y=115 -> Y=32) until finding open air
                        int scanY = 115;
                        pos.set(worldX, scanY, worldZ);
                        while (scanY > 35 && !chunk.getBlockState(pos).isAir()) {
                            scanY--;
                            pos.setY(scanY);
                        }

                        // 2. From the open air cavern space, scan down to find the floor / lava ocean
                        BlockState floorState = Blocks.AIR.defaultBlockState();
                        MapColor floorColor = MapColor.NONE;

                        while (scanY > minY) {
                            floorState = chunk.getBlockState(pos);
                            floorColor = floorState.getBlock().defaultMapColor();

                            // Stop when hitting non-air solid floor or lava
                            if (floorColor != MapColor.NONE && !floorState.isAir()) {
                                break;
                            }
                            scanY--;
                            pos.setY(scanY);
                        }

                        heights[idx] = scanY;
                        colors[idx] = floorColor;
                    } else {
                        // Standard Overworld & The End surface scanner
                        int topY = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, lx, lz);
                        int startScanY = dim.equals("the_end") ? Math.max(topY, 70) : Math.max(topY, 150);
                        startScanY = Math.min(maxY - 1, Math.max(minY + 1, startScanY));

                        pos.set(worldX, startScanY, worldZ);
                        BlockState state = chunk.getBlockState(pos);
                        MapColor mapColor = state.getBlock().defaultMapColor();

                        while (mapColor == MapColor.NONE && pos.getY() > minY) {
                            pos.setY(pos.getY() - 1);
                            state = chunk.getBlockState(pos);
                            mapColor = state.getBlock().defaultMapColor();
                        }

                        heights[idx] = pos.getY();
                        colors[idx] = mapColor;
                    }
                } catch (Exception e) {
                    heights[idx] = minY;
                    colors[idx] = MapColor.NONE;
                }
            }
        }

        // Shading: compute relief relative to northern block
        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                int idx = lz * 16 + lx;
                MapColor color = colors[idx];
                if (color == null || color == MapColor.NONE) {
                    tile[idx] = 0;
                    continue;
                }

                int y = heights[idx];
                int yNorth = (lz > 0) ? heights[(lz - 1) * 16 + lx] : y;

                MapColor.Brightness brightness;
                if (y > yNorth) {
                    brightness = MapColor.Brightness.HIGH;
                } else if (y < yNorth) {
                    brightness = MapColor.Brightness.LOW;
                } else {
                    brightness = MapColor.Brightness.NORMAL;
                }

                tile[idx] = color.getPackedId(brightness);
            }
        }

        return tile;
    }

    private void saveChunkTile(String dim, int cx, int cz, byte[] data) {
        if (data == null || data.length != 256) return;
        dim = normalizeDim(dim);
        String key = cx + "_" + cz;

        Map<String, byte[]> tileCache = tileMemoryCache.computeIfAbsent(dim, k -> new ConcurrentHashMap<>());
        Set<String> diskTiles = knownDiskTiles.computeIfAbsent(dim, k -> ConcurrentHashMap.newKeySet());
        Map<String, AtomicInteger> versions = regionVersions.computeIfAbsent(dim, k -> new ConcurrentHashMap<>());

        tileCache.put(key, data);
        diskTiles.add(key);
        int rx = cx >> 5;
        int rz = cz >> 5;
        String regKey = rx + "_" + rz;
        updateRegionBuffer(dim, rx, rz, cx, cz, data);
        versions.computeIfAbsent(regKey, k -> new AtomicInteger()).incrementAndGet();

        final String finalDim = dim;
        asyncDiskWriter.submit(() -> {
            File file = new File(new File(baseTilesDir, finalDim), key + ".vmap");
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(data);
            } catch (IOException ignored) {}
        });
    }

    public String getExploredIndexJson(String dim) {
        dim = normalizeDim(dim);
        Set<String> set = knownDiskTiles.get(dim);
        StringBuilder sb = new StringBuilder("[");
        if (set != null) {
            boolean first = true;
            for (String key : set) {
                String[] parts = key.split("_");
                if (parts.length == 2) {
                    if (!first) sb.append(",");
                    first = false;
                    sb.append("[").append(parts[0]).append(",").append(parts[1]).append("]");
                }
            }
        }
        sb.append("]");
        return sb.toString();
    }

    public String getExploredRegionsVersionJson(String dim) {
        dim = normalizeDim(dim);
        Map<String, AtomicInteger> versions = regionVersions.get(dim);
        StringBuilder sb = new StringBuilder("{");
        if (versions != null) {
            boolean first = true;
            for (Map.Entry<String, AtomicInteger> entry : versions.entrySet()) {
                if (!first) sb.append(",");
                first = false;
                sb.append("\"").append(entry.getKey()).append("\":").append(entry.getValue().get());
            }
        }
        sb.append("}");
        return sb.toString();
    }
}

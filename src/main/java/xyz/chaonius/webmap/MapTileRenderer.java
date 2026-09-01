package xyz.chaonius.webmap;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class MapTileRenderer {
    private final MinecraftServer server;
    private final File cacheDir;
    private final File cacheFile;
    private BufferedImage masterImage;
    private static final int MAP_SIZE = 1024; // 1024x1024 pixel canvas covering -2048 to +2048 blocks
    private static final int BLOCK_SCALE = 4; // 4 blocks per pixel (Level 2/4 vanilla scale)
    private static final int HALF_WORLD = (MAP_SIZE * BLOCK_SCALE) / 2; // 2048 blocks from spawn

    public MapTileRenderer(MinecraftServer server) {
        this.server = server;
        this.cacheDir = new File("webmap_cache");
        if (!cacheDir.exists()) {
            cacheDir.mkdirs();
        }
        this.cacheFile = new File(cacheDir, "overworld_master.png");
        loadCache();
    }

    private void loadCache() {
        if (cacheFile.exists()) {
            try {
                masterImage = ImageIO.read(cacheFile);
                System.out.println("[VanillaWebMap] Loaded persistent local map cache from " + cacheFile.getAbsolutePath());
            } catch (IOException e) {
                System.err.println("[VanillaWebMap] Failed to load map cache, creating new: " + e.getMessage());
            }
        }
        if (masterImage == null) {
            masterImage = new BufferedImage(MAP_SIZE, MAP_SIZE, BufferedImage.TYPE_INT_ARGB);
        }
    }

    public synchronized void saveCache() {
        if (masterImage != null) {
            try {
                ImageIO.write(masterImage, "png", cacheFile);
                System.out.println("[VanillaWebMap] Saved persistent map cache to disk (" + cacheFile.length() + " bytes)");
            } catch (IOException e) {
                System.err.println("[VanillaWebMap] Failed to save map cache: " + e.getMessage());
            }
        }
    }

    public synchronized void updateLoadedChunks() {
        ServerLevel level = server.overworld();
        if (level == null || masterImage == null) return;

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        // Scan active players and update chunks in their vicinity onto the persistent map
        server.getPlayerList().getPlayers().forEach(player -> {
            int pChunkX = ((int) player.getX()) >> 4;
            int pChunkZ = ((int) player.getZ()) >> 4;
            int radius = 8; // 8 chunks radius around active players

            for (int cz = pChunkZ - radius; cz <= pChunkZ + radius; cz++) {
                for (int cx = pChunkX - radius; cx <= pChunkX + radius; cx++) {
                    LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                    if (chunk == null) continue;

                    int chunkBaseX = cx << 4;
                    int chunkBaseZ = cz << 4;

                    for (int lz = 0; lz < 16; lz += BLOCK_SCALE) {
                        int worldZ = chunkBaseZ + lz;
                        int py = (worldZ + HALF_WORLD) / BLOCK_SCALE;
                        if (py < 0 || py >= MAP_SIZE) continue;

                        for (int lx = 0; lx < 16; lx += BLOCK_SCALE) {
                            int worldX = chunkBaseX + lx;
                            int px = (worldX + HALF_WORLD) / BLOCK_SCALE;
                            if (px < 0 || px >= MAP_SIZE) continue;

                            try {
                                int y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, lx, lz);
                                int yNorth = (lz > 0) ? chunk.getHeight(Heightmap.Types.WORLD_SURFACE, lx, lz - 1) : y;

                                pos.set(worldX, Math.max(0, y - 1), worldZ);
                                BlockState state = chunk.getBlockState(pos);
                                MapColor color = state.getBlock().defaultMapColor();

                                if (color == MapColor.NONE) continue;

                                MapColor.Brightness brightness;
                                if (y > yNorth) {
                                    brightness = MapColor.Brightness.HIGH;
                                } else if (y < yNorth) {
                                    brightness = MapColor.Brightness.LOW;
                                } else {
                                    brightness = MapColor.Brightness.NORMAL;
                                }

                                int argb = color.calculateARGBColor(brightness);
                                masterImage.setRGB(px, py, argb);
                            } catch (Exception ignored) {}
                        }
                    }
                }
            }
        });
    }

    public synchronized BufferedImage getCachedMap() {
        // Fast in-memory clone/return for web clients (0ms latency, zero chunk locks)
        return masterImage;
    }
}

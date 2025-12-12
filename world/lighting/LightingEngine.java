// src/main/java/com/mineshaft/world/lighting/LightingEngine.java
package com.mineshaft.world.lighting;

import com.mineshaft.block.BlockRegistry;
import com.mineshaft.block.GameBlock;
import com.mineshaft.core.Settings;
import com.mineshaft.core.TimeOfDay;
import com.mineshaft.world.Chunk;
import com.mineshaft.world.ChunkSection;
import com.mineshaft.world.ChunkState;
import com.mineshaft.world.World;

import java.util.*;
import java.util.concurrent.*;

/**
 * ⚡ OPTIMIZED Lighting Engine v3.0 - Minecraft-Style Lighting System
 * 
 * ============================================================================
 * MINECRAFT-STYLE LIGHTING CONCEPT:
 * ============================================================================
 * 
 * 1. LIGHT VALUES (0-15) are STATIC and stored per-block:
 * - Skylight: Can this block see the sky? (shadow propagation)
 * - Blocklight: Is there a torch/glowstone nearby?
 * 
 * 2. LIGHT VALUES DO NOT CHANGE WITH TIME OF DAY!
 * - A block with skylight=15 ALWAYS has skylight=15
 * - At noon: displayed brightness = skylight * 1.0
 * - At midnight: displayed brightness = skylight * 0.2
 * 
 * 3. TIME-OF-DAY BRIGHTNESS is applied at RENDER TIME (via glColor)
 * - ChunkRenderer.setTimeOfDayBrightness() handles this
 * - NO mesh rebuild needed when time changes!
 * 
 * 4. MESH REBUILD only happens when:
 * - Block is placed (shadow propagation changes)
 * - Block is removed (shadow propagation changes)
 * - Chunk first loads
 * 
 * ============================================================================
 */
public class LightingEngine {

    private SunLightCalculator sunLight;
    private TimeOfDay timeOfDay; // ✅ NEW: Reference to TimeOfDay for dynamic sky light
    private World world; // ✅ NEW: Reference to World for accessing chunks
    private static final float[] BRIGHTNESS_TABLE = new float[16];

    // ⚡ PERFORMANCE OPTIMIZATIONS
    private final ExecutorService lightingExecutor;
    private final Queue<Chunk> pendingLightUpdates = new ConcurrentLinkedQueue<>();
    private final Set<Chunk> processingChunks = ConcurrentHashMap.newKeySet();

    // Throttling controls - ✅ INCREASED from 4 to 8 for faster chunk loading
    private static final int MAX_CHUNKS_PER_FRAME = 8;

    /**
     * ✅ MINECRAFT-STYLE: Maximum skylight level (15 during day, 0-4 at night)
     * This is queried from TimeOfDay to reflect current time of day
     * Shadow propagation reduces this value as light travels through blocks
     */
    private static final int MAX_SKYLIGHT_LEVEL = 15;

    // Cache for avoiding redundant updates
    private final Set<ChunkPosition> initializedChunks = ConcurrentHashMap.newKeySet();

    // ✅ NEW: Track previous sky light for detecting time-based changes
    private int previousSkylightLevel = 15;

    static {
        // ✅ Brightness table converts light level (0-15) to brightness (0.0-1.0)
        // This is STATIC - doesn't change with time of day
        for (int i = 0; i < 16; i++) {
            if (i <= 0) {
                BRIGHTNESS_TABLE[i] = 0.4f; // Minimum brightness (cave darkness)
            } else if (i >= 15) {
                BRIGHTNESS_TABLE[i] = 1.0f; // Maximum brightness
            } else {
                float normalized = i / 15.0f;
                BRIGHTNESS_TABLE[i] = 0.4f + (normalized * 0.6f);
            }
        }
    }

    private static class LightNode {
        int x, y, z;
        int lightLevel;

        LightNode(int x, int y, int z, int lightLevel) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.lightLevel = lightLevel;
        }
    }

    private static class ChunkPosition {
        int x, z;

        ChunkPosition(int x, int z) {
            this.x = x;
            this.z = z;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (!(o instanceof ChunkPosition))
                return false;
            ChunkPosition that = (ChunkPosition) o;
            return x == that.x && z == that.z;
        }

        @Override
        public int hashCode() {
            return Objects.hash(x, z);
        }
    }

    public LightingEngine(World world, TimeOfDay timeOfDay) {
        this.sunLight = new SunLightCalculator(timeOfDay);
        this.timeOfDay = timeOfDay; // ✅ Store reference for dynamic sky light queries
        this.world = world; // ✅ Store reference for accessing chunks on time change

        // ✅ OPTIMIZED: Dynamic thread count based on CPU cores
        int threads = Math.max(4, Runtime.getRuntime().availableProcessors() / 2);
        this.lightingExecutor = Executors.newFixedThreadPool(threads, r -> {
            Thread t = new Thread(r, "LightingWorker");
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY - 1);
            return t;
        });

        if (Settings.DEBUG_MODE) {
            System.out.printf("[LightingEngine] Initialized with %d threads (Minecraft-style lighting)%n", threads);
        }
    }

    /**
     * ⚡ Update sun light direction and sky light level
     * 
     * ✅ MINECRAFT-STYLE: Updates both sun direction (visual) and sky light level
     * (lighting)
     * 
     * Sun direction affects:
     * - Sky color gradient
     * - Sun/moon position in skybox
     * 
     * Sky light level affects:
     * - Light values in blocks (15 day, 0-4 night)
     * - Propagates through BFS without mesh rebuild
     * 
     * @return true if sun direction changed significantly
     */
    public boolean updateSunLight() {
        boolean directionChanged = sunLight.updateSunDirection();

        // ✅ NEW: Check if sky light level changed (e.g., day→night transition)
        int currentSkylightLevel = getCurrentSkylightLevel();
        if (currentSkylightLevel != previousSkylightLevel) {
            // Sky light changed! Queue all initialized chunks for lighting update
            // This updates light VALUES but doesn't rebuild meshes
            // (Mesh rebuild happens only on geometry changes)
            onSkylightLevelChanged(previousSkylightLevel, currentSkylightLevel);
            previousSkylightLevel = currentSkylightLevel;
        }

        return directionChanged;
    }

    /**
     * ✅ NEW: Handle sky light level changes (day→night transitions)
     * Updates all loaded chunks with new sky light values
     */
    private void onSkylightLevelChanged(int oldLevel, int newLevel) {
        if (Settings.DEBUG_MODE) {
            System.out.printf("[LightingEngine] Sky light changed: %d → %d - Updating all loaded chunks%n", oldLevel,
                    newLevel);
        }

        // ✅ Update all loaded chunks with new sky light level
        // This updates VALUES stored in blocks, not mesh geometry
        if (world != null) {
            Collection<Chunk> loadedChunks = world.getChunks();

            if (loadedChunks != null && !loadedChunks.isEmpty()) {
                // ✅ THREAD SAFETY FIX: Create a copy of the list before passing to background
                // thread
                // Iterating over the live 'loadedChunks' collection in a background thread is
                // unsafe
                // because the main thread might add/remove chunks
                // (ConcurrentModificationException)
                List<Chunk> safeChunkList = new ArrayList<>(loadedChunks);

                // Process async to avoid blocking
                lightingExecutor.submit(() -> {
                    int chunksUpdated = 0;
                    for (Chunk chunk : safeChunkList) {
                        // ✅ FIX: Check isLightInitialized instead of isReady
                        // This ensures chunks in LIGHT_PENDING state also get time updates
                        if (chunk != null && chunk.isLightInitialized()) {
                            updateChunkSkylightForTimeChange(chunk, newLevel);
                            chunksUpdated++;
                        }
                    }

                    if (Settings.DEBUG_MODE) {
                        System.out.printf("[LightingEngine] Updated sky light in %d chunks%n", chunksUpdated);
                    }
                });
            }
        }
    }

    /**
     * ✅ NEW: Update chunk's sky light for time-of-day change
     * Recalculates sky light propagation with new sky light level
     * NOTE: Marks for rebuild since vertex colors are baked into mesh
     */
    private void updateChunkSkylightForTimeChange(Chunk chunk, int newSkylightLevel) {
        if (chunk == null)
            return;

        // ✅ STEP 1: Update vertical propagation with new time-based sky light
        for (int x = 0; x < Chunk.CHUNK_SIZE; x++) {
            for (int z = 0; z < Chunk.CHUNK_SIZE; z++) {
                updateColumnSkylightWithLevel(chunk, x, z, newSkylightLevel);
            }
        }

        // ✅ STEP 2: Propagate horizontally into tunnels/caves
        propagateSkylightHorizontal(chunk);

        // ✅ CRITICAL FIX: Mesh MUST rebuild because vertex colors include light values
        // Vertex colors = blockColor * lightBrightness(skylight)
        // When skylight changes, vertex colors must be recalculated
        chunk.setNeedsLightingUpdate(true);
        chunk.setNeedsGeometryRebuild(true); // Force full rebuild for immediate visual update
    }

    /**
     * ✅ NEW: Update column skylight with specific level (for time changes)
     */
    private void updateColumnSkylightWithLevel(Chunk chunk, int x, int z, int skylightLevel) {
        int currentLight = skylightLevel; // Start from new time-based sky light

        // Top to bottom - propagate skylight down until blocked
        for (int index = Chunk.CHUNK_HEIGHT - 1; index >= 0; index--) {
            GameBlock block = chunk.getBlockByIndex(x, index, z);
            int newLight;

            if (block == null || block.isAir()) {
                // Air blocks receive full skylight from above
                newLight = currentLight;
            } else if (block == BlockRegistry.WATER || block == BlockRegistry.OAK_LEAVES) {
                // Transparent blocks reduce skylight slightly
                newLight = Math.max(0, currentLight - 1);
                currentLight = newLight;
            } else if (block.isSolid()) {
                // Solid blocks block all skylight
                newLight = 0;
                currentLight = 0;
            } else {
                // Non-solid, non-air blocks (like flowers) pass light through
                newLight = currentLight;
            }

            // Convert index to world Y and update
            int worldY = Settings.indexToWorldY(index);
            chunk.setSkyLight(x, worldY, z, newLight);
        }
    }

    /**
     * ⚡ Call every frame for processing queued light updates
     * 
     * ✅ MINECRAFT-STYLE: This only processes block-change light updates,
     * NOT time-of-day updates (those don't exist anymore)
     */
    public void update() {
        // Process queued chunks (from block place/remove only)
        processQueuedChunks();
    }

    /**
     * ⚡ Process limited chunks per frame
     */
    private void processQueuedChunks() {
        int chunksProcessed = 0;

        while (chunksProcessed < MAX_CHUNKS_PER_FRAME && !pendingLightUpdates.isEmpty()) {
            Chunk chunk = pendingLightUpdates.poll();

            if (chunk == null || processingChunks.contains(chunk)) {
                continue;
            }

            processingChunks.add(chunk);

            // Process async - update skylight for block changes
            lightingExecutor.submit(() -> {
                try {
                    updateChunkSkylightForBlockChange(chunk);
                } finally {
                    processingChunks.remove(chunk);
                }
            });

            chunksProcessed++;
        }
    }

    /**
     * ⚡ Update chunk skylight after block placement/removal
     * 
     * ✅ MINECRAFT-STYLE: Skylight is always 15 for sky-visible blocks
     * This method recalculates shadow propagation, NOT time-based brightness
     */
    private void updateChunkSkylightForBlockChange(Chunk chunk) {
        if (chunk == null)
            return;

        boolean changed = false;
        for (int x = 0; x < Chunk.CHUNK_SIZE; x++) {
            for (int z = 0; z < Chunk.CHUNK_SIZE; z++) {
                if (updateColumnSkylight(chunk, x, z)) {
                    changed = true;
                }
            }
        }

        // ✅ Only mark for rebuild if shadow propagation actually changed
        if (changed) {
            chunk.setNeedsLightingUpdate(true);
        }
    }

    /**
     * ⚡ Update single column skylight - shadow propagation
     * 
     * ✅ MINECRAFT-STYLE: Skylight starts at current time-of-day level (15 day, 0-4
     * night)
     * then propagates downward, decreasing through transparent/semi-transparent
     * blocks
     */
    private boolean updateColumnSkylight(Chunk chunk, int x, int z) {
        int currentLight = getCurrentSkylightLevel(); // ✅ Dynamic: uses time-based sky light
        boolean changed = false;

        // Top to bottom - propagate skylight down until blocked
        for (int index = Chunk.CHUNK_HEIGHT - 1; index >= 0; index--) {
            GameBlock block = chunk.getBlockByIndex(x, index, z);
            int newLight;

            if (block == null || block.isAir()) {
                // Air blocks receive full skylight from above
                newLight = currentLight;
            } else if (block == BlockRegistry.WATER || block == BlockRegistry.OAK_LEAVES) {
                // Transparent blocks reduce skylight slightly
                newLight = Math.max(0, currentLight - 1);
                currentLight = newLight;
            } else if (block.isSolid()) {
                // Solid blocks block all skylight
                newLight = 0;
                currentLight = 0;
            } else {
                // Non-solid, non-air blocks (like flowers) pass light through
                newLight = currentLight;
            }

            // Convert index to world Y
            int worldY = Settings.indexToWorldY(index);
            int oldLight = chunk.getSkyLight(x, worldY, z);

            // ✅ FIX: Only update if changed (prevents unnecessary rebuilds)
            if (oldLight != newLight) {
                chunk.setSkyLight(x, worldY, z, newLight);
                changed = true;
            }
        }

        return changed;
    }

    /**
     * ⚡ Queue chunk for light update (called when blocks change)
     * 
     * ✅ MINECRAFT-STYLE: Only called for block place/remove, NOT for time change
     */
    public void queueChunkForLightUpdate(Chunk chunk) {
        if (chunk != null && !processingChunks.contains(chunk)) {
            pendingLightUpdates.offer(chunk);
        }
    }

    /**
     * ⚡ Batch queue chunks
     */
    public void queueChunksForLightUpdate(Collection<Chunk> chunks) {
        for (Chunk chunk : chunks) {
            queueChunkForLightUpdate(chunk);
        }
    }

    public SunLightCalculator getSunLight() {
        return sunLight;
    }

    /**
     * ✅ MINECRAFT-STYLE: Get current skylight level (15 during day, 0-4 at night)
     * This reflects the current time of day and represents the global sky light
     * source.
     * Light propagates from this value through open air, decreasing by 1 per block.
     * 
     * @return Current sky light level based on time of day
     */
    public int getCurrentSkylightLevel() {
        if (timeOfDay != null) {
            return timeOfDay.getSkylightLevel(); // ✅ Dynamic: changes with time (15 day, 4 night)
        }
        return MAX_SKYLIGHT_LEVEL; // Fallback if TimeOfDay not available
    }

    /**
     * ⚡ Initial skylight setup for newly loaded chunk
     * 
     * ✅ MINECRAFT-STYLE: Initialize with current time-of-day skylight propagating
     * down
     * Uses current time's sky light level (15 day, 0-4 night) at load time
     */
    /**
     * ⚡ Initial skylight setup for newly loaded chunk
     * 
     * ✅ MINECRAFT-STYLE: Initialize with current time-of-day skylight propagating
     * down
     * Uses current time's sky light level (15 day, 0-4 night) at load time
     */
    public void initializeSkylightForChunk(Chunk chunk) {
        initializeSkylightForChunk(chunk, getCurrentSkylightLevel());
    }

    /**
     * ⚡ Full Lighting Initialization (Skylight + Blocklight)
     * 
     * ✅ CRITICAL FIX: This runs ALL lighting calculations in order,
     * and ONLY THEN marks the chunk as READY. This prevents the "black tunnel" bug
     * where the renderer draws the chunk before lighting is done.
     */
    public void initializeLighting(Chunk chunk) {
        if (chunk == null)
            return;

        ChunkPosition pos = new ChunkPosition(chunk.getChunkX(), chunk.getChunkZ());
        if (initializedChunks.contains(pos))
            return;

        lightingExecutor.submit(() -> {
            try {
                // 1. Initialize Skylight (Vertical + Horizontal)
                int skylightLevel = getCurrentSkylightLevel();

                // Vertical propagation
                for (int x = 0; x < Chunk.CHUNK_SIZE; x++) {
                    for (int z = 0; z < Chunk.CHUNK_SIZE; z++) {
                        updateColumnSkylightInitial(chunk, x, z, skylightLevel);
                    }
                }

                // Horizontal propagation (BFS)
                propagateSkylightHorizontal(chunk);

                // 2. Initialize Blocklight
                // ✅ OPTIMIZATION: Skip empty sections (all air)
                Queue<LightNode> lightQueue = new LinkedList<>();
                for (int sectionIndex = 0; sectionIndex < Chunk.SECTION_COUNT; sectionIndex++) {
                    ChunkSection section = chunk.getSection(sectionIndex);

                    // Skip empty sections - they have no light-emitting blocks
                    if (section == null || section.isEmpty()) {
                        continue;
                    }

                    // Only process non-empty sections
                    for (int localX = 0; localX < Chunk.CHUNK_SIZE; localX++) {
                        for (int localY = 0; localY < 16; localY++) { // Section size = 16
                            for (int localZ = 0; localZ < Chunk.CHUNK_SIZE; localZ++) {
                                GameBlock block = section.getBlock(localX, localY, localZ);
                                int lightLevel = (block != null) ? block.getLightLevel() : 0;

                                if (lightLevel > 0) {
                                    int worldY = sectionIndex * 16 + Settings.WORLD_MIN_Y + localY;
                                    chunk.setBlockLight(localX, worldY, localZ, lightLevel);
                                    lightQueue.add(new LightNode(localX, worldY, localZ, lightLevel));
                                }
                            }
                        }
                    }
                }
                propagateLightOptimized(chunk, lightQueue, false);

                // 3. ✅ Mark as READY only after ALL lighting is done
                initializedChunks.add(pos);
                chunk.setLightInitialized(true);
                chunk.setState(ChunkState.READY);
                chunk.setNeedsGeometryRebuild(true);

                // Mark neighbors for rebuild (to fix edge seams)
                if (world != null) {
                    world.markNeighborsForRebuild(chunk.getChunkX(), chunk.getChunkZ());
                }

                if (Settings.DEBUG_CHUNK_LOADING) {
                    System.out.printf("[LightingEngine] Fully initialized lighting for chunk [%d, %d]%n",
                            chunk.getChunkX(), chunk.getChunkZ());
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    /**
     * ⚡ FAST Lighting Initialization - INSTANT, no background thread
     * 
     * This method:
     * - Runs SYNCHRONOUSLY on the calling thread
     * - Only does vertical skylight propagation (no horizontal BFS)
     * - Marks chunk as READY immediately
     * 
     * Use this for faster chunk loading when cave lighting quality is less
     * important.
     */
    public void initializeFastLighting(Chunk chunk) {
        if (chunk == null)
            return;

        ChunkPosition pos = new ChunkPosition(chunk.getChunkX(), chunk.getChunkZ());
        if (initializedChunks.contains(pos))
            return;

        // Use fast calculator - runs immediately, no threading
        FastLightingCalculator.initializeFastSkylight(chunk, getCurrentSkylightLevel());

        // Quick blocklight pass - just mark sources, minimal propagation
        for (int sectionIndex = 0; sectionIndex < Chunk.SECTION_COUNT; sectionIndex++) {
            ChunkSection section = chunk.getSection(sectionIndex);
            if (section == null || section.isEmpty()) {
                continue;
            }

            for (int localX = 0; localX < Chunk.CHUNK_SIZE; localX++) {
                for (int localY = 0; localY < 16; localY++) {
                    for (int localZ = 0; localZ < Chunk.CHUNK_SIZE; localZ++) {
                        GameBlock block = section.getBlock(localX, localY, localZ);
                        if (block != null && block.getLightLevel() > 0) {
                            int worldY = sectionIndex * 16 + Settings.WORLD_MIN_Y + localY;
                            FastLightingCalculator.updateBlockLightAt(chunk, localX, worldY, localZ,
                                    block.getLightLevel());
                        }
                    }
                }
            }
        }

        // Mark as ready immediately
        initializedChunks.add(pos);
        chunk.setLightInitialized(true);
        chunk.setState(ChunkState.READY);
        chunk.setNeedsGeometryRebuild(true);
    }

    /**
     * ⚡ Initial skylight setup with specific level
     * 
     * @deprecated Use initializeLighting(Chunk) for full initialization
     */
    @Deprecated
    public void initializeSkylightForChunk(Chunk chunk, int skylightLevel) {
        if (chunk == null)
            return;

        ChunkPosition pos = new ChunkPosition(chunk.getChunkX(), chunk.getChunkZ());

        // ✅ Skip if already initialized
        if (initializedChunks.contains(pos)) {
            return;
        }

        lightingExecutor.submit(() -> {
            // ✅ STEP 1: Vertical propagation (top-to-bottom in each column)
            for (int x = 0; x < Chunk.CHUNK_SIZE; x++) {
                for (int z = 0; z < Chunk.CHUNK_SIZE; z++) {
                    updateColumnSkylightInitial(chunk, x, z, skylightLevel);
                }
            }

            // ✅ STEP 2: Horizontal propagation (BFS spread into tunnels/caves)
            propagateSkylightHorizontal(chunk);

            initializedChunks.add(pos);
            chunk.setLightInitialized(true);

            if (Settings.DEBUG_CHUNK_LOADING) {
                System.out.printf("[LightingEngine] Initialized skylight for chunk [%d, %d]%n",
                        chunk.getChunkX(), chunk.getChunkZ());
            }
        });
    }

    /**
     * ⚡ Initial column skylight - REVISED to support horizontal propagation
     * Step 1: Mark sky-visible blocks with full skylight
     * Step 2: Horizontal propagation fills in caves/tunnels
     * Step 3: (handled elsewhere) Vertical shadow propagation
     */
    /**
     * ⚡ Initial column skylight - REVISED (User Fix)
     * Standard "rain down" algorithm:
     * - Air: Propagates full light
     * - Water/Leaves: Decays light
     * - Solid: Stops light
     */
    private void updateColumnSkylightInitial(Chunk chunk, int x, int z, int skylightLevel) {
        int current = skylightLevel;

        for (int index = Chunk.CHUNK_HEIGHT - 1; index >= 0; index--) {
            int worldY = Settings.indexToWorldY(index);
            GameBlock block = chunk.getBlock(x, worldY, z);

            if (block == null || block.isAir()) {
                // Air: Maintain current light level
                chunk.setSkyLight(x, worldY, z, current);
            } else if (block == BlockRegistry.OAK_LEAVES || block == BlockRegistry.WATER) {
                // Semi-transparent: Decay light
                current = Math.max(0, current - 1);
                chunk.setSkyLight(x, worldY, z, current);
            } else if (block.isSolid()) {
                // Solid: Block light completely
                current = 0;
                chunk.setSkyLight(x, worldY, z, 0);
            } else {
                // Other non-solid blocks (flowers, etc.): Maintain light
                chunk.setSkyLight(x, worldY, z, current);
            }
        }
    }

    /**
     * ✅ NEW: Horizontal BFS light propagation
     * Spreads light into tunnels and caves with -1 attenuation per block
     */
    private void propagateSkylightHorizontal(Chunk chunk) {
        if (chunk == null)
            return;

        Queue<LightNode> queue = new LinkedList<>();

        // ✅ Only seed from BRIGHT light sources (level >= 12) to avoid processing
        // entire chunk
        // These are blocks near the surface that can spread light into caves
        int seedCount = 0;
        for (int x = 0; x < Chunk.CHUNK_SIZE; x++) {
            for (int z = 0; z < Chunk.CHUNK_SIZE; z++) {
                // Start from top, find first bright light block
                for (int index = Chunk.CHUNK_HEIGHT - 1; index >= 0; index--) {
                    int worldY = Settings.indexToWorldY(index);
                    int lightLevel = chunk.getSkyLight(x, worldY, z);

                    // ✅ FIX: Seed from ANY light source > 0 (needed for night lighting)
                    // Previously was >= 12 which broke night propagation
                    if (lightLevel > 0) {
                        // OPTIMIZATION: Only seed if this block has a darker neighbor
                        // This prevents adding thousands of open-air blocks to the queue
                        if (canPropagateToNeighbors(chunk, x, worldY, z, lightLevel)) {
                            queue.offer(new LightNode(x, worldY, z, lightLevel));
                            seedCount++;
                        }
                        // FIX: Do NOT break here. We need to check the whole column for potential
                        // horizontal entry points.
                        // break;
                    }

                    // Stop searching this column if we hit a solid block
                    GameBlock block = chunk.getBlock(x, worldY, z);
                    if (block != null && block.isSolid() && block != BlockRegistry.WATER
                            && block != BlockRegistry.OAK_LEAVES) {
                        break;
                    }
                }
            }
        }

        if (Settings.DEBUG_LIGHTING) {
            System.out.printf("[LightingEngine] Horizontal propagation for chunk [%d,%d]: %d seed sources%n",
                    chunk.getChunkX(), chunk.getChunkZ(), seedCount);

            if (seedCount == 0) {
                System.out.printf("[DEBUG] NO SEEDS - Checking first column [0,0]:%n");
                for (int index = Chunk.CHUNK_HEIGHT - 1; index >= Math.max(0, Chunk.CHUNK_HEIGHT - 10); index--) {
                    int worldY = Settings.indexToWorldY(index);
                    int light = chunk.getSkyLight(0, worldY, 0);
                    GameBlock block = chunk.getBlock(0, worldY, 0);
                    System.out.printf("  Y=%d: light=%d, block=%s%n",
                            worldY, light, block != null ? block.toString() : "AIR");
                }
            }
        }

        // BFS propagation to 6 neighbors
        int[][] directions = {
                { 1, 0, 0 }, { -1, 0, 0 }, // X
                { 0, 1, 0 }, { 0, -1, 0 }, // Y
                { 0, 0, 1 }, { 0, 0, -1 } // Z
        };

        int propagations = 0;
        int MAX_PROPAGATIONS = 10000; // Prevent infinite loops

        while (!queue.isEmpty() && propagations < MAX_PROPAGATIONS) {
            LightNode node = queue.poll();

            int currentLight = chunk.getSkyLight(node.x, node.y, node.z);
            if (currentLight <= 0)
                continue;

            for (int[] dir : directions) {
                int nx = node.x + dir[0];
                int ny = node.y + dir[1];
                int nz = node.z + dir[2];

                if (!Settings.isValidWorldY(ny))
                    continue;
                if (nx < 0 || nx >= Chunk.CHUNK_SIZE || nz < 0 || nz >= Chunk.CHUNK_SIZE)
                    continue;

                // Get neighbor block - NULL means AIR
                GameBlock neighborBlock = chunk.getBlock(nx, ny, nz);

                // Skip solid opaque blocks
                if (neighborBlock != null && neighborBlock.isSolid() &&
                        neighborBlock != BlockRegistry.WATER && neighborBlock != BlockRegistry.OAK_LEAVES) {
                    continue;
                }

                // Calculate new light with -1 attenuation
                int newLight = currentLight - 1;
                if (newLight <= 0)
                    continue;

                int existingLight = chunk.getSkyLight(nx, ny, nz);

                // Only update if brighter
                if (newLight > existingLight) {
                    chunk.setSkyLight(nx, ny, nz, newLight);
                    queue.offer(new LightNode(nx, ny, nz, newLight));
                    propagations++;
                }
            }
        }

        if (Settings.DEBUG_LIGHTING) {
            System.out.printf("[LightingEngine] Horizontal propagation completed: %d propagations%n", propagations);
        }
    }

    /**
     * Helper to check if a block can propagate light to any neighbor
     * Used to optimize seeding of the BFS queue
     */
    private boolean canPropagateToNeighbors(Chunk chunk, int x, int y, int z, int currentLight) {
        int[][] directions = {
                { 1, 0, 0 }, { -1, 0, 0 },
                { 0, 1, 0 }, { 0, -1, 0 },
                { 0, 0, 1 }, { 0, 0, -1 }
        };

        for (int[] dir : directions) {
            int nx = x + dir[0];
            int ny = y + dir[1];
            int nz = z + dir[2];

            if (!Settings.isValidWorldY(ny))
                continue;

            // Check if neighbor is in bounds
            if (nx >= 0 && nx < Chunk.CHUNK_SIZE && nz >= 0 && nz < Chunk.CHUNK_SIZE) {
                // Intra-chunk check
                GameBlock neighborBlock = chunk.getBlock(nx, ny, nz);
                if (neighborBlock != null && neighborBlock.isSolid() &&
                        neighborBlock != BlockRegistry.WATER && neighborBlock != BlockRegistry.OAK_LEAVES) {
                    continue; // Solid blocks block light
                }

                int neighborLight = chunk.getSkyLight(nx, ny, nz);
                // If we can increase the neighbor's light (current - 1 > neighbor), then we
                // should propagate
                if (currentLight - 1 > neighborLight) {
                    return true;
                }
            } else {
                // Edge of chunk - always assume we might need to propagate to neighbor chunk
                // (Simpler than checking neighbor chunk existence/state here)
                return true;
            }
        }
        return false;
    }

    /**
     * ⚡ Blocklight initialization
     */
    public void initializeBlocklightForChunk(Chunk chunk) {
        if (chunk == null)
            return;

        lightingExecutor.submit(() -> {
            Queue<LightNode> lightQueue = new LinkedList<>();

            // Find all light-emitting blocks
            for (int x = 0; x < Chunk.CHUNK_SIZE; x++) {
                for (int index = 0; index < Chunk.CHUNK_HEIGHT; index++) {
                    for (int z = 0; z < Chunk.CHUNK_SIZE; z++) {
                        GameBlock block = chunk.getBlockByIndex(x, index, z);
                        int lightLevel = (block != null) ? block.getLightLevel() : 0;

                        if (lightLevel > 0) {
                            int worldY = Settings.indexToWorldY(index);
                            chunk.setBlockLight(x, worldY, z, lightLevel);
                            lightQueue.add(new LightNode(x, worldY, z, lightLevel));
                        }
                    }
                }
            }

            // Propagate light from sources
            propagateLightOptimized(chunk, lightQueue, false);
        });
    }

    /**
     * ⚡ OPTIMIZED: Light propagation with operation limit
     */
    private void propagateLightOptimized(Chunk chunk, Queue<LightNode> queue, boolean isSkylight) {
        int[][] directions = {
                { 1, 0, 0 }, { -1, 0, 0 },
                { 0, 1, 0 }, { 0, -1, 0 },
                { 0, 0, 1 }, { 0, 0, -1 }
        };

        Set<Long> visited = new HashSet<>();
        // ✅ INCREASED LIMIT: 5000 operations to handle deep tunnels/caves better
        int MAX_OPS = 5000;
        int operations = 0;

        while (!queue.isEmpty() && operations < MAX_OPS) {
            LightNode node = queue.poll();
            long key = ((long) node.x << 16) | ((long) (node.y & 0xFFFF) << 8) | (node.z & 0xFF);

            if (visited.contains(key))
                continue;
            visited.add(key);
            operations++;

            int newLight = node.lightLevel - 1;
            if (newLight <= 0)
                continue;

            for (int[] dir : directions) {
                int nx = node.x + dir[0];
                int ny = node.y + dir[1];
                int nz = node.z + dir[2];

                // Check bounds
                if (nx < 0 || nx >= Chunk.CHUNK_SIZE ||
                        !Settings.isValidWorldY(ny) ||
                        nz < 0 || nz >= Chunk.CHUNK_SIZE) {
                    continue;
                }

                long neighborKey = ((long) nx << 16) | ((long) (ny & 0xFFFF) << 8) | (nz & 0xFF);
                if (visited.contains(neighborKey))
                    continue;

                GameBlock neighbor = chunk.getBlock(nx, ny, nz);

                // Skip solid blocks (except leaves which are semi-transparent)
                if (neighbor != null && neighbor.isSolid() && neighbor != BlockRegistry.OAK_LEAVES) {
                    continue;
                }

                int currentLight = isSkylight ? chunk.getSkyLight(nx, ny, nz) : chunk.getBlockLight(nx, ny, nz);

                if (newLight > currentLight) {
                    if (isSkylight) {
                        chunk.setSkyLight(nx, ny, nz, newLight);
                    } else {
                        chunk.setBlockLight(nx, ny, nz, newLight);
                    }
                    queue.add(new LightNode(nx, ny, nz, newLight));
                }
            }
        }
    }

    /**
     * ⚡ Block placement - update lighting
     * 
     * ✅ MINECRAFT-STYLE: This triggers shadow recalculation, NOT time-based update
     */
    public void onBlockPlaced(Chunk chunk, int x, int y, int z, GameBlock block) {
        if (chunk == null || block == null)
            return;

        lightingExecutor.submit(() -> {
            // If solid block placed, it blocks skylight
            if (block.isSolid()) {
                chunk.setSkyLight(x, y, z, 0);
                propagateDarknessDown(chunk, x, y, z);
            }

            // If light-emitting block, propagate its light
            int lightLevel = block.getLightLevel();
            if (lightLevel > 0) {
                chunk.setBlockLight(x, y, z, lightLevel);
                Queue<LightNode> queue = new LinkedList<>();
                queue.add(new LightNode(x, y, z, lightLevel));
                propagateLightOptimized(chunk, queue, false);
            }

            // ✅ Mark for lighting update (NOT geometry rebuild)
            chunk.setNeedsLightingUpdate(true);
        });
    }

    /**
     * ⚡ Block removal - update lighting
     * 
     * ✅ MINECRAFT-STYLE: This triggers shadow recalculation, NOT time-based update
     */
    public void onBlockRemoved(Chunk chunk, int x, int y, int z) {
        if (chunk == null)
            return;

        lightingExecutor.submit(() -> {
            // ✅ FIX: Pull light from neighbors FIRST (including other chunks)
            // This ensures newly created air space has light to propagate
            recalculateSkylightAround(chunk, x, y, z);

            // Recalculate skylight for this column (vertical)
            updateColumnSkylight(chunk, x, z);

            // ✅ NEW: Propagate light horizontally into newly opened space
            propagateSkylightHorizontal(chunk);

            // Clear block light at this position
            chunk.setBlockLight(x, y, z, 0);

            // Recalculate block light from nearby sources
            recalculateBlocklightAround(chunk, x, y, z);

            // ✅ Mark for rebuild
            chunk.setNeedsLightingUpdate(true);
            chunk.setNeedsGeometryRebuild(true);
        });
    }

    /**
     * Propagate darkness downward when a solid block is placed
     */
    private void propagateDarknessDown(Chunk chunk, int x, int startY, int z) {
        for (int y = startY - 1; Settings.isValidWorldY(y); y--) {
            GameBlock block = chunk.getBlock(x, y, z);
            if (block != null && block.isSolid())
                break;

            int currentLight = chunk.getSkyLight(x, y, z);
            if (currentLight == 0)
                break;

            chunk.setSkyLight(x, y, z, 0);
        }
    }

    /**
     * Recalculate block light from nearby light sources
     */
    private void recalculateBlocklightAround(Chunk chunk, int x, int y, int z) {
        Queue<LightNode> queue = new LinkedList<>();

        int[][] directions = {
                { 1, 0, 0 }, { -1, 0, 0 },
                { 0, 1, 0 }, { 0, -1, 0 },
                { 0, 0, 1 }, { 0, 0, -1 }
        };

        for (int[] dir : directions) {
            int nx = x + dir[0];
            int ny = y + dir[1];
            int nz = z + dir[2];

            if (nx < 0 || nx >= Chunk.CHUNK_SIZE ||
                    !Settings.isValidWorldY(ny) ||
                    nz < 0 || nz >= Chunk.CHUNK_SIZE) {
                continue;
            }

            int light = chunk.getBlockLight(nx, ny, nz);
            if (light > 1) {
                queue.add(new LightNode(nx, ny, nz, light));
            }
        }

        propagateLightOptimized(chunk, queue, false);
    }

    /**
     * Recalculate skylight from nearby light sources (including neighbors)
     */
    private void recalculateSkylightAround(Chunk chunk, int x, int y, int z) {
        Queue<LightNode> queue = new LinkedList<>();

        int[][] directions = {
                { 1, 0, 0 }, { -1, 0, 0 },
                { 0, 1, 0 }, { 0, -1, 0 },
                { 0, 0, 1 }, { 0, 0, -1 }
        };

        for (int[] dir : directions) {
            int nx = x + dir[0];
            int ny = y + dir[1];
            int nz = z + dir[2];

            if (!Settings.isValidWorldY(ny))
                continue;

            // Handle neighbor chunks
            int neighborLight = 0;
            if (nx >= 0 && nx < Chunk.CHUNK_SIZE && nz >= 0 && nz < Chunk.CHUNK_SIZE) {
                neighborLight = chunk.getSkyLight(nx, ny, nz);
            } else {
                // Neighbor chunk
                int worldX = chunk.getChunkX() * Chunk.CHUNK_SIZE + nx;
                int worldZ = chunk.getChunkZ() * Chunk.CHUNK_SIZE + nz;
                if (world != null) {
                    neighborLight = world.getSkyLight(worldX, ny, worldZ);
                }
            }

            if (neighborLight > 1) {
                // We found a neighbor with light! Add IT to the queue so it can propagate TO us
                // Note: We add the neighbor as the source
                queue.add(new LightNode(nx, ny, nz, neighborLight));
            }
        }

        if (!queue.isEmpty()) {
            propagateLightOptimized(chunk, queue, true);
        }
    }

    /**
     * ✅ MINECRAFT-STYLE: Get combined light value (skylight × globalSkyLight +
     * blocklight)
     * 
     * This combines:
     * 1. Block's stored sky light (0-15) - never changes with time
     * 2. Global sky light level from TimeOfDay (4 night → 15 day)
     * 3. Block's emitted light (0-15) - never changes
     * 
     * Formula: effectiveSkyLight = (blockSkyLight × globalSkyLight) / 15
     * Final = max(effectiveSkyLight, blockLight)
     * 
     * @param chunk     Chunk containing the block
     * @param x         Local X coordinate
     * @param y         World Y coordinate
     * @param z         Local Z coordinate
     * @param timeOfDay TimeOfDay instance for global sky light level
     * @return Combined light value (0-15)
     */
    public static int getCombinedLight(Chunk chunk, int x, int y, int z, TimeOfDay timeOfDay) {
        if (chunk == null)
            return 15;

        int skyLight = chunk.getSkyLight(x, y, z);
        int blockLight = chunk.getBlockLight(x, y, z);

        // Return max of STORED values (no time scaling here!)
        // Time-based brightness is applied by renderer via glColor4f()
        return Math.max(skyLight, blockLight);
    }

    /**
     * ✅ DEPRECATED: Old version without TimeOfDay (for backward compatibility)
     * Use getCombinedLight(chunk, x, y, z, timeOfDay) instead
     */
    @Deprecated
    public static int getCombinedLight(Chunk chunk, int x, int y, int z) {
        if (chunk == null)
            return 15;

        int skyLight = chunk.getSkyLight(x, y, z);
        int blockLight = chunk.getBlockLight(x, y, z);

        // OLD: Just return max without time scaling
        return Math.max(skyLight, blockLight);
    }

    /**
     * ✅ Convert light value (0-15) to brightness (0.0-1.0)
     * This is STATIC brightness from light level, NOT time-adjusted
     * 
     * Time-of-day adjustment is done in ChunkRenderer via glColor
     */
    public static float getBrightness(int lightLevel) {
        if (lightLevel < 0)
            return BRIGHTNESS_TABLE[0];
        if (lightLevel > 15)
            return BRIGHTNESS_TABLE[15];
        return BRIGHTNESS_TABLE[lightLevel];
    }

    /**
     * ✅ Get brightness with gamma correction
     */
    public static float getBrightnessWithGamma(int lightLevel, float gamma) {
        float brightness = getBrightness(lightLevel);
        return (float) Math.pow(brightness, 1.0f / gamma);
    }

    /**
     * Cancel pending updates for a chunk (when unloading)
     */
    public void cancelChunkUpdates(Chunk chunk) {
        if (chunk == null)
            return;

        pendingLightUpdates.remove(chunk);
        processingChunks.remove(chunk);

        ChunkPosition pos = new ChunkPosition(chunk.getChunkX(), chunk.getChunkZ());
        initializedChunks.remove(pos);
    }

    /**
     * Get number of pending light updates
     */
    public int getPendingUpdatesCount() {
        return pendingLightUpdates.size() + processingChunks.size();
    }

    /**
     * Flush all pending updates (blocking)
     */
    public void flush() {
        while (!pendingLightUpdates.isEmpty() || !processingChunks.isEmpty()) {
            processQueuedChunks();

            // Small sleep to allow async tasks to complete
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    /**
     * Shutdown the lighting engine
     */
    public void shutdown() {
        lightingExecutor.shutdown();
        try {
            if (!lightingExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                lightingExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            lightingExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        pendingLightUpdates.clear();
        processingChunks.clear();
        initializedChunks.clear();

        System.out.println("[LightingEngine] Shutdown complete");
    }

    // ========== DEPRECATED METHODS (kept for compatibility) ==========

    /**
     * @deprecated Time-based skylight updates are no longer used in Minecraft-style
     *             lighting.
     *             Light values are static; time-of-day brightness is applied at
     *             render time.
     */
    @Deprecated
    public void updateSkylightForTimeChange() {
        // ✅ Do nothing - time change is handled by
        // ChunkRenderer.setTimeOfDayBrightness()
        // This method is kept for backward compatibility but should not be called
    }

    /**
     * @deprecated Use initializeSkylightForChunk() without skylightLevel parameter
     */
    @Deprecated
    public void initializeSkylightForChunk(Chunk chunk, int ignoredSkylightLevel, boolean ignored) {
        initializeSkylightForChunk(chunk);
    }
}
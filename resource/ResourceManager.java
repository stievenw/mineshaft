package com.mineshaft.resource;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.InputStreamReader;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages loading of resources (JSON data, textures, etc.).
 */
public class ResourceManager {
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String, BlockData> blockDataMap = new HashMap<>();
    private static final Map<String, EntityData> entityDataMap = new HashMap<>();

    /**
     * Load block data from JSON file.
     * 
     * @param id The block ID (e.g., "mineshaft:stone")
     * @return The loaded BlockData or null if failed
     */
    public static BlockData loadBlockData(String id) {
        if (blockDataMap.containsKey(id)) {
            return blockDataMap.get(id);
        }

        String path = "data/" + id.replace(":", "/") + ".json";
        // e.g., mineshaft:stone -> data/mineshaft/stone.json -> wait, structure is
        // data/mineshaft/blocks/stone.json usually
        // Let's stick to the plan: data/mineshaft/blocks/stone.json

        String[] parts = id.split(":");
        String namespace = parts[0];
        String name = parts[1];
        path = "data/" + namespace + "/blocks/" + name + ".json";

        try (Reader reader = new InputStreamReader(ResourceManager.class.getClassLoader().getResourceAsStream(path))) {
            BlockData data = gson.fromJson(reader, BlockData.class);
            data.id = id; // Ensure ID is set
            blockDataMap.put(id, data);
            System.out.println("✅ Loaded block data: " + id);
            return data;
        } catch (Exception e) {
            System.err.println("❌ Failed to load block data: " + path);
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Load entity data from JSON file.
     * 
     * @param id The entity ID (e.g., "mineshaft:player")
     * @return The loaded EntityData or null if failed
     */
    public static EntityData loadEntityData(String id) {
        if (entityDataMap.containsKey(id)) {
            return entityDataMap.get(id);
        }

        String[] parts = id.split(":");
        String namespace = parts[0];
        String name = parts[1];
        String path = "data/" + namespace + "/entities/" + name + ".json";

        try (Reader reader = new InputStreamReader(ResourceManager.class.getClassLoader().getResourceAsStream(path))) {
            EntityData data = gson.fromJson(reader, EntityData.class);
            data.id = id;
            entityDataMap.put(id, data);
            System.out.println("✅ Loaded entity data: " + id);
            return data;
        } catch (Exception e) {
            System.err.println("❌ Failed to load entity data: " + path);
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Get a texture path from a resource location string.
     * e.g., "mineshaft:block/stone" -> "assets/mineshaft/textures/block/stone.png"
     */
    public static String getTexturePath(String resourceLocation) {
        String[] parts = resourceLocation.split(":");
        String namespace = parts[0];
        String path = parts[1];
        return "assets/" + namespace + "/textures/" + path + ".png";
    }

    public static void cleanup() {
        blockDataMap.clear();
        entityDataMap.clear();
    }
}

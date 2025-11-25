package com.mineshaft.entity;

import com.mineshaft.registry.Identifier;
import com.mineshaft.registry.Registries;

/**
 * All vanilla entity types (like Minecraft's EntityType class)
 * ✅ Template for future entity registration
 */
public class EntityTypes {
    // Future entities
    // public static EntityType<Player> PLAYER;
    // public static EntityType<Zombie> ZOMBIE;
    // public static EntityType<ItemEntity> ITEM;
    
    /**
     * Register all entity types (called during bootstrap)
     */
    public static void init() {
        System.out.println("Registering entity types...");
        
        // Example usage (uncomment when implementing):
        // PLAYER = register("player", 
        //     new EntityType.Builder<>("player", Player::new)
        //         .size(0.6f, 1.8f)
        //         .build()
        // );
    }
    
    /**
     * Helper: Register entity type
     * ✅ Reserved for future entity implementation
     * 
     * @param name The entity type name
     * @param type The entity type instance
     * @return Registered entity type
     */
    @SuppressWarnings({"unchecked", "unused"})
    private static <T extends Entity> EntityType<T> register(String name, EntityType<T> type) {
        return (EntityType<T>) Registries.ENTITY_TYPE.register(Identifier.of(name), type);
    }
}
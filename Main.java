package com.mineshaft;

import com.mineshaft.core.Bootstrap;
import com.mineshaft.core.Game;

/**
 * Mineshaft - Minecraft-style Voxel Engine
 * Entry point
 */
public class Main {
    public static void main(String[] args) {
        System.out.println("═══════════════════════════════════════════════");
        System.out.println("    MINESHAFT - Minecraft-style Engine");
        System.out.println("    Alpha v0.4 - Registry System");
        System.out.println("═══════════════════════════════════════════════");
        
        try {
            // STEP 1: Bootstrap (initialize registries)
            Bootstrap.initialize();
            
            // STEP 2: Start game
            Game game = new Game();
            game.start();
            
        } catch (Exception e) {
            System.err.println("Fatal error occurred:");
            e.printStackTrace();
            System.exit(1);
        }
    }
}
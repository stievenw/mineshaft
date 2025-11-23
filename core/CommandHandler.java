package com.mineshaft.core;

import com.mineshaft.entity.Camera;
import com.mineshaft.render.ChatOverlay;
import com.mineshaft.world.GameMode;
import com.mineshaft.world.World;

/**
 * ✅ Minecraft-style command handler
 */
public class CommandHandler {
    
    private World world;
    private Camera camera;
    private TimeOfDay timeOfDay;
    private ChatOverlay chat;
    
    public CommandHandler(World world, Camera camera, TimeOfDay timeOfDay, ChatOverlay chat) {
        this.world = world;
        this.camera = camera;
        this.timeOfDay = timeOfDay;
        this.chat = chat;
    }
    
    /**
     * ✅ Execute command
     */
    public void executeCommand(String input) {
        if (input == null || input.isEmpty()) return;
        
        // Not a command - just a chat message
        if (!input.startsWith("/")) {
            chat.addMessage("<Player> " + input);
            return;
        }
        
        // Parse command
        String[] parts = input.substring(1).toLowerCase().split(" ");
        String command = parts[0];
        
        try {
            switch (command) {
                case "time":
                    handleTimeCommand(parts);
                    break;
                    
                case "gamemode":
                case "gm":
                    handleGameModeCommand(parts);
                    break;
                    
                case "help":
                    handleHelpCommand();
                    break;
                    
                default:
                    chat.addMessage("§c Unknown command: " + command);
                    chat.addMessage("§7 Type /help for available commands");
            }
        } catch (Exception e) {
            chat.addMessage("§c Error executing command: " + e.getMessage());
        }
    }
    
    /**
     * ✅ /time command handler
     */
    private void handleTimeCommand(String[] args) {
        if (args.length < 2) {
            chat.addMessage("§c Usage: /time <set|add|speed|query> <value>");
            return;
        }
        
        String subCommand = args[1];
        
        switch (subCommand) {
            case "set":
                if (args.length < 3) {
                    chat.addMessage("§c Usage: /time set <value|day|night|noon|midnight|sunrise|sunset>");
                    return;
                }
                
                String setValue = args[2];
                
                switch (setValue) {
                    case "day":
                    case "noon":
                        timeOfDay.setTimeToNoon();
                        chat.addMessage("Time set to noon (6000)");
                        break;
                        
                    case "night":
                    case "midnight":
                        timeOfDay.setTimeToMidnight();
                        chat.addMessage("Time set to midnight (18000)");
                        break;
                        
                    case "sunrise":
                        timeOfDay.setTimeToSunrise();
                        chat.addMessage("Time set to sunrise (23000)");
                        break;
                        
                    case "sunset":
                        timeOfDay.setTimeToSunset();
                        chat.addMessage("Time set to sunset (12000)");
                        break;
                        
                    default:
                        try {
                            long time = Long.parseLong(setValue);
                            timeOfDay.setTimeOfDay(time);
                            chat.addMessage("Time set to " + time);
                        } catch (NumberFormatException e) {
                            chat.addMessage("§c Invalid time value: " + setValue);
                        }
                }
                
                world.updateSkylightForTimeChange();
                break;
                
            case "add":
                if (args.length < 3) {
                    chat.addMessage("§c Usage: /time add <ticks>");
                    return;
                }
                
                try {
                    long add = Long.parseLong(args[2]);
                    timeOfDay.setTimeOfDay(timeOfDay.getWorldTime() + add);
                    chat.addMessage("Added " + add + " ticks to time");
                    world.updateSkylightForTimeChange();
                } catch (NumberFormatException e) {
                    chat.addMessage("§c Invalid number: " + args[2]);
                }
                break;
                
            case "speed":
                if (args.length < 4 || !args[2].equals("set")) {
                    chat.addMessage("§c Usage: /time speed set <multiplier>");
                    return;
                }
                
                try {
                    float speed = Float.parseFloat(args[3]);
                    timeOfDay.setDaySpeed(speed);
                    
                    if (speed == 0) {
                        chat.addMessage("Time cycle paused");
                    } else if (speed == 1) {
                        chat.addMessage("Time cycle set to normal speed");
                    } else {
                        chat.addMessage("Time cycle speed set to " + speed + "x");
                    }
                } catch (NumberFormatException e) {
                    chat.addMessage("§c Invalid speed: " + args[3]);
                }
                break;
                
            case "query":
                long time = timeOfDay.getTimeOfDay();
                long day = timeOfDay.getDayCount();
                chat.addMessage("Day " + day + ", Time: " + time + " (" + timeOfDay.getTimePhase() + ")");
                chat.addMessage("Moon Phase: " + timeOfDay.getMoonPhaseName());
                chat.addMessage("Skylight Level: " + timeOfDay.getSkylightLevel());
                break;
                
            default:
                chat.addMessage("§c Unknown time subcommand: " + subCommand);
                chat.addMessage("§7 Use: set, add, speed, or query");
        }
    }
    
    /**
     * ✅ /gamemode command handler - SURVIVAL, CREATIVE, SPECTATOR
     */
    private void handleGameModeCommand(String[] args) {
        if (args.length < 2) {
            chat.addMessage("§c Usage: /gamemode <survival|creative|spectator>");
            return;
        }
        
        String mode = args[1].toLowerCase();
        GameMode newMode = null;
        
        // Parse gamemode - HANYA 3 MODE: SURVIVAL, CREATIVE, SPECTATOR
        switch (mode) {
            case "survival":
            case "s":
            case "0":
                newMode = GameMode.SURVIVAL;
                break;
                
            case "creative":
            case "c":
            case "1":
                newMode = GameMode.CREATIVE;
                break;
                
            case "spectator":
            case "sp":
            case "3":
            case "2":  // Support both 2 and 3 for spectator
                newMode = GameMode.SPECTATOR;
                break;
                
            default:
                chat.addMessage("§c Unknown game mode: " + mode);
                chat.addMessage("§7 Available: survival (0), creative (1), spectator (2)");
                return;
        }
        
        camera.setGameMode(newMode);
        chat.addMessage("Game mode set to " + newMode.getName());
    }
    
    /**
     * ✅ /help command
     */
    private void handleHelpCommand() {
        chat.addMessage("§e=== Available Commands ===");
        chat.addMessage("§a/time set <value|day|night|noon|midnight|sunrise|sunset>");
        chat.addMessage("§7  Set the time of day");
        chat.addMessage("§a/time add <ticks>");
        chat.addMessage("§7  Add ticks to current time");
        chat.addMessage("§a/time speed set <multiplier>");
        chat.addMessage("§7  Set time cycle speed (0 = paused, 1 = normal)");
        chat.addMessage("§a/time query");
        chat.addMessage("§7  Display current time info");
        chat.addMessage("§a/gamemode <survival|creative|spectator>");
        chat.addMessage("§7  Change game mode (shortcuts: s, c, sp or 0-2)");
        chat.addMessage("§a/help");
        chat.addMessage("§7  Show this help message");
    }
}
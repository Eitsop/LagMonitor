package uk.ravenflight.lagmonitor;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.vehicle.VehicleCreateEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.NonNull;

import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class Lagmonitor extends JavaPlugin implements Listener {

    private boolean pluginEnabled;
    private int maxBoats;
    private int maxArmor;
    private int maxMinecarts;
    private int scanInterval;
    private int scanXSize;
    private int scanZSize;
    private int scanYSize;
    private boolean reportToAdmins;
    private boolean reportBlocksToAdmins;

    // Per-block limits
    private boolean perBlockLimitEnabled;
    private int maxBoatsPerBlock;
    private int maxArmorPerBlock;
    private int maxMinecartsPerBlock;
    private String scanAction;
    private boolean useDynamicRadius;
    private double dynamicRadiusMultiplier;

    private static final MiniMessage MM = MiniMessage.miniMessage();

    @Override
    public void onEnable() {
        this.saveDefaultConfig();
        loadConfigValues();

        boolean vdtPresent = Bukkit.getPluginManager().isPluginEnabled("ViewDistanceTweaks");
        if (vdtPresent) {
            getLogger().info("ViewDistanceTweaks detected. Dynamic scan radius enabled.");
        }

        Bukkit.getPluginManager().registerEvents(this, this);

        if( this.getCommand("lagmonitor") != null ) {
            Objects.requireNonNull(this.getCommand("lagmonitor")).setExecutor(this);
            Objects.requireNonNull(this.getCommand("lagmonitor")).setTabCompleter(this);
        } else {
            getLogger().info("Lagmonitor commands are disabled.");
        }

        Bukkit.getScheduler().runTaskTimer(this, this::scanAndCram, 100L, this.scanInterval);

        if (this.pluginEnabled) {
            getLogger().info("Lagmonitor enabled. Protecting the world from boat-mageddon.");
        } else {
            getLogger().info("Lagmonitor is currently DISABLED via config.");
        }
    }

    private void loadConfigValues() {
        this.pluginEnabled = getConfig().getBoolean("lagmonitor-enabled", true);
        this.maxBoats = getConfig().getInt("lag-max-boats-per-scan", 20);
        this.maxArmor = getConfig().getInt("lag-max-armor-per-scan", 50);
        this.maxMinecarts = getConfig().getInt("lag-max-minecarts-per-scan", 20);
        this.scanInterval = getConfig().getInt("lag-scan-interval-ticks", 100);
        this.scanXSize = getConfig().getInt("lag-scan-x-size", 16);
        this.scanZSize = getConfig().getInt("lag-scan-z-size", 16);
        this.scanYSize = getConfig().getInt("lag-scan-y-size", 8);
        this.reportToAdmins = getConfig().getBoolean("lag-report-to-admins", true);
        this.reportBlocksToAdmins = getConfig().getBoolean("lag-report-blocks-to-admins", true);

        this.perBlockLimitEnabled = getConfig().getBoolean("lag-max-per-block-limit-enabled", true);
        this.maxBoatsPerBlock = getConfig().getInt("lag-max-boats-per-block", 5);
        this.maxArmorPerBlock = getConfig().getInt("lag-max-armor-per-block", 5);
        this.maxMinecartsPerBlock = getConfig().getInt("lag-max-minecarts-per-block", 5);
        this.scanAction = getConfig().getString("lag-scan-action", "REMOVE").toUpperCase();
        this.useDynamicRadius = getConfig().getBoolean("lag-use-dynamic-scan-radius", true);
        this.dynamicRadiusMultiplier = getConfig().getDouble("lag-dynamic-radius-multiplier", 1.0);
    }

    /**
     * Proactive: Prevents players/dispensers from adding vehicles to a crowded chunk.
     */
    @EventHandler
    public void onVehicleCreate(VehicleCreateEvent event) {
        if (!this.pluginEnabled) return;
        Entity vehicle = event.getVehicle();
        if (vehicle instanceof Boat) {
            if (countEntitiesInChunk(vehicle.getLocation().getChunk(), Boat.class) >= this.maxBoats) {
                event.setCancelled(true);
                handleProactiveBlock(vehicle, "boat (chunk limit)");
            } else if (this.perBlockLimitEnabled && countEntitiesAtLocation(vehicle.getLocation(), Boat.class) >= this.maxBoatsPerBlock) {
                event.setCancelled(true);
                handleProactiveBlock(vehicle, "boat (block limit)");
            }
        } else if (vehicle instanceof Minecart) {
            if (countEntitiesInChunk(vehicle.getLocation().getChunk(), Minecart.class) >= this.maxMinecarts) {
                event.setCancelled(true);
                handleProactiveBlock(vehicle, "minecart (chunk limit)");
            } else if (this.perBlockLimitEnabled && countEntitiesAtLocation(vehicle.getLocation(), Minecart.class) >= this.maxMinecartsPerBlock) {
                event.setCancelled(true);
                handleProactiveBlock(vehicle, "minecart (block limit)");
            }
        }
    }

    /**
     * Proactive: Prevents players/dispensers from adding armor stands to a crowded chunk.
     */
    @EventHandler
    public void onArmorStandSpawn(CreatureSpawnEvent event) {
        if (!this.pluginEnabled) return;
        if (event.getEntity() instanceof ArmorStand) {
            if (countEntitiesInChunk(event.getEntity().getLocation().getChunk(), ArmorStand.class) >= this.maxArmor) {
                event.setCancelled(true);
                handleProactiveBlock(event.getEntity(), "armor stand (chunk limit)");
            } else if (this.perBlockLimitEnabled && countEntitiesAtLocation(event.getEntity().getLocation(), ArmorStand.class) >= this.maxArmorPerBlock) {
                event.setCancelled(true);
                handleProactiveBlock(event.getEntity(), "armor stand (block limit)");
            }
        }
    }

    private void handleProactiveBlock(Entity entity, String type) {
        if (this.reportBlocksToAdmins) {
            String location = entity.getWorld().getName() + " " + entity.getLocation().getBlockX() + " " + entity.getLocation().getBlockY() + " " + entity.getLocation().getBlockZ();
            notifyAdmins("Blocked " + type + " placement at " + location);
        }
    }

    private int countEntitiesInChunk(Chunk chunk, Class<? extends Entity> clazz) {
        int count = 0;
        for (Entity entity : chunk.getEntities()) {
            if (clazz.isInstance(entity)) count++;
        }
        return count;
    }

    private int countEntitiesAtLocation(org.bukkit.Location loc, Class<? extends Entity> clazz) {
        // Optimization: Use predicate filtering in getNearbyEntities
        Collection<Entity> nearby = loc.getWorld().getNearbyEntities(loc, 0.5, 0.5, 0.5, clazz::isInstance);
        return nearby.size();
    }

    private long getBlockKey(int x, int y, int z) {
        return ((long) x & 0x3FFFFFFL) << 38 | ((long) z & 0x3FFFFFFL) << 12 | ((long) y & 0xFFFL);
    }

    /**
     * Reactive: Scans loaded chunks and "crams" entities if they exceed the limit.
     */
    private void scanAndCram() {
        if (!this.pluginEnabled) return;
        performEntityScan(null, false);
    }

    private void performEntityScan(CommandSender sender, boolean forceRemove) {
        int totalBoatsRemoved = 0;
        int totalArmorRemoved = 0;
        int totalMinecartsRemoved = 0;

        boolean shouldRemove = forceRemove || this.scanAction.equals("REMOVE");

        Set<Entity> processedEntities = new HashSet<>();
        
        // Optimization: Global maps to track across all players and reduce allocations
        Map<Long, Integer> blockBoats = new HashMap<>();
        Map<Long, Integer> blockArmor = new HashMap<>();
        Map<Long, Integer> blockMinecarts = new HashMap<>();
        
        // Optimization: Reuse location object to reduce GC pressure
        org.bukkit.Location locHelper = new org.bukkit.Location(null, 0, 0, 0);

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (sender != null) {
                sender.sendMessage(MM.deserialize("<gold>Checking " + player.getName()));
            }

            int currentScanX = this.scanXSize;
            int currentScanZ = this.scanZSize;

            if (this.useDynamicRadius) {
                // Simulation distance is in chunks. 1 chunk = 16 blocks.
                int simDistance = player.getWorld().getSimulationDistance();
                currentScanX = (int) (simDistance * 16 * this.dynamicRadiusMultiplier);
                currentScanZ = currentScanX; // Keep it square
            }

            // Optimization: Use predicate to let the engine filter by type
            Collection<Entity> nearby = player.getWorld().getNearbyEntities(
                player.getLocation(), 
                currentScanX, this.scanYSize, currentScanZ,
                entity -> entity instanceof Boat || entity instanceof ArmorStand || entity instanceof Minecart
            );

            int boatCount = 0;
            int armorCount = 0;
            int minecartCount = 0;

            for (Entity entity : nearby) {
                if (!processedEntities.add(entity)) continue;

                entity.getLocation(locHelper);
                long blockKey = getBlockKey(locHelper.getBlockX(), locHelper.getBlockY(), locHelper.getBlockZ());

                switch (entity) {
                    case Boat _ -> {
                        boatCount++;
                        int inBlock = blockBoats.getOrDefault(blockKey, 0) + 1;
                        blockBoats.put(blockKey, inBlock);

                        if (boatCount > this.maxBoats || (this.perBlockLimitEnabled && inBlock > this.maxBoatsPerBlock)) {
                            if (shouldRemove) {
                                entity.remove();
                                totalBoatsRemoved++;
                            }
                        }
                    }
                    case ArmorStand _ -> {
                        armorCount++;
                        int inBlock = blockArmor.getOrDefault(blockKey, 0) + 1;
                        blockArmor.put(blockKey, inBlock);

                        if (armorCount > this.maxArmor || (this.perBlockLimitEnabled && inBlock > this.maxArmorPerBlock)) {
                            if (shouldRemove) {
                                entity.remove();
                                totalArmorRemoved++;
                            }
                        }
                    }
                    case Minecart _ -> {
                        minecartCount++;
                        int inBlock = blockMinecarts.getOrDefault(blockKey, 0) + 1;
                        blockMinecarts.put(blockKey, inBlock);

                        if (minecartCount > this.maxMinecarts || (this.perBlockLimitEnabled && inBlock > this.maxMinecartsPerBlock)) {
                            if (shouldRemove) {
                                entity.remove();
                                totalMinecartsRemoved++;
                            }
                        }
                    }
                    default -> {
                    }
                }
            }

            reportStatusIfNeeded(sender, player, this.maxBoats, boatCount, "boat", shouldRemove);
            reportStatusIfNeeded(sender, player, this.maxArmor, armorCount, "armor", shouldRemove);
            reportStatusIfNeeded(sender, player, this.maxMinecarts, minecartCount, "minecart", shouldRemove);
        }

        if (sender != null) {
            String actionVerb = shouldRemove ? "removed" : "detected";
            sender.sendMessage(MM.deserialize("<green>Scan complete. " + totalBoatsRemoved + " boats, " + totalArmorRemoved + " armor stands, and " + totalMinecartsRemoved + " minecarts " + actionVerb + "."));
        }
    }

    private void reportStatusIfNeeded(CommandSender sender, Player player, int maximumAllowed, int entityCount, String itemTypeName, boolean removed) {
        if (entityCount > maximumAllowed) {
            String tpFormat = player.getWorld().getName() + " " + player.getLocation().getBlockX() + " " + player.getLocation().getBlockY() + " " + player.getLocation().getBlockZ();
            int entitiesExcess = entityCount - maximumAllowed;
            String actionVerb = removed ? "Removed" : "Detected";
            String fullReport = "[Lagmonitor] " + actionVerb + " " + entitiesExcess + " excess " + itemTypeName + "(s) near " + player.getName() + " at " + tpFormat;

            getLogger().log(Level.WARNING, fullReport);
            if (this.reportToAdmins) {
                notifyAdmins(fullReport);
            } else if (sender != null) {
                sender.sendMessage(MM.deserialize("<red>" + fullReport));
            }
        }
    }

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, String[] args) {

        boolean senderHasAnyPermission = sender.hasPermission("lagmonitor.admin")
                || sender.hasPermission("lagmonitor.reload")
                || sender.hasPermission("lagmonitor.scan")
                || sender.hasPermission("lagmonitor.clear");

        if (args.length == 0) {
            if (senderHasAnyPermission) {
                sender.sendMessage(MM.deserialize("<yellow>Usage: /lagmonitor <reload|scan|clear>"));
            } else {
                sender.sendMessage(MM.deserialize("<red>No permission!"));
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("lagmonitor.reload")) {
                sender.sendMessage(MM.deserialize("<red>No permission!"));
                return true;
            }
            this.reloadConfig();
            loadConfigValues();
            sender.sendMessage(MM.deserialize("<green>Configuration reloaded!"));
        } else if (args[0].equalsIgnoreCase("scan")) {
            if (!sender.hasPermission("lagmonitor.scan")) {
                sender.sendMessage(MM.deserialize("<red>No permission!"));
                return true;
            }
            sender.sendMessage(MM.deserialize("<aqua>Manually starting entity density scan..."));
            performEntityScan(sender, false);
        } else if (args[0].equalsIgnoreCase("clear")) {
            if (!sender.hasPermission("lagmonitor.clear")) {
                sender.sendMessage(MM.deserialize("<red>No permission!"));
                return true;
            }
            sender.sendMessage(MM.deserialize("<red>Manually starting entity density scan and CLEAR..."));
            performEntityScan(sender, true);
        } else {
            if (senderHasAnyPermission) {
                sender.sendMessage(MM.deserialize("<yellow>Usage: /lagmonitor <reload|scan|clear>"));
            } else {
                sender.sendMessage(MM.deserialize("<red>No permission!"));
            }
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(@NonNull CommandSender sender, @NonNull Command command, @NonNull String alias, String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            if (sender.hasPermission("lagmonitor.reload")) {
                completions.add("reload");
            }
            if (sender.hasPermission("lagmonitor.scan")) {
                completions.add("scan");
            }
            if(sender.hasPermission("lagmonitor.clear")) {
                completions.add("clear");
            }

            return completions.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }

    private void notifyAdmins(String message) {
        getLogger().info(message);
        Component component = MM.deserialize("<red>[LagMonitor] <yellow>" + message);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("lagmonitor.notify")) {
                player.sendMessage(component);
            }
        }
    }
}

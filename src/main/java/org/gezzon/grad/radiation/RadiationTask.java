package org.gezzon.grad.radiation;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.gezzon.grad.Grad;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class RadiationTask extends BukkitRunnable {

    private final RadiationManager manager;
    private final Map<UUID, Double> damageTimers = new HashMap<>();
    private final Map<UUID, Integer> regionCache = new HashMap<>();
    private final Map<UUID, Long> regionCacheTimestamps = new HashMap<>();
    private static final long REGION_CACHE_LIFETIME = 1000L; // 1 секунда

    public RadiationTask(RadiationManager manager) {
        this.manager = manager;
    }

    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();

            if (manager.isGodMode(uuid)) {
                manager.setPlayerRadiation(uuid, 0.0);
                continue;
            }

            double newRadiation = calculateRadiationForPlayer(player);
            double currentRad = manager.getPlayerRadiation(uuid);
            double updatedRad = currentRad + newRadiation;
            manager.setPlayerRadiation(uuid, updatedRad);

            double damage = 0.0;
            double damageInterval = Double.MAX_VALUE;

            for (int level = 5; level >= 1; level--) {
                Map<String, Double> data = manager.getLevelData(level);
                if (data == null) continue;

                double ds = data.get("damage_start");
                if (updatedRad >= ds) {
                    damage = data.get("damage_amount");
                    damageInterval = data.get("damage_interval");
                    break;
                }
            }

            if (damage > 0) {
                double timeLeft = damageTimers.getOrDefault(uuid, 0.0);
                if (timeLeft <= 0) {
                    player.damage(damage);
                    damageTimers.put(uuid, damageInterval);
                } else {
                    damageTimers.put(uuid, timeLeft - 1);
                }
            } else {
                damageTimers.put(uuid, 0.0);
            }
        }
    }

    private double calculateRadiationForPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        Location loc = player.getLocation();
        double totalRadiation = 0.0;

        for (RadiationSource source : manager.getAllSources()) {
            if (!source.getCenter().getWorld().equals(loc.getWorld())) {
                continue;
            }

            double distanceSquared = source.getCenter().distanceSquared(loc);
            double radiusSquared = Math.pow(source.getRadius(), 2);
            if (distanceSquared <= radiusSquared) {
                Map<String, Double> data = manager.getLevelData(source.getIntensity());
                if (data != null) {
                    double baseAcc = data.get("base_accumulation");
                    totalRadiation += baseAcc * calculateDistanceFactor(distanceSquared, radiusSquared);
                }
            }
        }

        int radiationLevel = getRadiationLevel(player);
        if (radiationLevel > 0) {
            Map<String, Double> data = manager.getLevelData(radiationLevel);
            if (data != null) {
                totalRadiation += data.get("base_accumulation");
            }
        }

        totalRadiation *= (1.0 - calculateArmorProtection(player));
        return totalRadiation;
    }

    private double calculateDistanceFactor(double distanceSquared, double radiusSquared) {
        double normalizedDistance = Math.sqrt(distanceSquared) / Math.sqrt(radiusSquared);
        return Math.max(0, 1 - normalizedDistance);
    }

    public int getRadiationLevel(Player player) {
        UUID playerId = player.getUniqueId();
        long currentTime = System.currentTimeMillis();

        if (regionCache.containsKey(playerId) && (currentTime - regionCacheTimestamps.get(playerId)) < REGION_CACHE_LIFETIME) {
            return regionCache.get(playerId);
        }

        int radiationLevel = calculateRadiationLevel(player);
        regionCache.put(playerId, radiationLevel);
        regionCacheTimestamps.put(playerId, currentTime);

        return radiationLevel;
    }

    private int calculateRadiationLevel(Player player) {
        Location loc = player.getLocation();
        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        RegionManager regionManager = container.get(BukkitAdapter.adapt(loc.getWorld()));

        if (regionManager != null) {
            ApplicableRegionSet regions = regionManager.getApplicableRegions(BukkitAdapter.asBlockVector(loc));
            for (ProtectedRegion region : regions) {
                Integer radiationLevel = region.getFlag(Grad.RADIATION_FLAG);
                if (radiationLevel != null) {
                    return radiationLevel;
                }
            }
        }
        return 0;
    }

    private double calculateArmorProtection(Player player) {
        String keyBase = "protect_radiation_";
        double protection = 0.0;

        for (ItemStack piece : player.getEquipment().getArmorContents()) {
            if (piece == null) continue;

            ItemMeta meta = piece.getItemMeta();
            if (meta == null) continue;

            NamespacedKey key = new NamespacedKey(Grad.getInstance(), "radiation_level");
            String storedValue = meta.getPersistentDataContainer().get(key, PersistentDataType.STRING);

            for (int level = 1; level <= 5; level++) {
                if (storedValue != null && storedValue.equalsIgnoreCase(keyBase + level)) {
                    protection += 0.25;
                }
            }
        }

        return Math.min(protection, 1.0);
    }
}

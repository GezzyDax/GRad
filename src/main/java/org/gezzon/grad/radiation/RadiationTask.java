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

/**
 * Задача для регулярного расчета радиации и её влияния на игроков.
 */
public class RadiationTask extends BukkitRunnable {

    private final RadiationManager manager; // Менеджер радиации для управления источниками и уровнями
    private final Map<UUID, CachedRegionData> playerRegionCache = new HashMap<>(); // Кэш данных по регионам
    private static final long REGION_CACHE_LIFETIME = 1000L; // Время жизни кэша (1 секунда)

    public RadiationTask(RadiationManager manager) {
        this.manager = manager;
    }

    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();

            // Пропускаем игроков с включённым "бог-режимом"
            if (manager.isGodMode(uuid)) {
                manager.setPlayerRadiation(uuid, 0.0); // Сбрасываем радиацию
                continue;
            }

            // Рассчитываем новый уровень радиации
            double newRadiation = calculateRadiationForPlayer(player);
            double currentRad = manager.getPlayerRadiation(uuid);
            double updatedRad = currentRad + newRadiation;
            manager.setPlayerRadiation(uuid, updatedRad); // Обновляем радиацию игрока

            double damage = 0.0;
            double damageInterval = Double.MAX_VALUE;

            // Определяем, вызывает ли радиация урон
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

            // Если радиация наносит урон
            if (damage > 0) {
                CachedRegionData cachedData = playerRegionCache.getOrDefault(uuid, new CachedRegionData(0, 0, player.getLocation(), 0));
                double timeLeft = cachedData.damageTimer;
                if (timeLeft <= 0) {
                    player.damage(damage); // Наносим урон игроку
                    playerRegionCache.put(uuid, new CachedRegionData(damageInterval, System.currentTimeMillis(), player.getLocation(), cachedData.radiationLevel));
                } else {
                    cachedData.damageTimer -= 1; // Уменьшаем таймер до следующего урона
                    playerRegionCache.put(uuid, cachedData); // Обновляем кэш
                }
            }

        }
    }

    /**
     * Рассчитывает общий уровень радиации для игрока.
     */
    private double calculateRadiationForPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        Location loc = player.getLocation();
        double totalRadiation = 0.0;

        // Проверка радиации от всех источников
        for (RadiationSource source : manager.getAllSources()) {
            if (!source.getCenter().getWorld().equals(loc.getWorld())) {
                continue; // Пропускаем источники из другого мира
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

        // Проверка радиационного уровня в регионе
        int radiationLevel = getRadiationLevel(player);
        if (radiationLevel > 0) {
            Map<String, Double> data = manager.getLevelData(radiationLevel);
            if (data != null) {
                totalRadiation += data.get("base_accumulation");
            }
        }

        // Учитываем защиту от брони
        totalRadiation *= (1.0 - calculateArmorProtection(player, getRadiationLevel(player)));
        return totalRadiation;
    }

    /**
     * Рассчитывает влияние расстояния на радиацию.
     */
    private double calculateDistanceFactor(double distanceSquared, double radiusSquared) {
        double normalizedDistance = Math.sqrt(distanceSquared) / Math.sqrt(radiusSquared);
        return Math.max(0, 1 - normalizedDistance);
    }

    /**
     * Получает радиационный уровень региона, в котором находится игрок.
     */
    public int getRadiationLevel(Player player) {
        UUID playerId = player.getUniqueId();
        Location loc = player.getLocation();
        CachedRegionData cache = playerRegionCache.get(playerId);

        // Если кэш устарел или позиция изменилась
        if (cache == null || cache.timestamp + REGION_CACHE_LIFETIME < System.currentTimeMillis() || !cache.lastLocation.equals(loc)) {
            int level = calculateRadiationLevel(player);
            playerRegionCache.put(playerId, new CachedRegionData(0, System.currentTimeMillis(), loc, level));
            return level;
        }

        return cache.radiationLevel; // Возвращаем значение из кэша
    }

    /**
     * Определяет радиационный уровень региона.
     */
    private int calculateRadiationLevel(Player player) {
        Location loc = player.getLocation();
        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        RegionManager regionManager = container.get(BukkitAdapter.adapt(loc.getWorld()));

        if (regionManager != null) {
            ApplicableRegionSet regions = regionManager.getApplicableRegions(BukkitAdapter.asBlockVector(loc));
            for (ProtectedRegion region : regions) {
                Integer radiationLevel = region.getFlag(Grad.RADIATION_FLAG);
                if (radiationLevel != null) {
                    return radiationLevel; // Возвращаем уровень радиации региона
                }
            }
        }

        return 0; // Если регионов с радиацией нет
    }

    /**
     * Рассчитывает защиту игрока от радиации на основе брони.
     */
    private double calculateArmorProtection(Player player, int radiationLevel) {
        double protection = 0.0;

        for (ItemStack piece : player.getEquipment().getArmorContents()) {
            if (piece == null) continue;

            // Проверяем наличие зачарования Radiation Protection
            if (piece.containsEnchantment(Grad.getRadiationProtectionEnchantment())) {
                int level = piece.getEnchantmentLevel(Grad.getRadiationProtectionEnchantment());
                protection += level * 0.25; // Каждое зачарование добавляет 25% защиты за уровень

                // Если уровень зачарования выше или равен текущему уровню радиации, игрок игнорирует эффект
                if (level >= radiationLevel) {
                    return 1.0; // Полная защита от радиации данного уровня
                }
            }
        }

        return Math.min(protection, 1.0); // Ограничиваем защиту максимум 100%
    }

    /**
     * Вспомогательный класс для хранения данных кэша.
     */
    private static class CachedRegionData {
        double damageTimer; // Таймер для урона от радиации
        long timestamp; // Время последнего обновления данных
        Location lastLocation; // Последняя локация игрока
        int radiationLevel; // Уровень радиации в регионе

        CachedRegionData(double damageTimer, long timestamp, Location lastLocation, int radiationLevel) {
            this.damageTimer = damageTimer;
            this.timestamp = timestamp;
            this.lastLocation = lastLocation;
            this.radiationLevel = radiationLevel;
        }
    }
}

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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Периодическая задача, которая:
 *  1) Каждую секунду начисляет игрокам "порцию" радиации,
 *  2) При достижении определённых значений радиации наносит урон,
 *     с интервалом, зависящим от уровня радиации.
 */
public class RadiationTask extends BukkitRunnable {

    private final RadiationManager manager;

    // Отслеживаем индивидуальные таймеры урона для каждого игрока:
    //   UUID -> время до следующего удара (в секундах)
    private final Map<UUID, Double> damageTimers = new HashMap<>();

    public RadiationTask(RadiationManager manager) {
        this.manager = manager;
    }

    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();

            // Если "бог-режим" включён — обнуляем радиацию и пропускаем урон
            if (manager.isGodMode(uuid)) {
                manager.setPlayerRadiation(uuid, 0.0);
                continue;
            }

            // 1) Считаем, сколько радиации игрок набирает за эту "тик" (1 секунду)
            double newRadiation = calculateRadiationForPlayer(player);
            double currentRad = manager.getPlayerRadiation(uuid);
            double updatedRad = currentRad + newRadiation;
            manager.setPlayerRadiation(uuid, updatedRad);

            // 2) Определяем, какой уровень радиации сейчас "активен"
            //    (то есть, updatedRad >= damage_start конкретного уровня)
            double damage = 0.0;
            double damageInterval = Double.MAX_VALUE; // если не найден уровень, урон = 0

            // Перебираем уровни с 5 по 1 (сверху вниз), чтобы найти максимальный,
            // под который подпадает текущий updatedRad
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

            // 3) Наносим урон, если damage > 0, но с учётом damageInterval
            if (damage > 0) {
                double timeLeft = damageTimers.getOrDefault(uuid, 0.0);
                if (timeLeft <= 0) {
                    // Наступило время нанести урон
                    player.damage(damage);
                    // Сбрасываем таймер
                    damageTimers.put(uuid, damageInterval);
                } else {
                    // Уменьшаем таймер до следующего удара
                    damageTimers.put(uuid, timeLeft - 1);
                }
            } else {
                // Если урона быть не должно, сбрасываем таймер
                damageTimers.put(uuid, 0.0);
            }
        }
    }

    /**
     * Расчёт, сколько радиации игрок получит за 1 секунду:
     *  - Суммируем вклад от всех сферических источников;
     *  - Суммируем вклад от WorldGuard-регионов (radiation_1..radiation_5),
     *    берём максимальный уровень в точке.
     */
    private double calculateRadiationForPlayer(Player player) {
        Location loc = player.getLocation();
        double total = 0.0;

        // 1) Все сферические источники:
        for (RadiationSource source : manager.getAllSources()) {
            if (!source.getCenter().getWorld().equals(loc.getWorld())) {
                continue;
            }
            double distance = source.getCenter().distance(loc);
            if (distance <= source.getRadius()) {
                // Определяем "часть" зоны, делим радиус на 5 равных сегментов
                double segment = source.getRadius() / 5.0;
                int part = (int) Math.ceil(distance / segment);
                if (part < 1) part = 1;
                if (part > 5) part = 5;

                // Читаем base_accumulation для уровня source.getIntensity()
                Map<String, Double> data = manager.getLevelData(source.getIntensity());
                if (data != null) {
                    double baseAcc = data.get("base_accumulation");
                    // Формула: baseAcc * power * (1.5^(part - 1))
                    double localAccum = baseAcc * source.getPower() * (Math.pow(1.5, part - 1));
                    total += localAccum;
                }
            }
        }

        // 2) WorldGuard-флаги radiation_1..radiation_5
        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        RegionManager regionManager = container.get(BukkitAdapter.adapt(loc.getWorld()));
        int wgMaxLevel = 0;

        if (regionManager != null) {
            ApplicableRegionSet regions = regionManager.getApplicableRegions(BukkitAdapter.asBlockVector(loc));
            for (ProtectedRegion region : regions) {
                String regionId = region.getId().toLowerCase();
                // "Костыльный" способ: если в имени региона есть radiation_1..5
                for (int i = 1; i <= 5; i++) {
                    if (regionId.contains("radiation_" + i)) {
                        wgMaxLevel = Math.max(wgMaxLevel, i);
                    }
                }
            }
        }

        // Если есть WG-зона с флагом radiation_X
        if (wgMaxLevel > 0) {
            Map<String, Double> data = manager.getLevelData(wgMaxLevel);
            if (data != null) {
                double baseAcc = data.get("base_accumulation");
                // Накопление без деления на части (т.е. зона целиком)
                total += baseAcc;
            }
        }

        return total;
    }
}

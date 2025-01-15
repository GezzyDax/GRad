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
import com.sk89q.worldguard.protection.flags.StateFlag;

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
    /**
     * Проверяет, защищён ли игрок от конкретного уровня радиации (1..5).
     *
     * Например, если level = 2, то ищем тег protect_rad_2
     * на любом предмете брони игрока.
     */
    private boolean isPlayerProtectedFromLevel(Player player, int level) {
        // Собираем нужный ключ
        String desiredTag = "protect_rad_" + level;

        // Проверяем весь сет брони (шлем, нагрудник, поножи, ботинки)
        ItemStack[] armor = player.getEquipment().getArmorContents();
        if (armor == null) return false;

        for (ItemStack piece : armor) {
            if (piece == null) continue;
            ItemMeta meta = piece.getItemMeta();
            if (meta == null) continue;

            // Проверяем PersistentDataContainer
            if (meta.getPersistentDataContainer() != null) {
                // Создаём NamespacedKey (обычно по вашему плагину)
                NamespacedKey key = new NamespacedKey(Grad.getInstance(), "protect_rad_level");

                // Считаем сохранённое значение (например, строку)
                String storedValue = meta.getPersistentDataContainer().get(key, PersistentDataType.STRING);
                if (storedValue != null && storedValue.equalsIgnoreCase(desiredTag)) {
                    // Нашли нужный тег
                    return true;
                }
            }
        }
        return false;
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
        double totalRadiation = 0.0;

        // 1) Считаем вклад от сферических источников радиации
        for (RadiationSource source : manager.getAllSources()) {
            if (!source.getCenter().getWorld().equals(loc.getWorld())) {
                continue;
            }
            double distance = source.getCenter().distance(loc);
            if (distance <= source.getRadius()) {
                double segment = source.getRadius() / 5.0;
                int part = (int) Math.ceil(distance / segment);
                if (part < 1) part = 1;
                if (part > 5) part = 5;

                Map<String, Double> data = manager.getLevelData(source.getIntensity());
                if (data != null) {
                    double baseAcc = data.get("base_accumulation");
                    double localAccum = baseAcc * source.getPower() * Math.pow(1.5, part - 1);
                    totalRadiation += localAccum;
                }
            }
        }

        // 2) Считаем вклад от WorldGuard-флагов
        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        RegionManager regionManager = container.get(BukkitAdapter.adapt(loc.getWorld()));
        int wgMaxLevel = 0;

        if (regionManager != null) {
            ApplicableRegionSet regions = regionManager.getApplicableRegions(BukkitAdapter.asBlockVector(loc));
            for (ProtectedRegion region : regions) {
                for (int i = 1; i <= 5; i++) {
                    if (region.getFlag(new StateFlag("radiation_" + i, false)) == StateFlag.State.ALLOW) {
                        wgMaxLevel = Math.max(wgMaxLevel, i);
                    }
                }
            }
        }

        if (wgMaxLevel > 0) {
            Map<String, Double> data = manager.getLevelData(wgMaxLevel);
            if (data != null) {
                double baseAcc = data.get("base_accumulation");
                totalRadiation += baseAcc;
            }
        }

        // 3) Учитываем защиту от брони
        for (int level = 5; level >= 1; level--) {
            double armorProtection = calculateArmorProtection(player, level);
            if (armorProtection > 0) {
                // Уменьшаем радиацию на процент защиты
                totalRadiation *= (1.0 - armorProtection);
            }
        }

        return totalRadiation;
    }

    /**
     * Проверяет, насколько броня игрока защищает от заданного уровня радиации (1..5).
     *
     * @param player Игрок, чья броня проверяется.
     * @param level  Уровень радиации (1..5), от которого проверяется защита.
     * @return Доля защиты (от 0.0 до 1.0), где 1.0 — полная защита.
     */
    private double calculateArmorProtection(Player player, int level) {
        // Формируем искомый тег
        String desiredTag = "radiation_" + level;

        // Получаем сет брони игрока
        ItemStack[] armor = player.getEquipment().getArmorContents();
        if (armor == null) return 0.0;

        double protection = 0.0;

        // Проверяем каждую часть брони
        for (ItemStack piece : armor) {
            if (piece == null) continue;

            ItemMeta meta = piece.getItemMeta();
            if (meta == null || meta.getPersistentDataContainer() == null) continue;

            // Ищем NBT-тег
            NamespacedKey key = new NamespacedKey(Grad.getInstance(), "radiation_level");
            String storedValue = meta.getPersistentDataContainer().get(key, PersistentDataType.STRING);

            // Если тег совпадает, добавляем 1/4 защиты
            if (storedValue != null && storedValue.equalsIgnoreCase(desiredTag)) {
                protection += 0.25;
            }
        }

        // Возвращаем суммарную защиту
        return Math.min(protection, 1.0); // Максимум 100% защиты
    }
}

package org.gezzon.grad.radiation;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.gezzon.grad.Grad;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Менеджер радиации:
 *  - хранение, загрузка, сохранение и поиск источников радиации;
 *  - хранение уровней радиации и их параметров (base_accumulation, damage_start, damage_interval, damage_amount);
 *  - отслеживание "бог-режима" для игроков;
 *  - хранение накопленных значений радиации игроков.
 */
public class RadiationManager {

    private final Grad plugin;

    // Список источников радиации: ID -> объект
    private final Map<Integer, RadiationSource> sources = new HashMap<>();
    private int nextId = 1; // Автоинкремент для нового источника

    // Данные по уровням радиации (1..5):
    // уровень -> Map{ "base_accumulation", "damage_start", "damage_interval", "damage_amount" }
    private final Map<Integer, Map<String, Double>> levelData = new HashMap<>();

    // Хранение радиации игроков: UUID -> накопленное значение
    private final Map<UUID, Double> playerRadiation = new HashMap<>();

    // Состояние "бог-режима" для игроков: множество UUID, у кого включен god-mode
    private final Set<UUID> godModePlayers = new HashSet<>();

    // Файл и конфигурация, в которых хранятся источники
    private File sourcesFile;
    private YamlConfiguration sourcesConfig;

    public RadiationManager(Grad plugin) {
        this.plugin = plugin;
    }

    /**
     * Инициализация менеджера:
     *  - загрузка параметров уровней из config.yml
     *  - загрузка источников из radiation-sources.yml
     */
    public void init() {
        loadLevelData();
        loadSourcesFromFile();
    }

    /**
     * Загрузка данных об уровнях радиации (1..5) из config.yml
     */
    private void loadLevelData() {
        // Читаем список уровней из секции radiation.levels
        List<Map<?, ?>> levels = plugin.getConfig().getMapList("radiation.levels");
        for (Map<?, ?> levelMap : levels) {
            int level = (int) levelMap.get("level");
            double baseAcc = (double) levelMap.get("base_accumulation");
            double damageStart = Double.valueOf(levelMap.get("damage_start").toString());
            double damageInterval = Double.valueOf(levelMap.get("damage_interval").toString());
            double damageAmount = Double.valueOf(levelMap.get("damage_amount").toString());

            Map<String, Double> data = new HashMap<>();
            data.put("base_accumulation", baseAcc);
            data.put("damage_start", damageStart);
            data.put("damage_interval", damageInterval);
            data.put("damage_amount", damageAmount);

            levelData.put(level, data);
        }
    }

    /**
     * Загрузка списка источников из файла radiation-sources.yml
     */
    private void loadSourcesFromFile() {
        sourcesFile = new File(plugin.getDataFolder(), "radiation-sources.yml");
        if (!sourcesFile.exists()) {
            // Если файла нет, пытаемся создать пустой
            try {
                sourcesFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().warning("Не удалось создать файл radiation-sources.yml!");
            }
        }
        sourcesConfig = YamlConfiguration.loadConfiguration(sourcesFile);

        // Читаем секцию 'sources'
        if (sourcesConfig.contains("sources")) {
            List<Map<?, ?>> list = sourcesConfig.getMapList("sources");
            for (Map<?, ?> entry : list) {
                int id = (int) entry.get("id");
                int intensity = (int) entry.get("intensity");
                double radius = Double.valueOf(entry.get("radius").toString());
                double power = Double.valueOf(entry.get("power").toString());
                String worldName = (String) entry.get("world");
                double x = Double.valueOf(entry.get("x").toString());
                double y = Double.valueOf(entry.get("y").toString());
                double z = Double.valueOf(entry.get("z").toString());

                World world = Bukkit.getWorld(worldName);
                if (world != null) {
                    Location center = new Location(world, x, y, z);
                    RadiationSource source = new RadiationSource(id, intensity, radius, power, center);
                    sources.put(id, source);

                    // Обновляем счётчик nextId, чтобы он не перезаписывал существующие источники
                    nextId = Math.max(nextId, id + 1);
                }
            }
        }
    }

    /**
     * Сохранение текущих источников радиации в файл radiation-sources.yml
     */
    public void saveSourcesToFile() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (RadiationSource source : sources.values()) {
            Map<String, Object> entry = new HashMap<>();
            entry.put("id", source.getId());
            entry.put("intensity", source.getIntensity());
            entry.put("radius", source.getRadius());
            entry.put("power", source.getPower());
            entry.put("world", source.getCenter().getWorld().getName());
            entry.put("x", source.getCenter().getX());
            entry.put("y", source.getCenter().getY());
            entry.put("z", source.getCenter().getZ());
            list.add(entry);
        }
        sourcesConfig.set("sources", list);

        try {
            sourcesConfig.save(sourcesFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Не удалось сохранить файл radiation-sources.yml!");
        }
    }

    /**
     * Создать новый источник радиации и сохранить в файл
     */
    public RadiationSource addSource(int intensity, double radius, double power, Location center) {
        RadiationSource source = new RadiationSource(nextId, intensity, radius, power, center);
        sources.put(nextId, source);
        nextId++;
        saveSourcesToFile();
        return source;
    }

    /**
     * Удалить источник радиации по ID
     */
    public boolean removeSource(int id) {
        if (sources.containsKey(id)) {
            sources.remove(id);
            saveSourcesToFile();
            return true;
        }
        return false;
    }

    /**
     * Получить источник радиации по ID
     */
    public RadiationSource getSource(int id) {
        return sources.get(id);
    }

    /**
     * Получить все существующие источники радиации
     */
    public Collection<RadiationSource> getAllSources() {
        return sources.values();
    }

    /**
     * Найти все источники в заданном радиусе distance от локации loc
     */
    public List<RadiationSource> getSourcesNear(Location loc, double distance) {
        return sources.values().stream()
                .filter(s -> s.getCenter().getWorld().equals(loc.getWorld()))
                .filter(s -> s.getCenter().distance(loc) <= distance)
                .collect(Collectors.toList());
    }

    /**
     * Получить Map с данными выбранного уровня (1..5),
     * где можно взять base_accumulation, damage_start и т.д.
     */
    public Map<String, Double> getLevelData(int level) {
        return levelData.get(level);
    }

    /**
     * Установить накопленный уровень радиации для игрока
     */
    public void setPlayerRadiation(UUID uuid, double value) {
        playerRadiation.put(uuid, value);
    }

    /**
     * Получить накопленный уровень радиации игрока
     */
    public double getPlayerRadiation(UUID uuid) {
        return playerRadiation.getOrDefault(uuid, 0.0);
    }

    /**
     * Включить/выключить "бог-режим" (god-mode) для указанного игрока
     */
    public void setGodMode(UUID uuid, boolean god) {
        if (god) {
            godModePlayers.add(uuid);
        } else {
            godModePlayers.remove(uuid);
        }
    }

    /**
     * Узнать, включён ли "бог-режим" у игрока
     */
    public boolean isGodMode(UUID uuid) {
        return godModePlayers.contains(uuid);
    }
}
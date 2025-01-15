package org.gezzon.grad.radiation;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
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

    private Material radioactiveBlockType; // Тип блока
    private double blockRadius;            // Радиус радиации вокруг блока
    private int blockLevel;                // Уровень радиации от блока
    private double blockPower;             // Множитель радиации от блока

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
        loadRadioactiveBlockConfig();
    }



    public void loadRadioactiveBlockConfig() {
        FileConfiguration config = plugin.getConfig();
        String blockTypeStr = config.getString("blocks.radioactive_block.type");
        if (blockTypeStr == null) {
            plugin.getLogger().warning("Тип радиационного блока не задан в config.yml!");
            return;
        }

        try {
            Material blockType = Material.valueOf(blockTypeStr.toUpperCase());
            radioactiveBlockType = blockType; // Сохраняем тип блока в поле менеджера

            blockRadius = config.getDouble("blocks.radioactive_block.radius", 10);
            blockLevel = config.getInt("blocks.radioactive_block.level", 3);
            blockPower = config.getDouble("blocks.radioactive_block.power", 1.0);

        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Неверный тип блока в config.yml: " + blockTypeStr);
        }
    }

    /**
     * Загрузка данных об уровнях радиации (1..5) из config.yml
     */
    private void loadLevelData() {
        List<Map<?, ?>> levels = plugin.getConfig().getMapList("radiation.levels");
        for (Map<?, ?> levelMap : levels) {
            int level = ((Number) levelMap.get("level")).intValue();
            double baseAcc = ((Number) levelMap.get("base_accumulation")).doubleValue();
            double damageStart = ((Number) levelMap.get("damage_start")).doubleValue();
            double damageInterval = ((Number) levelMap.get("damage_interval")).doubleValue();
            double damageAmount = ((Number) levelMap.get("damage_amount")).doubleValue();

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
            try {
                // Создаём файл, если его нет
                sourcesFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().warning("Не удалось создать файл radiation-sources.yml!");
                return;
            }
        }
        sourcesConfig = YamlConfiguration.loadConfiguration(sourcesFile);
        if (sourcesConfig == null) {
            sourcesConfig = new YamlConfiguration();
        }

        if (sourcesConfig.contains("sources")) {
            List<Map<?, ?>> list = sourcesConfig.getMapList("sources");
            for (Map<?, ?> entry : list) {
                int id = ((Number) entry.get("id")).intValue();
                int intensity = ((Number) entry.get("intensity")).intValue();
                double radius = ((Number) entry.get("radius")).doubleValue();
                double power = ((Number) entry.get("power")).doubleValue();
                String worldName = (String) entry.get("world");
                double x = ((Number) entry.get("x")).doubleValue();
                double y = ((Number) entry.get("y")).doubleValue();
                double z = ((Number) entry.get("z")).doubleValue();

                World world = Bukkit.getWorld(worldName);
                if (world != null) {
                    Location center = new Location(world, x, y, z);
                    RadiationSource source = new RadiationSource(id, intensity, radius, power, center);
                    sources.put(id, source);
                    nextId = Math.max(nextId, id + 1);
                }
            }
        }
    }

    /**
     * Сохранение текущих источников радиации в файл radiation-sources.yml
     */
    public void saveSourcesToFile() {
        if (sourcesConfig == null) {
            plugin.getLogger().warning("sourcesConfig не инициализирован. Пропускаем сохранение источников.");
            return;
        }
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

    public Material getRadioactiveBlockType() {
        return radioactiveBlockType;
    }

    public double getBlockRadius() {
        return blockRadius;
    }

    public int getBlockLevel() {
        return blockLevel;
    }

    public double getBlockPower() {
        return blockPower;
    }
    /**
     * Узнать, включён ли "бог-режим" у игрока
     */
    public boolean isGodMode(UUID uuid) {
        return godModePlayers.contains(uuid);
    }
}
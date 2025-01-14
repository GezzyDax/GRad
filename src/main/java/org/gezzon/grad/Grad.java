package org.gezzon.grad;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;


import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Основной класс плагина ColdRad.
 */
public class Grad extends JavaPlugin implements Listener, TabExecutor {

    // ============================
    // Вспомогательные классы
    // ============================

    /**
     * Класс, описывающий один источник радиации (сферическую зону).
     */
    public static class RadiationSource {
        private int id;
        private int intensity;   // Уровень радиации (1..5)
        private double radius;   // Радиус сферы
        private double power;    // Множитель радиации
        private Location center; // Центр зоны (x,y,z + мир)

        public RadiationSource(int id, int intensity, double radius, double power, Location center) {
            this.id = id;
            this.intensity = intensity;
            this.radius = radius;
            this.power = power;
            this.center = center;
        }

        public int getId() {
            return id;
        }

        public int getIntensity() {
            return intensity;
        }

        public void setIntensity(int intensity) {
            this.intensity = intensity;
        }

        public double getRadius() {
            return radius;
        }

        public void setRadius(double radius) {
            this.radius = radius;
        }

        public double getPower() {
            return power;
        }

        public void setPower(double power) {
            this.power = power;
        }

        public Location getCenter() {
            return center;
        }

        public void setCenter(Location center) {
            this.center = center;
        }
    }

    /**
     * Менеджер радиации: хранение, загрузка, сохранение и поиск источников радиации.
     */
    public static class RadiationManager {
        private final Grad plugin;
        // Список источников радиации. Ключ — id, значение — объект источника.
        private final Map<Integer, RadiationSource> sources = new HashMap<>();
        private int nextId = 1; // Используем автоинкремент для ID источников

        // Загрузка уровней радиации из конфига (1..5).
        // Структура: level -> (base_accumulation, damage_start, damage_interval, damage_amount)
        private final Map<Integer, Map<String, Double>> levelData = new HashMap<>();

        // Хранение радиации игроков: Player UUID -> накопленное значение
        private final Map<UUID, Double> playerRadiation = new HashMap<>();

        // Хранение состояния "бог-режима" для игроков
        private final Set<UUID> godModePlayers = new HashSet<>();

        // Файл для хранения источников
        private File sourcesFile;
        private FileConfiguration sourcesConfig;

        public RadiationManager(Grad plugin) {
            this.plugin = plugin;
        }

        /**
         * Инициализация менеджера: чтение конфигурации уровней и загрузка источников
         */
        public void init() {
            loadLevelData();
            loadSourcesFromFile();
        }

        /**
         * Загрузка данных о каждом уровне радиации из config.yml
         */
        private void loadLevelData() {
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
         * Загрузка источников из файла radiation-sources.yml
         */
        private void loadSourcesFromFile() {
            sourcesFile = new File(plugin.getDataFolder(), "radiation-sources.yml");
            if (!sourcesFile.exists()) {
                // Если файла нет, создаём пустой
                try {
                    sourcesFile.createNewFile();
                } catch (IOException e) {
                    plugin.getLogger().warning("Не удалось создать файл radiation-sources.yml!");
                }
            }
            sourcesConfig = YamlConfiguration.loadConfiguration(sourcesFile);
            // Читаем секцию sources
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
         * Создание нового источника радиации
         */
        public RadiationSource addSource(int intensity, double radius, double power, Location center) {
            RadiationSource source = new RadiationSource(nextId, intensity, radius, power, center);
            sources.put(nextId, source);
            nextId++;
            saveSourcesToFile();
            return source;
        }

        /**
         * Удаление источника радиации
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
         * Получение источника по ID
         */
        public RadiationSource getSource(int id) {
            return sources.get(id);
        }

        /**
         * Список всех источников
         */
        public Collection<RadiationSource> getAllSources() {
            return sources.values();
        }

        /**
         * Поиск ближайших источников в радиусе (distance) от точки
         */
        public List<RadiationSource> getSourcesNear(Location loc, double distance) {
            return sources.values().stream()
                    .filter(s -> s.getCenter().getWorld().equals(loc.getWorld()))
                    .filter(s -> s.getCenter().distance(loc) <= distance)
                    .collect(Collectors.toList());
        }

        /**
         * Получение Map с данными о выбранном уровне радиации (base_accumulation и т.д.)
         */
        public Map<String, Double> getLevelData(int level) {
            return levelData.get(level);
        }

        /**
         * Запоминание накопленного уровня радиации игрока
         */
        public void setPlayerRadiation(UUID uuid, double value) {
            playerRadiation.put(uuid, value);
        }

        /**
         * Получение накопленного уровня радиации игрока
         */
        public double getPlayerRadiation(UUID uuid) {
            return playerRadiation.getOrDefault(uuid, 0.0);
        }

        /**
         * Добавить "бог-режим" игроку
         */
        public void setGodMode(UUID uuid, boolean god) {
            if (god) {
                godModePlayers.add(uuid);
            } else {
                godModePlayers.remove(uuid);
            }
        }

        /**
         * Проверка "бог-режима"
         */
        public boolean isGodMode(UUID uuid) {
            return godModePlayers.contains(uuid);
        }
    }

    /**
     * Логика периодического начисления радиации и нанесения урона
     */
    public class RadiationTask extends BukkitRunnable {

        private final RadiationManager manager;

        // Чтобы не вычислять урон каждую секунду заново, будем отслеживать,
        // когда именно нужно нанести урон. У разных игроков может быть разная
        // задержка между "тиками" урона (damage_interval).
        // Храним: UUID -> время до следующего удара (в секундах).
        private final Map<UUID, Double> damageTimers = new HashMap<>();

        public RadiationTask(RadiationManager manager) {
            this.manager = manager;
        }

        @Override
        public void run() {
            for (Player player : Bukkit.getOnlinePlayers()) {
                UUID uuid = player.getUniqueId();

                // Если включён "god-режим", пропускаем
                if (manager.isGodMode(uuid)) {
                    manager.setPlayerRadiation(uuid, 0.0);
                    continue;
                }

                // 1) Считаем, сколько радиации набирает игрок в эту секунду.
                double newRadiation = calculateRadiationForPlayer(player);
                // Текущее накопление
                double currentRad = manager.getPlayerRadiation(uuid);
                double updatedRad = currentRad + newRadiation;
                manager.setPlayerRadiation(uuid, updatedRad);

                // 2) Определяем, какой уровень радиации "активен"
                //    (т.е. какой damage_start <= updatedRad).
                double damage = 0.0;
                double damageInterval = Double.MAX_VALUE; // большой интервал (пока не определим точнее)
                for (int level = 5; level >= 1; level--) {
                    Map<String, Double> data = manager.getLevelData(level);
                    if (data == null) {
                        continue;
                    }
                    double ds = data.get("damage_start");
                    if (updatedRad >= ds) {
                        damage = data.get("damage_amount");
                        damageInterval = data.get("damage_interval");
                        // Уровень найден, выходим
                        break;
                    }
                }

                // 3) Если damage > 0, значит нужно наносить урон каждые damageInterval секунд
                if (damage > 0) {
                    double timeLeft = damageTimers.getOrDefault(uuid, 0.0);
                    if (timeLeft <= 0) {
                        // Наносим урон
                        player.damage(damage);
                        // Сбрасываем таймер
                        damageTimers.put(uuid, damageInterval);
                    } else {
                        // Уменьшаем таймер
                        damageTimers.put(uuid, timeLeft - 1);
                    }
                } else {
                    // Если урона нет, сбрасываем таймер
                    damageTimers.put(uuid, 0.0);
                }
            }
        }

        /**
         * Вычисляет радиацию, получаемую игроком за 1 секунду.
         * Учитываем сферические источники и флаги WorldGuard.
         */
        private double calculateRadiationForPlayer(Player player) {
            Location loc = player.getLocation();
            double total = 0.0;

            // 1) Суммируем вклад от всех сферических источников.
            for (RadiationSource source : manager.getAllSources()) {
                if (!source.getCenter().getWorld().equals(loc.getWorld())) {
                    continue;
                }
                double distance = source.getCenter().distance(loc);
                if (distance <= source.getRadius()) {
                    // Определяем, в какую "часть" зоны попал игрок
                    // Делим радиус на 5 равных сегментов
                    double segment = source.getRadius() / 5.0;
                    int part = (int) Math.ceil(distance / segment);
                    if (part < 1) part = 1;
                    if (part > 5) part = 5;

                    // base_accumulation для уровня source.getIntensity()
                    Map<String, Double> data = manager.getLevelData(source.getIntensity());
                    if (data != null) {
                        double baseAcc = data.get("base_accumulation");
                        // Формула: base_acc * power * (1.5^(part - 1))
                        double localAccum = baseAcc * source.getPower() * (Math.pow(1.5, part - 1));
                        total += localAccum;
                    }
                }
            }

            // 2) Суммируем вклад от WorldGuard регионов с флагами radiation_1..radiation_5.
            //    (В конфиге у нас есть worldguard.flags.radiation_X -> X)
            //    Предположим, что флаг в конфиге задаётся как radiation_1: 1, radiation_2: 2 и т.д.


            // Получаем контейнер регионов
            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();

            // Получаем менеджер регионов для текущего мира
            RegionManager regionManager = container.get(BukkitAdapter.adapt(loc.getWorld()));
            int wgMaxLevel = 0;
            // Проверяем, существует ли RegionManager для данного мира
            if (regionManager != null) {
                ApplicableRegionSet regions = regionManager.getApplicableRegions(BukkitAdapter.asBlockVector(loc));

                // Перебираем все регионы в точке
                for (ProtectedRegion region : regions) {
                    String regionId = region.getId().toLowerCase();

                    // Проверяем название региона или его свойства
                    for (int i = 1; i <= 5; i++) {
                        if (regionId.contains("radiation_" + i)) {
                            wgMaxLevel = Math.max(wgMaxLevel, i);
                        }
                    }
                }
            }
            // Смотрим, какие флаги установлены в регионах
            // Упрощённо: если регион имеет флаг radiation_1..radiation_5, берём максимальный уровень
            // (или суммируем — как вам удобнее. Но в задаче указано, что "множитель радиации всегда одинаковый",
            //  значит, если несколько регионов, можно взять самый большой уровень).


            // Для получения значения кастомных флагов нужен свой флаг-объект. Однако,
            // в данном примере мы для упрощения берём значение level из конфига по имени флага.
            // В реальном проекте можно использовать WGCustomFlags или аналог.
            //
            // Допустим, в config.yml у нас:
            // worldguard:
            //   flags:
            //     radiation_1: 1
            //     radiation_2: 2
            //     radiation_3: 3
            //     radiation_4: 4
            //     radiation_5: 5
            //
            // Мы не можем напрямую получить "radiation_1" из WorldGuard без объявленного StateFlag.
            // Поэтому для демонстрации предположим, что вы вручную назначаете StateFlag "radiation_1" (и т.п.)
            // через WGCustomFlags, либо делаете логику сопоставления регионам по их имени / любому другому признаку.

            // В рамках демо — просто перебираем регионы и проверяем по имени (или любой другой логике),
            // содержится ли в имени "radiation_1" / "radiation_2" / ...
            // Это, конечно, костыльно, но иллюстрирует идею.
            //
            // В реальном проекте вам нужно объявить свои StateFlag ( radiation_1 и т.д. ) и считывать их из региона.
            // Либо вручную сопоставлять в вашем коде.


            // Если найден флаг WG радиации
            if (wgMaxLevel > 0) {
                Map<String, Double> data = manager.getLevelData(wgMaxLevel);
                if (data != null) {
                    double baseAcc = data.get("base_accumulation");
                    // В задаче сказано, что "множитель радиации всегда одинаковый", то есть без деления на части.
                    total += baseAcc;
                }
            }

            return total;
        }
    }

    // ============================
    // Поля класса ColdRad
    // ============================

    private RadiationManager radiationManager;
    private RadiationTask radiationTask;

    // ============================
    // Жизненный цикл плагина
    // ============================

    @Override
    public void onEnable() {
        // Сначала сохраняем дефолтный config.yml, если он не существует
        saveDefaultConfig();

        // Создаём менеджер, загружаем источники и уровни
        radiationManager = new RadiationManager(this);
        radiationManager.init();

        // Регистрируем и запускаем задачу (каждую секунду)
        radiationTask = new RadiationTask(radiationManager);
        radiationTask.runTaskTimer(this, 20L, 20L); // старт через 1с, повтор каждую 1с

        // Регистрируем listener и команду
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("radiation").setExecutor(this);
        getCommand("radiation").setTabCompleter(this);

        getLogger().info("ColdRad плагин включён!");
    }

    @Override
    public void onDisable() {
        // Сохраняем источники
        radiationManager.saveSourcesToFile();
        getLogger().info("ColdRad плагин выключён!");
    }

    // ============================
    // Обработка команд /radiation
    // ============================

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Основная точка входа для /radiation ...
        if (!command.getName().equalsIgnoreCase("radiation")) {
            return false;
        }

        if (args.length == 0) {
            sendHelp(sender, 1);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "list":
                handleList(sender);
                break;
            case "add":
                handleAdd(sender, args);
                break;
            case "del":
                handleDel(sender, args);
                break;
            case "edit":
                handleEdit(sender, args);
                break;
            case "meter":
                handleMeter(sender);
                break;
            case "view":
                handleView(sender, args);
                break;
            case "near":
                handleNear(sender, args);
                break;
            case "god":
                handleGod(sender);
                break;
            case "help":
                int page = 1;
                if (args.length >= 2) {
                    try {
                        page = Integer.parseInt(args[1]);
                    } catch (NumberFormatException ignored) {
                    }
                }
                sendHelp(sender, page);
                break;
            default:
                sendHelp(sender, 1);
                break;
        }
        return true;
    }

    private void sendHelp(CommandSender sender, int page) {
        // Для простоты выводим всё на одной странице
        sender.sendMessage("§a=====[ ColdRad Help ]=====");
        sender.sendMessage("§7/radiation list §f- Показать все источники.");
        sender.sendMessage("§7/radiation add <intensity> <radius> [power=1] [x,y,z] §f- Добавить источник.");
        sender.sendMessage("§7/radiation del <id> §f- Удалить источник.");
        sender.sendMessage("§7/radiation edit <id> <intensity|radius|power> <value> §f- Изменить параметр источника.");
        sender.sendMessage("§7/radiation meter §f- Показать уровень радиации в текущей точке.");
        sender.sendMessage("§7/radiation view <id|all> §f- Визуализировать радиационную зону (частицы).");
        sender.sendMessage("§7/radiation near <radius> §f- Показать источники в заданном радиусе от вас.");
        sender.sendMessage("§7/radiation god §f- Включить/выключить режим 'бога' от радиации.");
        sender.sendMessage("§7/radiation help [page] §f- Показать помощь.");
    }

    private void handleList(CommandSender sender) {
        Collection<RadiationSource> all = radiationManager.getAllSources();
        if (all.isEmpty()) {
            sender.sendMessage("§cИсточников радиации нет.");
            return;
        }
        sender.sendMessage("§aСписок источников радиации:");
        for (RadiationSource src : all) {
            sender.sendMessage(String.format("§7ID: %d, Intensity: %d, Radius: %.1f, Power: %.2f, World: %s (%.1f, %.1f, %.1f)",
                    src.getId(),
                    src.getIntensity(),
                    src.getRadius(),
                    src.getPower(),
                    src.getCenter().getWorld().getName(),
                    src.getCenter().getX(),
                    src.getCenter().getY(),
                    src.getCenter().getZ()
            ));
        }
    }

    private void handleAdd(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§cИспользование: /radiation add <intensity> <radius> [power=1] [x,y,z]");
            return;
        }
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cКоманду может использовать только игрок.");
            return;
        }
        Player player = (Player) sender;
        try {
            int intensity = Integer.parseInt(args[1]);
            double radius = Double.parseDouble(args[2]);
            double power = 1.0;
            Location loc = player.getLocation();

            if (args.length >= 4) {
                // Проверяем, power ли это или координаты
                // Допустим, если параметр содержит запятую - считаем, что это координаты
                if (args[3].contains(",")) {
                    String[] coords = args[3].split(",");
                    double x = Double.parseDouble(coords[0]);
                    double y = Double.parseDouble(coords[1]);
                    double z = Double.parseDouble(coords[2]);
                    loc = new Location(player.getWorld(), x, y, z);
                } else {
                    // Иначе, это power
                    power = Double.parseDouble(args[3]);
                }
            }
            if (args.length >= 5) {
                // Если дошли до сюда, то 5-й аргумент явно должен быть координатами
                if (args[4].contains(",")) {
                    String[] coords = args[4].split(",");
                    double x = Double.parseDouble(coords[0]);
                    double y = Double.parseDouble(coords[1]);
                    double z = Double.parseDouble(coords[2]);
                    loc = new Location(player.getWorld(), x, y, z);
                }
            }

            RadiationSource source = radiationManager.addSource(intensity, radius, power, loc);
            sender.sendMessage("§aНовый источник создан: ID = " + source.getId());
        } catch (NumberFormatException e) {
            sender.sendMessage("§cНеверный формат числа!");
        }
    }

    private void handleDel(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cИспользование: /radiation del <id>");
            return;
        }
        try {
            int id = Integer.parseInt(args[1]);
            boolean removed = radiationManager.removeSource(id);
            if (removed) {
                sender.sendMessage("§aИсточник с ID " + id + " удалён.");
            } else {
                sender.sendMessage("§cИсточник с ID " + id + " не найден.");
            }
        } catch (NumberFormatException e) {
            sender.sendMessage("§cНеверный формат ID!");
        }
    }

    private void handleEdit(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage("§cИспользование: /radiation edit <id> <intensity|radius|power> <value>");
            return;
        }
        try {
            int id = Integer.parseInt(args[1]);
            String field = args[2].toLowerCase();
            double value = Double.parseDouble(args[3]);

            RadiationSource src = radiationManager.getSource(id);
            if (src == null) {
                sender.sendMessage("§cИсточник с ID " + id + " не найден.");
                return;
            }

            switch (field) {
                case "intensity":
                    src.setIntensity((int) value);
                    break;
                case "radius":
                    src.setRadius(value);
                    break;
                case "power":
                    src.setPower(value);
                    break;
                default:
                    sender.sendMessage("§cНеизвестный параметр: " + field);
                    return;
            }
            radiationManager.saveSourcesToFile();
            sender.sendMessage("§aИсточник " + id + " успешно изменён.");
        } catch (NumberFormatException e) {
            sender.sendMessage("§cНеверный формат числа!");
        }
    }

    private void handleMeter(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cТолько для игроков.");
            return;
        }
        Player player = (Player) sender;
        double rad = radiationManager.getPlayerRadiation(player.getUniqueId());
        player.sendMessage("§aВаш текущий уровень радиации: §e" + String.format("%.2f", rad));
    }

    private void handleView(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cТолько для игроков.");
            return;
        }
        Player player = (Player) sender;
        if (args.length < 2) {
            sender.sendMessage("§cИспользование: /radiation view <id|all>");
            return;
        }

        if (args[1].equalsIgnoreCase("all")) {
            for (RadiationSource source : radiationManager.getAllSources()) {
                visualizeSphere(player, source);
            }
            sender.sendMessage("§aВизуализация всех источников запущена (см. частицы).");
        } else {
            try {
                int id = Integer.parseInt(args[1]);
                RadiationSource src = radiationManager.getSource(id);
                if (src == null) {
                    sender.sendMessage("§cИсточник с ID " + id + " не найден.");
                    return;
                }
                visualizeSphere(player, src);
                sender.sendMessage("§aВизуализация источника " + id + " запущена.");
            } catch (NumberFormatException e) {
                sender.sendMessage("§cНеверный формат ID!");
            }
        }
    }

    /**
     * Визуализируем сферу с помощью частиц вокруг центра источника.
     * Для упрощения просто рисуем частицы по окружности с шагом.
     */
    private void visualizeSphere(Player player, RadiationSource source) {
        // Число точек, шаг угла и т.п.
        double r = source.getRadius();
        Location center = source.getCenter();
        World world = center.getWorld();
        if (world == null) return;

        // Возьмём полусферу (или полный шар?)
        // Упростим: рисуем несколько окружностей по высоте
        for (double y = -r; y <= r; y += (r / 4)) {
            double circleRadius = Math.sqrt(r*r - y*y);
            // Рисуем окружность
            for (int angleDeg = 0; angleDeg < 360; angleDeg += 15) {
                double radAngle = Math.toRadians(angleDeg);
                double x = center.getX() + circleRadius * Math.cos(radAngle);
                double z = center.getZ() + circleRadius * Math.sin(radAngle);
                Location particleLoc = new Location(world, x, center.getY() + y, z);
                player.spawnParticle(Particle.VILLAGER_HAPPY, particleLoc, 1, 0, 0, 0, 0);
            }
        }
    }

    private void handleNear(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cТолько для игроков.");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage("§cИспользование: /radiation near <radius>");
            return;
        }
        Player player = (Player) sender;
        try {
            double radius = Double.parseDouble(args[1]);
            List<RadiationSource> near = radiationManager.getSourcesNear(player.getLocation(), radius);
            if (near.isEmpty()) {
                sender.sendMessage("§aРядом нет источников радиации.");
            } else {
                sender.sendMessage("§aИсточники радиации в радиусе " + radius + ":");
                for (RadiationSource src : near) {
                    sender.sendMessage(String.format("§7ID: %d, (%.1f, %.1f, %.1f), r=%.1f",
                            src.getId(),
                            src.getCenter().getX(),
                            src.getCenter().getY(),
                            src.getCenter().getZ(),
                            src.getRadius()
                    ));
                }
            }
        } catch (NumberFormatException e) {
            sender.sendMessage("§cНеверный формат числа!");
        }
    }

    private void handleGod(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cТолько для игроков.");
            return;
        }
        Player player = (Player) sender;
        UUID uuid = player.getUniqueId();
        boolean isGod = radiationManager.isGodMode(uuid);
        radiationManager.setGodMode(uuid, !isGod);
        if (!isGod) {
            sender.sendMessage("§aРежим бога от радиации включён.");
        } else {
            sender.sendMessage("§cРежим бога от радиации выключен.");
        }
    }

    // ============================
    // Дополнительно: Listener'ы
    // ============================

    /**
     * Пример: при заходе на сервер сбрасываем радиацию в 0
     */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        radiationManager.setPlayerRadiation(event.getPlayer().getUniqueId(), 0.0);
    }

    /**
     * Пример: при движении можно было бы пересчитывать радиацию чаще, но
     * мы уже имеем периодическую задачу. Поэтому тут не делаем ничего.
     */
    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        // Место для логики (если понадобится)
    }

    // ============================
    // TabCompleter (упрощённый)
    // ============================

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!command.getName().equalsIgnoreCase("radiation")) {
            return Collections.emptyList();
        }
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            // Подсказки для первого аргумента
            String[] subs = {"list", "add", "del", "edit", "meter", "view", "near", "god", "help"};
            for (String s : subs) {
                if (s.startsWith(args[0].toLowerCase())) {
                    completions.add(s);
                }
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("del")) {
            // Подсказки для ID
            for (RadiationSource src : radiationManager.getAllSources()) {
                String idStr = String.valueOf(src.getId());
                if (idStr.startsWith(args[1])) {
                    completions.add(idStr);
                }
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("edit")) {
            // Предлагаем ID
            for (RadiationSource src : radiationManager.getAllSources()) {
                String idStr = String.valueOf(src.getId());
                if (idStr.startsWith(args[1])) {
                    completions.add(idStr);
                }
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("edit")) {
            // Предлагаем поля intensity|radius|power
            String[] fields = {"intensity", "radius", "power"};
            for (String f : fields) {
                if (f.startsWith(args[2].toLowerCase())) {
                    completions.add(f);
                }
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("view")) {
            // view <source|all>
            if ("all".startsWith(args[1].toLowerCase())) {
                completions.add("all");
            }
            for (RadiationSource src : radiationManager.getAllSources()) {
                String idStr = String.valueOf(src.getId());
                if (idStr.startsWith(args[1])) {
                    completions.add(idStr);
                }
            }
        }
        return completions;
    }
}


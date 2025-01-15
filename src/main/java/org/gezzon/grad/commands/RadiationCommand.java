package org.gezzon.grad.commands;

import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.gezzon.grad.Grad;
import org.gezzon.grad.radiation.RadiationManager;
import org.gezzon.grad.radiation.RadiationSource;
import org.bukkit.Particle;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Единый класс для обработки всех подкоманд /radiation и автодополнения (TabComplete).
 */
public class RadiationCommand implements CommandExecutor, TabCompleter {

    private final Grad plugin;
    private final RadiationManager radiationManager;

    public RadiationCommand(Grad plugin, RadiationManager radiationManager) {
        this.plugin = plugin;
        this.radiationManager = radiationManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Проверяем, что вызвали именно /radiation
        if (!command.getName().equalsIgnoreCase("radiation")) {
            return false;
        }

        // Если не указали аргументы — показываем help
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
            case "clear":
                handleClear(sender,args);
                break;
            case "help":
                int page = 1;
                if (args.length >= 2) {
                    try {
                        page = Integer.parseInt(args[1]);
                    } catch (NumberFormatException ignored) {}
                }
                sendHelp(sender, page);
                break;
            default:
                // Если нет такой подкоманды, снова выводим help
                sendHelp(sender, 1);
                break;
        }
        return true;
    }

    /**
     * Показать справку /radiation help
     */
    private void sendHelp(CommandSender sender, int page) {
        sender.sendMessage("§a=====[ ColdRad Help ]=====");
        sender.sendMessage("§7/radiation list §f- Показать все источники радиации.");
        sender.sendMessage("§7/radiation add <intensity> <radius> [power=1] [x,y,z] §f- Добавить источник.");
        sender.sendMessage("§7/radiation del <id> §f- Удалить источник.");
        sender.sendMessage("§7/radiation edit <id> <intensity|radius|power> <value> §f- Изменить источник.");
        sender.sendMessage("§7/radiation meter §f- Показать уровень радиации в текущей точке.");
        sender.sendMessage("§7/radiation view <id|all> §f- Визуализировать радиационные зоны (частицы).");
        sender.sendMessage("§7/radiation near <radius> §f- Найти источники в заданном радиусе.");
        sender.sendMessage("§7/radiation god §f- Включить/выключить 'бог-режим' от радиации.");
        sender.sendMessage("§7/radiation help [page] §f- Показать помощь.");
        sender.sendMessage("§7/radiation clear [player] $f- Очистить уровень радиации");
    }

    /**
     * /radiation list
     * Показывает все источники
     */
    private void handleList(CommandSender sender) {
        Collection<RadiationSource> all = radiationManager.getAllSources();
        if (all.isEmpty()) {
            sender.sendMessage("§cИсточников радиации нет.");
            return;
        }
        sender.sendMessage("§aСписок источников радиации:");
        for (RadiationSource src : all) {
            sender.sendMessage(String.format(
                    "§7ID: %d, Intensity: %d, Radius: %.1f, Power: %.2f, World: %s (%.1f, %.1f, %.1f)",
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

    /**
     * /radiation add <intensity> <radius> [power=1] [x,y,z]
     */
    private void handleAdd(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§cИспользование: /radiation add <intensity> <radius> [power=1] [x,y,z]");
            return;
        }
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cТолько игрок может использовать эту команду.");
            return;
        }
        Player player = (Player) sender;

        try {
            int intensity = Integer.parseInt(args[1]);
            double radius = Double.parseDouble(args[2]);
            double power = 1.0;
            Location loc = player.getLocation();

            // Проверяем аргументы на power и/или координаты
            if (args.length >= 4) {
                if (args[3].contains(",")) {
                    // Похоже на "x,y,z"
                    String[] coords = args[3].split(",");
                    double x = Double.parseDouble(coords[0]);
                    double y = Double.parseDouble(coords[1]);
                    double z = Double.parseDouble(coords[2]);
                    loc = new Location(player.getWorld(), x, y, z);
                } else {
                    // Похоже на power
                    power = Double.parseDouble(args[3]);
                }
            }
            // Если есть пятый аргумент, то точно координаты
            if (args.length >= 5) {
                if (args[4].contains(",")) {
                    String[] coords = args[4].split(",");
                    double x = Double.parseDouble(coords[0]);
                    double y = Double.parseDouble(coords[1]);
                    double z = Double.parseDouble(coords[2]);
                    loc = new Location(player.getWorld(), x, y, z);
                }
            }

            // Создаём и добавляем источник
            RadiationSource source = radiationManager.addSource(intensity, radius, power, loc);
            sender.sendMessage("§aНовый источник создан. ID = " + source.getId());

        } catch (NumberFormatException e) {
            sender.sendMessage("§cНеверный формат числового параметра!");
        }
    }

    /**
     * /radiation del <id>
     */
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

    /**
     * /radiation edit <id> <intensity|radius|power> <value>
     */
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

            // Сохраняем изменения
            radiationManager.saveSourcesToFile();
            sender.sendMessage("§aИсточник " + id + " успешно изменён.");

        } catch (NumberFormatException e) {
            sender.sendMessage("§cНеверный формат числа!");
        }
    }

    /**
     * /radiation meter
     * Показывает текущий уровень радиации игрока
     */
    private void handleMeter(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cТолько игрок может использовать эту команду.");
            return;
        }
        Player player = (Player) sender;
        double rad = radiationManager.getPlayerRadiation(player.getUniqueId());
        player.sendMessage("§aВаш текущий уровень радиации: §e" + String.format("%.2f", rad));
    }

    /**
     * /radiation view <id|all>
     * Визуализация сферических зон частицами
     */
    private void handleView(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cТолько игрок может использовать эту команду.");
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
            sender.sendMessage("§aВизуализация всех источников радиации запущена (частицы).");
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
     * Рисуем сферу частиц вокруг центра источника
     */
    private void visualizeSphere(Player player, RadiationSource source) {
        double r = source.getRadius();
        Location center = source.getCenter();
        World world = center.getWorld();
        if (world == null) return;

        Particle.DustOptions dustOptions = new Particle.DustOptions(Color.RED, 1.0f);

        for (double y = -r; y <= r; y += (r / 4)) {
            double circleRadius = Math.sqrt(r * r - y * y);
            for (int angleDeg = 0; angleDeg < 360; angleDeg += 15) {
                double radAngle = Math.toRadians(angleDeg);
                double x = center.getX() + circleRadius * Math.cos(radAngle);
                double z = center.getZ() + circleRadius * Math.sin(radAngle);
                Location particleLoc = new Location(world, x, center.getY() + y, z);

                // Спавним REDSTONE с настройками DustOptions
                player.spawnParticle(Particle.HAPPY_VILLAGER, particleLoc, 1, dustOptions);
            }
        }
    }

    /**
     * /radiation near <radius>
     * Показывает все источники в заданном радиусе от игрока
     */
    private void handleNear(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cТолько игрок может использовать эту команду.");
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

    /**
     * /radiation clear [player]
     * - Без аргументов: сброс радиации текущему игроку.
     * - С аргументом: сброс радиации целевому игроку.
     */
    private void handleClear(CommandSender sender, String[] args) {
        // /radiation clear
        if (args.length == 1) {
            if (sender instanceof Player) {
                Player player = (Player) sender;
                radiationManager.setPlayerRadiation(player.getUniqueId(), 0.0);
                sender.sendMessage("§aВаша радиация обнулена.");
            } else {
                sender.sendMessage("§cВы должны указать имя игрока, если вызываете команду из консоли.");
            }
            return;
        }

        // /radiation clear <playerName>
        if (args.length == 2) {
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage("§cИгрок " + args[1] + " не найден.");
                return;
            }
            radiationManager.setPlayerRadiation(target.getUniqueId(), 0.0);
            sender.sendMessage("§aРадиация обнулена для " + target.getName());
            return;
        }

        // Если аргументов больше, чем нужно
        sender.sendMessage("§cИспользование: /radiation clear [player]");
    }
    /**
     * /radiation god
     * Включает/выключает "бог-режим" у игрока, чтобы не получать урон от радиации
     */
    private void handleGod(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cТолько игрок может использовать эту команду.");
            return;
        }
        Player player = (Player) sender;
        boolean isGod = radiationManager.isGodMode(player.getUniqueId());
        radiationManager.setGodMode(player.getUniqueId(), !isGod);

        if (!isGod) {
            sender.sendMessage("§aРежим бога от радиации включён.");
        } else {
            sender.sendMessage("§cРежим бога от радиации выключен.");
        }
    }

    /**
     * Логика автодополнения (TabCompleter)
     */
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!command.getName().equalsIgnoreCase("radiation")) {
            return Collections.emptyList();
        }

        List<String> completions = new ArrayList<>();

        // 1) /radiation <subcommand>
        if (args.length == 1) {
            String[] subs = {"list", "add", "del", "edit", "meter", "view", "near", "god", "help"};
            for (String s : subs) {
                if (s.startsWith(args[0].toLowerCase())) {
                    completions.add(s);
                }
            }
        }
        // 2) /radiation del <id>
        else if (args.length == 2 && args[0].equalsIgnoreCase("del")) {
            for (RadiationSource src : radiationManager.getAllSources()) {
                String idStr = String.valueOf(src.getId());
                if (idStr.startsWith(args[1])) {
                    completions.add(idStr);
                }
            }
        }
        else if (args.length == 2 && args[0].equalsIgnoreCase("clear")) {
            // Предлагаем список онлайн-игроков
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                    completions.add(p.getName());
                }
            }
        }
        // 3) /radiation edit <id> ...
        else if (args.length == 2 && args[0].equalsIgnoreCase("edit")) {
            for (RadiationSource src : radiationManager.getAllSources()) {
                String idStr = String.valueOf(src.getId());
                if (idStr.startsWith(args[1])) {
                    completions.add(idStr);
                }
            }
        }
        // 4) /radiation edit <id> <field> ...
        else if (args.length == 3 && args[0].equalsIgnoreCase("edit")) {
            String[] fields = {"intensity", "radius", "power"};
            for (String f : fields) {
                if (f.startsWith(args[2].toLowerCase())) {
                    completions.add(f);
                }
            }
        }
        // 5) /radiation view <id|all>
        else if (args.length == 2 && args[0].equalsIgnoreCase("view")) {
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

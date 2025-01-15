package org.gezzon.grad;

import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.IntegerFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;
import org.bukkit.plugin.java.JavaPlugin;
import org.gezzon.grad.radiation.RadiationManager;
import org.gezzon.grad.radiation.RadiationTask;
import org.gezzon.grad.commands.RadiationCommand;
import org.gezzon.grad.listener.PlayerListener;



/**
 * Главный класс плагина Grad.
 * Здесь:
 *  - Загружается и инициализируется менеджер радиации;
 *  - Регистрируются команды и слушатели;
 *  - Запускается периодическая задача RadiationTask.
 */

public class Grad extends JavaPlugin {

    public static final IntegerFlag RADIATION_FLAG = new IntegerFlag("radiation");
    private RadiationManager radiationManager;
    private RadiationTask radiationTask;



    private static Grad instance;

    @Override
    public void onEnable() {
        // Создаём/сохраняем config.yml по умолчанию, если его нет
        saveDefaultConfig();
        instance = this;
        // Создаём менеджер радиации, читаем конфигурацию, загружаем источники
        radiationManager = new RadiationManager(this);
        radiationManager.init();

        // Запускаем периодическую задачу (каждую секунду)
        radiationTask = new RadiationTask(radiationManager);
        radiationTask.runTaskTimer(this, 20L, 20L); // старт через 1с, повтор каждые 20 тиков (1с)

        // Регистрируем слушатели
        getServer().getPluginManager().registerEvents(new PlayerListener(radiationManager), this);

        // Регистрируем единую команду /radiation
        getCommand("radiation").setExecutor(new RadiationCommand(this, radiationManager));
        getCommand("radiation").setTabCompleter(new RadiationCommand(this, radiationManager));
        registerFlags();
        getLogger().info("Grad плагин включён!");
    }

    private void registerFlags() {
        FlagRegistry registry = WorldGuard.getInstance().getFlagRegistry();
        try {
            if (registry.get("radiation") == null) {
                registry.register(RADIATION_FLAG);
                getLogger().info("Custom WorldGuard flag 'radiation' registered successfully.");
            }
        } catch (Exception e) {
            getLogger().warning("Failed to register custom flag 'radiation': " + e.getMessage());
            e.printStackTrace();
        }
    }



    @Override
    public void onDisable() {
        // При выключении сервера/плагина сохраняем все источники радиации
        radiationManager.saveSourcesToFile();
        getLogger().info("Grad плагин выключён!");
    }

    /**
     * Геттер менеджера радиации (например, если понадобится в других частях плагина).
     */
    public RadiationManager getRadiationManager() {
        return radiationManager;
    }
    public static Grad getInstance() {
        return instance;
    }
}

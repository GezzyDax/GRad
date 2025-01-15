package org.gezzon.grad;

import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;
import org.bukkit.plugin.java.JavaPlugin;
import org.gezzon.grad.radiation.RadiationManager;
import org.gezzon.grad.radiation.RadiationTask;
import org.gezzon.grad.commands.RadiationCommand;
import org.gezzon.grad.listener.PlayerListener;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;

import java.lang.reflect.Field;


/**
 * Главный класс плагина Grad.
 * Здесь:
 *  - Загружается и инициализируется менеджер радиации;
 *  - Регистрируются команды и слушатели;
 *  - Запускается периодическая задача RadiationTask.
 */

public class Grad extends JavaPlugin {

        public static final StateFlag RADIATION_1 = new StateFlag("radiation_1", false);
        public static final StateFlag RADIATION_2 = new StateFlag("radiation_2", false);
        public static final StateFlag RADIATION_3 = new StateFlag("radiation_3", false);
        public static final StateFlag RADIATION_4 = new StateFlag("radiation_4", false);
        public static final StateFlag RADIATION_5 = new StateFlag("radiation_5", false);
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
            registry.register(RADIATION_1);
            registry.register(RADIATION_2);
            registry.register(RADIATION_3);
            registry.register(RADIATION_4);
            registry.register(RADIATION_5);
            getLogger().info("Custom WorldGuard flags registered successfully.");
        } catch (Exception e) {
            getLogger().warning("Failed to register custom WorldGuard flags!");
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

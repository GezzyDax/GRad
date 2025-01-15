package org.gezzon.grad;

import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.IntegerFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagConflictException;
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

    private RadiationManager radiationManager;
    private RadiationTask radiationTask;
    public static IntegerFlag RADIATION_FLAG;


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
            // Создаём флаг с именем "radiation" (IntegerFlag)
            IntegerFlag flag = new IntegerFlag("radiation");
            registry.register(flag);
            // Устанавливаем наш статический флаг, если ошибок не возникло
            RADIATION_FLAG = flag;
            getLogger().info("Custom WorldGuard flag 'radiation' registered successfully.");
        } catch (FlagConflictException e) {
            // Если другой плагин уже зарегистрировал флаг с таким именем
            Flag<?> existing = registry.get("radiation");
            if (existing instanceof IntegerFlag) {
                RADIATION_FLAG = (IntegerFlag) existing;
                getLogger().warning("Custom flag 'radiation' already registered by another plugin. Using existing flag.");
            } else {
                // Если типы не совпадают, это критическая ошибка
                getLogger().severe("Flag conflict detected for 'radiation' with incompatible type. Plugin may not work correctly.");
            }
        } catch (Exception e) {
            getLogger().severe("An unexpected error occurred while registering the 'radiation' flag.");
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

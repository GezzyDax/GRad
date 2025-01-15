package org.gezzon.grad.listener;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.entity.Player;
import org.gezzon.grad.radiation.RadiationManager;
import org.gezzon.grad.radiation.RadiationSource;

public class PlayerListener implements Listener {

    private final RadiationManager radiationManager;

    public PlayerListener(RadiationManager radiationManager) {
        this.radiationManager = radiationManager;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // Сбрасываем радиацию при входе
        radiationManager.setPlayerRadiation(event.getPlayer().getUniqueId(), 0.0);
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();
        Player player = event.getPlayer();

        // Проверяем, является ли установленный блок радиационным
        if (block.getType() == radiationManager.getRadioactiveBlockType()) {
            Location loc = block.getLocation();
            // Добавляем радиационный источник вокруг блока
            RadiationSource source = radiationManager.addSource(
                    radiationManager.getBlockLevel(),
                    radiationManager.getBlockRadius(),
                    radiationManager.getBlockPower(),
                    loc
            );
            player.sendMessage("§aРадиоактивный блок установлен. ID источника: " + source.getId());
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Player player = event.getPlayer();

        // Проверяем, является ли разрушенный блок радиационным
        if (block.getType() == radiationManager.getRadioactiveBlockType()) {
            // Находим радиационный источник, связанный с этим блоком
            Location loc = block.getLocation();
            RadiationSource toRemove = null;

            for (RadiationSource source : radiationManager.getAllSources()) {
                if (source.getCenter().equals(loc)) {
                    toRemove = source;
                    break;
                }
            }

            if (toRemove != null) {
                // Удаляем источник радиации
                boolean removed = radiationManager.removeSource(toRemove.getId());
                if (removed) {
                    player.sendMessage("§aРадиоактивный блок удалён. Источник радиации удалён.");
                }
            }
        }
    }

    /**
     * Сброс радиации при смерти игрока.
     */
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        radiationManager.setPlayerRadiation(player.getUniqueId(), 0.0);
    }
}

package org.gezzon.grad.listener;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.entity.Player;
import org.gezzon.grad.radiation.RadiationManager;
import org.gezzon.grad.radiation.RadiationSource;
public class PlayerListener implements Listener {

    private final RadiationManager radiationManager;
    private final Material radioactiveBlockType;

    public PlayerListener(RadiationManager radiationManager) {
        this.radiationManager = radiationManager;
        this.radioactiveBlockType = radiationManager.getRadioactiveBlockType();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (radiationManager.getPlayerRadiation(player.getUniqueId()) > 0) {
            radiationManager.setPlayerRadiation(player.getUniqueId(), 0.0);
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (event.getBlock().getType() == radioactiveBlockType){
        Block block = event.getBlock();
        Player player = event.getPlayer();

        if (block.getType() == radiationManager.getRadioactiveBlockType()) {
            Location loc = block.getLocation();
            if (radiationManager.getSourceByLocation(loc) == null) { // Проверяем, есть ли уже источник
                RadiationSource source = radiationManager.addSource(
                        radiationManager.getBlockLevel(),
                        radiationManager.getBlockRadius(),
                        radiationManager.getBlockPower(),
                        loc
                );
                sendPlayerMessage(event.getPlayer(), "Радиоактивный блок установлен. ID источника: " + source.getId());
            } else {
                sendPlayerMessage(event.getPlayer(), "Источник радиации уже существует на этом месте.");
            }
        }
    }
    }

    private void sendPlayerMessage(Player player, String message) {
        player.sendMessage("§a" + message);
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.getBlock().getType() == radioactiveBlockType) {
            Block block = event.getBlock();
            Player player = event.getPlayer();

            if (block.getType() == radiationManager.getRadioactiveBlockType()) {
                Location loc = block.getLocation();
                RadiationSource source = radiationManager.getSourceByLocation(loc);

                if (source != null) {
                    boolean removed = radiationManager.removeSource(source.getId());
                    if (removed) {
                        sendPlayerMessage(event.getPlayer(), "Радиоактивный блок удалён. Источник радиации удалён.");
                    }
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

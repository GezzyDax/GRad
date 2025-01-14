package org.gezzon.grad.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.entity.Player;
import org.gezzon.grad.radiation.RadiationManager;

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

    /**
     * Сброс радиации при смерти игрока.
     */
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        radiationManager.setPlayerRadiation(player.getUniqueId(), 0.0);
    }
}

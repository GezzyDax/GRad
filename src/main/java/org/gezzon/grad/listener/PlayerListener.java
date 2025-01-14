package org.gezzon.grad.listener;


import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.gezzon.grad.radiation.RadiationManager;

/**
 * Слушатели событий, связанных с игроками:
 *  - При входе в игру обнуляем радиацию,
 *  - При движении (опционально) можно что-то проверять, но сейчас пусто.
 */
public class PlayerListener implements Listener {

    private final RadiationManager radiationManager;

    public PlayerListener(RadiationManager radiationManager) {
        this.radiationManager = radiationManager;
    }

    /**
     * При входе игрока на сервер (PlayerJoinEvent) сбрасываем накопленную радиацию.
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        radiationManager.setPlayerRadiation(event.getPlayer().getUniqueId(), 0.0);
    }

    /**
     * При движении игрока (PlayerMoveEvent) — пока ничего не делаем.
     * Но можно было бы вызывать, например, пересчёт радиации чаще, если нужно.
     */
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        // Пусто — логика при необходимости
    }
}

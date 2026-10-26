package me.darrionat.commandcooldown;

import me.darrionat.commandcooldown.cooldowns.Cooldown;
import me.darrionat.commandcooldown.cooldowns.PlayerCooldown;
import me.darrionat.commandcooldown.cooldowns.SavedCommand;
import me.darrionat.commandcooldown.interfaces.ICooldownsRepository;
import me.darrionat.commandcooldown.services.BypassService;
import me.darrionat.commandcooldown.services.CooldownService;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.junit.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class CompatibilityRegressionTest {
    @Test
    public void deployedPlayerCooldownFormatRetainsIdentityArgumentsAndDeadline() {
        UUID id = UUID.randomUUID();
        String stored = id + "/warp/shop main/45.0/4102444800000";
        PlayerCooldown restored = PlayerCooldown.parsePlayerCooldown(stored);
        assertNotNull(restored);
        assertEquals(id, restored.getPlayer());
        assertEquals(List.of("shop", "main"), restored.getCooldown().getArgs());
        assertEquals(4102444800000L, restored.getEnd());
        assertEquals(stored, restored.toString());
        assertFalse(restored.expired());
    }

    @Test
    public void aliasAndLongestArgumentCooldownRetainExistingSelection() {
        SavedCommand command = new SavedCommand("warp");
        command.addAlias("ewarp");
        Cooldown base = new Cooldown(command, 30);
        Cooldown shop = new Cooldown(command, "shop", 15);
        Cooldown shopMain = new Cooldown(command, "shop main", 5);
        command.addCooldown(base);
        command.addCooldown(shop);
        command.addCooldown(shopMain);
        ICooldownsRepository repository = mock(ICooldownsRepository.class);
        when(repository.getCommandCooldowns()).thenReturn(List.of(command));
        CooldownService service = new CooldownService(repository);
        assertSame(shopMain, service.findApplicableCooldown("EWARP SHOP MAIN"));
        assertSame(shop, service.findApplicableCooldown("warp shop other"));
        assertSame(base, service.findApplicableCooldown("warp elsewhere"));
        assertNull(service.findApplicableCooldown("untracked"));
    }

    @Test
    public void durationPermissionUsesLowestAllowedValueWithoutMutatingDefinition() {
        Player player = mock(Player.class);
        SavedCommand command = new SavedCommand("warp");
        Cooldown cooldown = new Cooldown(command, "shop", 30);
        when(player.getEffectivePermissions()).thenReturn(Set.of(
                new PermissionAttachmentInfo(player, "commandcooldown.warp_shop.10", null, true),
                new PermissionAttachmentInfo(player, "commandcooldown.warp_shop.5", null, true),
                new PermissionAttachmentInfo(player, "commandcooldown.warp_shop.1", null, false)));
        Cooldown selected = new CooldownService(mock(ICooldownsRepository.class))
                .permissionCooldownChange(player, cooldown);
        assertEquals(5, selected.getDuration(), 0);
        assertEquals(30, cooldown.getDuration(), 0);
        assertNotSame(cooldown, selected);
    }

    @Test
    public void bypassPermissionAndExplicitToggleRemainIndependent() {
        Player player = mock(Player.class);
        Cooldown cooldown = new Cooldown(new SavedCommand("warp"), "shop", 30);
        BypassService service = new BypassService();
        assertFalse(service.playerIsBypassing(cooldown, player));
        when(player.hasPermission("commandcooldown.bypass.warp_shop")).thenReturn(true);
        assertTrue(service.playerIsBypassing(cooldown, player));
        when(player.hasPermission("commandcooldown.bypass.warp_shop")).thenReturn(false);
        service.startBypassing(player);
        assertTrue(service.playerIsBypassing(cooldown, player));
        service.stopBypassing(player);
        assertFalse(service.playerIsBypassing(cooldown, player));
    }

    @Test
    public void failedInitializationCanDisableWithoutTouchingUninitializedStorage() {
        CommandCooldownPlugin plugin = mock(CommandCooldownPlugin.class, CALLS_REAL_METHODS);
        plugin.onDisable();
    }
}

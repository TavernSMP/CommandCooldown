package me.darrionat.commandcooldown;

import me.darrionat.commandcooldown.cooldowns.PlayerCooldown;
import me.darrionat.commandcooldown.interfaces.IPlayerCooldownsRepository;
import me.darrionat.commandcooldown.repository.LegacyCooldownMigration;
import me.darrionat.commandcooldown.repository.LocalPlayerCooldownsRepository;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class LegacyCooldownMigrationTest {
    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    private String stored(UUID player, String command, long deadline) {
        return player + "/" + command + "/*/300.0/" + deadline;
    }

    private File legacy(File folder, List<String> values) throws Exception {
        File file = new File(folder, "savedCooldowns.yml");
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("savedCooldowns", values);
        yaml.save(file);
        return file;
    }

    private LocalPlayerCooldownsRepository repository(File folder) {
        CommandCooldownPlugin plugin = mock(CommandCooldownPlugin.class);
        when(plugin.getDataFolder()).thenReturn(folder);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("migration-test"));
        return new LocalPlayerCooldownsRepository(plugin);
    }

    @Test
    public void activeLegacyRowsMergeWithoutShorteningTargetAndBackupRemains() throws Exception {
        File folder = temporary.newFolder();
        UUID player = UUID.randomUUID();
        String incoming = stored(player, "warp", 4102444800000L);
        String newer = stored(player, "warp", 4202444800000L);
        String unrelated = stored(player, "home", 4302444800000L);
        File source = legacy(folder, List.of(incoming, stored(player, "expired", 1L)));
        byte[] original = Files.readAllBytes(source.toPath());
        File target = new File(folder, "playerData/" + player + ".yml");
        YamlConfiguration existing = new YamlConfiguration();
        existing.set("cooldowns", List.of(newer, unrelated));
        existing.save(target);
        LegacyCooldownMigration.migrate(folder, repository(folder));
        List<String> actual = YamlConfiguration.loadConfiguration(target).getStringList("cooldowns");
        assertEquals(2, actual.size());
        assertTrue(actual.contains(newer));
        assertTrue(actual.contains(unrelated));
        assertFalse(source.exists());
        assertArrayEquals(original, Files.readAllBytes(new File(folder, "savedCooldowns.yml.pre-26.3").toPath()));
        byte[] saved = Files.readAllBytes(target.toPath());
        LegacyCooldownMigration.migrate(folder, repository(folder));
        assertArrayEquals(saved, Files.readAllBytes(target.toPath()));
    }

    @Test
    public void failedDestinationRetainsLegacyFileWithoutCompletionMarker() throws Exception {
        File folder = temporary.newFolder();
        File source = legacy(folder, List.of(stored(UUID.randomUUID(), "warp", 4102444800000L)));
        IPlayerCooldownsRepository destination = mock(IPlayerCooldownsRepository.class);
        doThrow(new IllegalStateException("disk unavailable")).when(destination).restoreCooldown(any());
        assertThrows(IllegalStateException.class, () -> LegacyCooldownMigration.migrate(folder, destination));
        assertTrue(source.exists());
        assertFalse(new File(folder, "savedCooldowns.yml.pre-26.3").exists());
    }

    @Test
    public void malformedLegacyRowPreventsAllDestinationWrites() throws Exception {
        File folder = temporary.newFolder();
        File source = legacy(folder, List.of(stored(UUID.randomUUID(), "warp", 4102444800000L), "invalid"));
        IPlayerCooldownsRepository destination = mock(IPlayerCooldownsRepository.class);
        assertThrows(IllegalStateException.class, () -> LegacyCooldownMigration.migrate(folder, destination));
        verifyNoInteractions(destination);
        assertTrue(source.exists());
    }

    @Test
    public void scalarLegacyListIsRejectedWithoutArchiving() throws Exception {
        File folder = temporary.newFolder();
        File source = new File(folder, "savedCooldowns.yml");
        Files.writeString(source.toPath(), "savedCooldowns: unexpected\n");
        IPlayerCooldownsRepository destination = mock(IPlayerCooldownsRepository.class);
        assertThrows(IllegalStateException.class, () -> LegacyCooldownMigration.migrate(folder, destination));
        verifyNoInteractions(destination);
        assertTrue(source.exists());
    }

    @Test
    public void malformedDestinationIsPreservedAndMigrationCanRetry() throws Exception {
        File folder = temporary.newFolder();
        UUID player = UUID.randomUUID();
        File source = legacy(folder, List.of(stored(player, "warp", 4102444800000L)));
        File target = new File(folder, "playerData/" + player + ".yml");
        target.getParentFile().mkdirs();
        Files.writeString(target.toPath(), "cooldowns: unexpected\n");
        byte[] original = Files.readAllBytes(target.toPath());
        assertThrows(IllegalStateException.class, () -> LegacyCooldownMigration.migrate(folder, repository(folder)));
        assertArrayEquals(original, Files.readAllBytes(target.toPath()));
        assertTrue(source.exists());
        Files.writeString(target.toPath(), "cooldowns: []\n");
        LegacyCooldownMigration.migrate(folder, repository(folder));
        assertFalse(source.exists());
        assertEquals(1, YamlConfiguration.loadConfiguration(target).getStringList("cooldowns").size());
    }

    @Test
    public void duplicateLegacyEntriesKeepLongestOriginalAbsoluteDeadline() throws Exception {
        File folder = temporary.newFolder();
        UUID player = UUID.randomUUID();
        legacy(folder, List.of(stored(player, "warp", 4202444800000L), stored(player, "warp", 4102444800000L)));
        LegacyCooldownMigration.migrate(folder, repository(folder));
        List<String> values = YamlConfiguration.loadConfiguration(new File(folder, "playerData/" + player + ".yml"))
                .getStringList("cooldowns");
        assertEquals(1, values.size());
        assertEquals(4202444800000L, PlayerCooldown.parsePlayerCooldown(values.getFirst()).getEnd());
    }
}

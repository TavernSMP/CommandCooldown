package me.darrionat.commandcooldown.repository;

import me.darrionat.commandcooldown.cooldowns.PlayerCooldown;
import me.darrionat.commandcooldown.interfaces.IPlayerCooldownsRepository;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class LegacyCooldownMigration {
    private LegacyCooldownMigration() {
    }

    public static void migrate(File directory, IPlayerCooldownsRepository destination) {
        Path source = directory.toPath().resolve("savedCooldowns.yml");
        if (!Files.isRegularFile(source)) {
            return;
        }
        try {
            YamlConfiguration file = new YamlConfiguration();
            file.load(source.toFile());
            if (file.contains("savedCooldowns") && !file.isList("savedCooldowns")) {
                throw new IllegalArgumentException("Legacy savedCooldowns must be a list");
            }
            List<PlayerCooldown> active = new ArrayList<>();
            for (String value : file.getStringList("savedCooldowns")) {
                PlayerCooldown cooldown = PlayerCooldown.parsePlayerCooldown(value);
                if (cooldown == null || !Double.isFinite(cooldown.getCooldown().getDuration())
                        || cooldown.getCooldown().getDuration() < 0 || cooldown.getEnd() < 0) {
                    throw new IllegalArgumentException("Invalid legacy cooldown; original file retained");
                }
                if (!cooldown.expired()) {
                    active.add(cooldown);
                }
            }
            for (PlayerCooldown cooldown : active) {
                destination.restoreCooldown(cooldown);
            }
            Path backup = directory.toPath().resolve("savedCooldowns.yml.pre-26.3");
            if (Files.exists(backup)) {
                backup = directory.toPath().resolve("savedCooldowns.yml.pre-26.3-" + System.nanoTime());
            }
            Files.move(source, backup);
        } catch (Exception failure) {
            throw new IllegalStateException("Legacy cooldown migration failed; savedCooldowns.yml retained", failure);
        }
    }
}

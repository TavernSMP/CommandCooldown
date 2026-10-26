package me.darrionat.commandcooldown.repository;

import me.darrionat.commandcooldown.CommandCooldownPlugin;
import me.darrionat.commandcooldown.cooldowns.Cooldown;
import me.darrionat.commandcooldown.cooldowns.PlayerCooldown;
import me.darrionat.commandcooldown.interfaces.IPlayerCooldownsRepository;
import me.darrionat.pluginlib.files.Config;
import me.darrionat.pluginlib.files.ConfigBuilder;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class LocalPlayerCooldownsRepository implements IPlayerCooldownsRepository {

    private final CommandCooldownPlugin plugin;

    //    private final Set<PlayerCooldown> cooldowns = new HashSet<>();
    private final HashMap<UUID, Config> playerConfigMap = new HashMap<>();

    public LocalPlayerCooldownsRepository(CommandCooldownPlugin plugin) {
        this.plugin = plugin;
        init();
    }

    public void init() {
        playerConfigMap.clear();
    }

    @Override
    public void restoreCooldown(PlayerCooldown incoming) {
        Config config = getPlayerDataConfig(incoming.getPlayer());
        YamlConfiguration file = new YamlConfiguration();
        try {
            file.load(config.getFile());
        } catch (Exception failure) {
            throw new IllegalStateException("Could not read destination player cooldowns", failure);
        }
        if (file.contains("cooldowns") && !file.isList("cooldowns")) {
            throw new IllegalStateException("Destination cooldowns must be a list");
        }
        List<String> merged = new ArrayList<>();
        long deadline = incoming.getEnd();
        for (String value : file.getStringList("cooldowns")) {
            PlayerCooldown existing = PlayerCooldown.parsePlayerCooldown(value);
            if (existing == null) {
                throw new IllegalStateException("Invalid destination cooldown during legacy migration");
            }
            if (existing.equals(incoming)) {
                deadline = Math.max(deadline, existing.getEnd());
            } else {
                merged.add(value);
            }
        }
        merged.add(new PlayerCooldown(incoming.getPlayer(), incoming.getCooldown(), deadline).toString());
        file.set("cooldowns", merged);
        Path pending = null;
        try {
            pending = Files.createTempFile(config.getFile().toPath().getParent(), "cooldown-migration-", ".yml");
            file.save(pending.toFile());
            Files.move(pending, config.getFile().toPath(), StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
            playerConfigMap.remove(incoming.getPlayer());
        } catch (Exception failure) {
            throw new IllegalStateException("Could not atomically persist migrated player cooldowns", failure);
        } finally {
            if (pending != null) {
                try {
                    Files.deleteIfExists(pending);
                } catch (java.io.IOException failure) {
                    plugin.getLogger().warning("Could not remove cooldown migration temporary file");
                }
            }
        }
    }

    private void setupPlayerDataFile(UUID uuid) {
        ConfigBuilder builder = new ConfigBuilder(plugin, uuid.toString() + ".yml", PLAYER_DATA_DIR);
        Config config = builder.build();
        playerConfigMap.put(uuid, config);
    }

    private boolean playerFileExists(UUID uuid) {
        return new ConfigBuilder(plugin, uuid.toString() + ".yml", PLAYER_DATA_DIR).exists();
    }

    private Config getPlayerDataConfig(UUID uuid) {
        if (!playerConfigMap.containsKey(uuid) || !playerFileExists(uuid)) {
            setupPlayerDataFile(uuid);
        }
        return playerConfigMap.get(uuid);
    }

    /**
     * Updates the player data to not contain expired cooldowns
     *
     * @param uuid The uuid associated with the player data.
     */
    private void removeExpiredCooldowns(UUID uuid) {
        Config config = getPlayerDataConfig(uuid);
        FileConfiguration fileConfiguration = config.getFileConfiguration();
        List<String> list = fileConfiguration.getStringList("cooldowns");
        List<String> toSave = new ArrayList<>();
        // Look through currently saved PlayerCooldowns for the given UUID, and then only save non-expired PlayerCooldowns.
        for (String playerCooldownAsString : list) {
            PlayerCooldown playerCooldown = PlayerCooldown.parsePlayerCooldown(playerCooldownAsString);
            if (playerCooldown == null) {
                plugin.getErrorHandler().parsePlayerCooldownError(uuid);
                continue;
            }
            if (!playerCooldown.expired()) {
                toSave.add(playerCooldownAsString);
            }
        }
        fileConfiguration.set("cooldowns", toSave);
        config.save(fileConfiguration);
    }

    @Override
    public void giveCooldown(Player p, Cooldown cooldown) {
        if (cooldown == null) return;
        long current = System.currentTimeMillis();
        long cooldownMS = (long) (cooldown.getDuration() * 1000);
        long end = current + cooldownMS;
        PlayerCooldown playerCooldown = new PlayerCooldown(p.getUniqueId(), cooldown, end);

        // Get player Config and FileConfiguration
        Config config = getPlayerDataConfig(p.getUniqueId());
        FileConfiguration fileConfiguration = config.getFileConfiguration();

        // Get list of cooldowns
        List<String> list = fileConfiguration.getStringList("cooldowns");
        // Update list and save config
        list.add(playerCooldown.toString());
        fileConfiguration.set("cooldowns", list);
        config.save(fileConfiguration);
    }

    @Override
    public void removePlayerCooldowns(Player p) {
        Config config = getPlayerDataConfig(p.getUniqueId());
        FileConfiguration fileConfiguration = config.getFileConfiguration();
        // Update list and save config
        fileConfiguration.set("cooldowns", null);
        config.save(fileConfiguration);
    }

    @Override
    public double getRemainingCooldown(Player p, Cooldown cooldown) {
        return getRemainingCooldown(p.getUniqueId(), cooldown);
    }

    @Override
    public double getRemainingCooldown(UUID uuid, Cooldown cooldown) {
        PlayerCooldown cd = plugin.matchCooldownToPlayerCooldown(uuid, cooldown);
        if (cd == null) return 0;
        long end = cd.getEnd();
        long rem = end - System.currentTimeMillis();
        return Math.max(rem / 1000, 0);
    }

    @Override
    public boolean playerHasCooldown(Player p, Cooldown cooldown) {
        UUID uuid = p.getUniqueId();
        PlayerCooldown playerCooldown = plugin.matchCooldownToPlayerCooldown(uuid, cooldown);
        if (playerCooldown == null) return false;
        boolean expired = playerCooldown.expired();
        if (expired) {
            // Cooldown is expired, remove from list
            Config config = getPlayerDataConfig(uuid);
            FileConfiguration fileConfiguration = config.getFileConfiguration();
            List<String> list = fileConfiguration.getStringList("cooldowns");
            list.remove(playerCooldown.toString());
            fileConfiguration.set("cooldowns", list);
            config.save(fileConfiguration);
        }
        return !expired;
    }

    @Override
    public List<PlayerCooldown> getAllPlayerCooldownsForPlayer(Player p) {
        return getAllPlayerCooldownsForPlayer(p.getUniqueId());
    }

    @Override
    public List<PlayerCooldown> getAllPlayerCooldownsForPlayer(UUID uuid) {
        removeExpiredCooldowns(uuid);
        List<PlayerCooldown> toReturn = new ArrayList<>();

        Config config = getPlayerDataConfig(uuid);
        FileConfiguration fileConfiguration = config.getFileConfiguration();
        List<String> list = fileConfiguration.getStringList("cooldowns");

        for (String playerCooldownAsString : list) {
            PlayerCooldown playerCooldown = PlayerCooldown.parsePlayerCooldown(playerCooldownAsString);
            if (playerCooldown == null) {
                plugin.getErrorHandler().parsePlayerCooldownError(uuid);
                continue;
            }
            assert playerCooldown.getPlayer() == uuid;
            toReturn.add(playerCooldown);
        }
        return toReturn;
    }
}

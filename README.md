# Command Cooldown — TavernSMP

This fork targets **Paper 26.3 / Java 25** and is based on upstream [Darrionat/Command-Cooldown](https://github.com/Darrionat/Command-Cooldown), commit `cfcd2baf197fc8a636dbc044ecd8680d7ee0de83` (3.0.8).

It updates the private shaded PluginLib dependency for modern Paper and automatically imports legacy `savedCooldowns.yml` entries into the configured YAML or MySQL store. Original absolute expiry times are retained, newer destination deadlines are preserved, and the legacy file becomes a `.pre-26.3` backup only after successful destination writes. Retries are idempotent. Existing command aliases, permissions, and chat-based editor behavior are retained.

## Build this fork

Use JDK 25 and Maven 3.9 or newer. The patched [PluginLib](https://github.com/Darrionat/PluginLib) source is included under `dependencies/PluginLib-main` with its MIT license, so no unpublished prebuilt dependency is required:

```sh
mvn -f dependencies/PluginLib-main/pom.xml clean install
mvn clean verify
```

The deployable plugin is `target/CommandCooldown-3.0.8-26.3-tavern.1.jar`. Install only that JAR; PluginLib is shaded into it. Keep the existing plugin folder and restart the server. No manual configuration changes are required.

The clean build passes 11 regression tests. The exact maintenance JAR passed 42 assertions on isolated Paper 26.3 build 223 with YAML and disposable MariaDB, including command blocking and bypass, editor callbacks, migration, and restart persistence. Editor probes used synthetic Player objects rather than a rendered network client. No production services were used.

The upstream documentation below describes historical releases; this maintenance build supports only Paper 26.3 / Java 25.

---

# Upstream Command Cooldown

Created by [Darrion][Darrionat], CommandCooldown is a Spigot plugin is capable of adding cooldowns to any command.
Originally published in December 2019,
CommandCooldown has gone through a multitude of changes to end where it is.

### The Editor

To make CommandCooldown as intuitive as possible, the CommandCooldown has a GUI editor
where almost all administrative action takes place.

To open the editor use `/cc editor`.

### MySQL Support

CommandCooldown can connect to a database to store player cooldowns.
This allows for players to have a cooldown on the same command across BungeeCord.
Database connections can be enabled and configured in the `config.yml` file.

### Dependencies

This project uses the library [PluginLib][PluginLib]. PluginLib was also created by Darrionat and is licensed under MIT.

<!-- Links -->

[Darrionat]: https://github.com/Darrionat

[PluginLib]: https://github.com/Darrionat/PluginLib

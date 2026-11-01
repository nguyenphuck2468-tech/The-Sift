"""P2-C i18n sweep: route remaining player-facing strings through Messages (vi/en).

Diagnostic admin dumps (/sift doctor status(), provider summary(), API probes) stay
English on purpose: they are server-log material, not gameplay chat.
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
F = "src/main/java/com/gamepathics/thesift/foundation/"


def patch(path, pairs):
    p = ROOT / path
    text = p.read_text(encoding="utf-8")
    for old, new in pairs:
        count = text.count(old)
        if count == 0:
            print(f"  SKIP (already applied or absent) in {path}: {old[:90]!r}")
            continue
        text = text.replace(old, new)
        if count > 1:
            print(f"  note: needle applied {count}x in {path}: {old[:80]!r}")
    p.write_text(text, encoding="utf-8")
    print(f"patched {path} ({len(pairs)} edits)")


# --- Messages: action-bar helper -------------------------------------------------------
patch(F + "Messages.java", [
    ("import org.bukkit.command.CommandSender;",
     "import org.bukkit.command.CommandSender;\nimport org.bukkit.entity.Player;"),
    ("""    public static void send(CommandSender sender, String key, String englishDefault) {
        sender.sendMessage(PREFIX + get(key, englishDefault));
    }""",
     """    public static void send(CommandSender sender, String key, String englishDefault) {
        sender.sendMessage(PREFIX + get(key, englishDefault));
    }

    /**
     * Action bars carry no chat prefix; colour codes live in the translated string itself.
     */
    public static void actionBar(Player player, String key, String englishDefault) {
        player.sendActionBar(get(key, englishDefault));
    }"""),
])

# --- TheSiftFoundationPlugin -----------------------------------------------------------
patch(F + "TheSiftFoundationPlugin.java", [
    ('sender.sendMessage("The Sift config was reloaded. Restart the server to apply content flag changes.");',
     'Messages.send(sender, "sift.reload-done",\n                    "The Sift config was reloaded. Restart the server to apply content flag changes.");'),
    ('sender.sendMessage("Usage: /sift datapack <list|enable|disable> [name]");',
     'Messages.send(sender, "sift.datapack-usage", "Usage: /sift datapack <list|enable|disable> [name]");'),
    ('sender.sendMessage("The Sift resource pack sent to you (" + (force ? "forced" : "additive") + ").");',
     'sender.sendMessage(Messages.get("sift.resourcepack-sent",\n                        "The Sift resource pack sent to you (%mode%).").replace("%mode%", force ? "forced" : "additive"));'),
    ('''sender.sendMessage("No resource-pack provider plugin detected and resource-pack.server-url"
                            + " is empty; set an http(s) download URL or install a provider before broadcasting.");''',
     '''Messages.send(sender, "sift.resourcepack-no-provider",
                            "No resource-pack provider plugin detected and resource-pack.server-url is empty; set an http(s) download URL or install a provider before broadcasting.");'''),
    ('sender.sendMessage("The Sift foundation: " + getPluginMeta().getVersion());',
     'sender.sendMessage(Messages.get("sift.version", "The Sift foundation: %version%")\n                    .replace("%version%", getPluginMeta().getVersion()));'),
    ('sender.sendMessage("Runtime target: Paper 26.3 / Paper API 26.3.build.218-stable");',
     'Messages.send(sender, "sift.runtime-target", "Runtime target: Paper 26.3 / Paper API 26.3.build.218-stable");'),
    ('sender.sendMessage("The Sift dimension is disabled.");',
     'Messages.send(sender, "sift.dimension-disabled", "The Sift dimension is disabled.");'),
    ('sender.sendMessage("The Sift dimension is locked because required provider IDs are missing. Run /sift doctor; see integrations/README.md for setup instructions.");',
     'Messages.send(sender, "sift.dimension-locked", "The Sift dimension is locked because required provider IDs are missing. Run /sift doctor; see integrations/README.md for setup instructions.");'),
    ('sender.sendMessage("The Sift dimension is ready. Players can enter with /sift dimension.");',
     'Messages.send(sender, "sift.dimension-ready", "The Sift dimension is ready. Players can enter with /sift dimension.");'),
    ('sender.sendMessage("The Sift dimension could not be loaded; check the datapack and server log.");',
     'Messages.send(sender, "sift.dimension-load-failed", "The Sift dimension could not be loaded; check the datapack and server log.");'),
    ('sender.sendMessage("Only players can locate an Ancient City gate.");',
     'Messages.send(sender, "sift.locate-console-only", "Only players can locate an Ancient City gate.");'),
    ('sender.sendMessage("Ancient City gates are disabled.");',
     'sender.sendMessage(Messages.get("gate.disabled", "Ancient City gates are disabled."));'),
    ('sender.sendMessage("Only players can return from The Sift.");',
     'Messages.send(sender, "sift.return-console-only", "Only players can return from The Sift.");'),
    ('sender.sendMessage("Ichor content is disabled.");',
     'Messages.send(sender, "sift.ichor-disabled", "Ichor content is disabled.");'),
    ('sender.sendMessage("Rift content is disabled.");',
     'Messages.send(sender, "sift.rift-disabled", "Rift content is disabled.");'),
    ('sender.sendMessage("Charoite/Siftite content is disabled.");',
     'Messages.send(sender, "sift.siftite-disabled", "Charoite/Siftite content is disabled.");'),
    ('sender.sendMessage("Singer content is disabled.");',
     'Messages.send(sender, "sift.singer-disabled", "Singer content is disabled.");'),
    ('sender.sendMessage("Echo Golem content is disabled.");',
     'Messages.send(sender, "sift.echo-golem-disabled", "Echo Golem content is disabled.");'),
    ('sender.sendMessage("Sift creature content is disabled.");',
     'Messages.send(sender, "sift.creature-disabled", "Sift creature content is disabled.");'),
    ('sender.sendMessage("Player not found.");',
     'Messages.send(sender, "sift.player-not-found", "Player not found.");'),
    ('sender.sendMessage("Item ID \'ichor_bottle\' is unresolved by the selected provider. Run /sift doctor.");',
     'Messages.send(sender, "sift.bottle-unresolved", "Item ID \'ichor_bottle\' is unresolved by the selected provider. Run /sift doctor.");'),
    ('sender.sendMessage("Item ID \'rift_key\' is unavailable in strict provider mode. Run /sift doctor.");',
     'Messages.send(sender, "sift.key-unavailable", "Item ID \'rift_key\' is unavailable in strict provider mode. Run /sift doctor.");'),
    ('sender.sendMessage("Item ID \'" + args[1].toLowerCase() + "\' is unknown or unresolved by the selected provider. Run /sift doctor.");',
     'sender.sendMessage(Messages.get("sift.item-unresolved", "Item ID \'%item%\' is unknown or unresolved by the selected provider. Run /sift doctor.")\n                        .replace("%item%", args[1].toLowerCase()));'),
    ('sender.sendMessage("Gave Ichor Bottle to " + target.getName() + ".");',
     'sender.sendMessage(Messages.get("sift.gave", "Gave %item% to %target%.")\n                        .replace("%item%", "Ichor Bottle").replace("%target%", target.getName()));'),
    ('sender.sendMessage("Gave Rift Key to " + target.getName() + ".");',
     'sender.sendMessage(Messages.get("sift.gave", "Gave %item% to %target%.")\n                            .replace("%item%", "Rift Key").replace("%target%", target.getName()));'),
    ('sender.sendMessage("Gave " + args[1] + " to " + target.getName() + ".");',
     'sender.sendMessage(Messages.get("sift.gave", "Gave %item% to %target%.")\n                        .replace("%item%", args[1]).replace("%target%", target.getName()));'),
    ('sender.sendMessage("Usage: /sift summon singer <player>");',
     'Messages.send(sender, "sift.summon-usage", "Usage: /sift summon singer <player>");'),
    ('sender.sendMessage("Usage: /sift summon echo_golem <player>");',
     'Messages.send(sender, "sift.summon-usage-echo", "Usage: /sift summon echo_golem <player>");'),
    ('sender.sendMessage("Usage: /sift summon rift <player>");',
     'Messages.send(sender, "sift.summon-usage-rift", "Usage: /sift summon rift <player>");'),
    ('sender.sendMessage("Usage: /sift summon <species> [player]");',
     'Messages.send(sender, "sift.summon-usage-generic", "Usage: /sift summon <species> [player]");'),
    ('sender.sendMessage("Summoned a Singer near " + target.getName() + ".");',
     'sender.sendMessage(Messages.get("sift.summoned", "Summoned %mob% near %target%.")\n                        .replace("%mob%", "a Singer").replace("%target%", target.getName()));'),
    ('sender.sendMessage("Summoned an Echo Golem near " + target.getName() + ".");',
     'sender.sendMessage(Messages.get("sift.summoned", "Summoned %mob% near %target%.")\n                        .replace("%mob%", "an Echo Golem").replace("%target%", target.getName()));'),
    ('sender.sendMessage("Opened a Rift near " + target.getName() + ".");',
     'sender.sendMessage(Messages.get("sift.summoned", "Summoned %mob% near %target%.")\n                        .replace("%mob%", "a Rift").replace("%target%", target.getName()));'),
    ('sender.sendMessage("Summoned Sift creature " + args[1] + " near " + target.getName() + ".");',
     'sender.sendMessage(Messages.get("sift.summoned", "Summoned %mob% near %target%.")\n                        .replace("%mob%", "Sift creature " + args[1]).replace("%target%", target.getName()));'),
    ('} else sender.sendMessage("Rift content is currently disabled.");',
     '} else Messages.send(sender, "sift.rift-disabled", "Rift content is currently disabled.");'),
    ('sender.sendMessage("Unknown Sift creature: " + args[1]);',
     'sender.sendMessage(Messages.get("sift.unknown-creature", "Unknown Sift creature: %species%")\n                        .replace("%species%", args[1]));'),
    ('sender.sendMessage("Usage: /sift <doctor|reload|datapack <list|enable|disable>|resourcepack [force]|dimension|locate|return|give <item> <player>|summon <species> [player]>");',
     'Messages.send(sender, "sift.usage", "Usage: /sift <doctor|reload|datapack <list|enable|disable>|resourcepack [force]|dimension|locate|return|give <item> <player>|summon <species> [player]>");'),
])

# --- RiftManager -----------------------------------------------------------------------
patch(F + "RiftManager.java", [
    ('String message = "Rift blocked: required providers for The Sift are unavailable. Run /sift doctor.";',
     'String message = Messages.get("rift.blocked-providers",\n                    "Rift blocked: required providers for The Sift are unavailable. Run /sift doctor.");'),
    ('String message = "Rift blocked: its destination world could not be loaded. Check the server log and /sift doctor.";',
     'String message = Messages.get("rift.blocked-world",\n                    "Rift blocked: its destination world could not be loaded. Check the server log and /sift doctor.");'),
    ('target.sendMessage("\\u00a75A Rift tears open somewhere nearby...");',
     'Messages.send(target, "rift.tear-open", "\\u00a75A Rift tears open somewhere nearby...");'),
    ('notifyOpener("§5The rift begins to tear open...");',
     'notifyOpener(Messages.get("rift.tearing-open", "§5The rift begins to tear open..."));'),
    ('player.sendMessage("§5The Rift carries you between worlds.");',
     'Messages.send(player, "rift.carries", "§5The Rift carries you between worlds.");'),
    ('event.getPlayer().sendActionBar("§bSift note: " + (next + 1));',
     'player.sendActionBar(Messages.get("sonorous.note", "§bSift note: %note%")\n                        .replace("%note%", String.valueOf(next + 1)));'),
    ('player.sendActionBar("§7The song echoes, but no Reinforced Deepslate frame answers.");',
     'Messages.actionBar(player, "sonorous.no-frame", "§7The song echoes, but no Reinforced Deepslate frame answers.");'),
    ('player.sendActionBar("§7The portal is already open. Play the song backwards to close it.");',
     'Messages.actionBar(player, "sonorous.already-open", "§7The portal is already open. Play the song backwards to close it.");'),
    ('if (player.isOnline()) player.sendActionBar("§cThe Rift could not open; check provider readiness.");',
     'if (player.isOnline()) Messages.actionBar(player, "rift.open-failed", "§cThe Rift could not open; check provider readiness.");'),
    ('player.sendActionBar("§bThe Sonorous song is complete.");',
     'Messages.actionBar(player, "sonorous.complete", "§bThe Sonorous song is complete.");'),
    ('player.sendActionBar("§5The portal folds back into the deepslate.");',
     'Messages.actionBar(player, "sonorous.closed", "§5The portal folds back into the deepslate.");'),
])

# --- AncientCityGateManager ------------------------------------------------------------
patch(F + "AncientCityGateManager.java", [
    ('''player.sendMessage("Nearest The Sift gate: " + target.getBlockX() + ", " + target.getBlockY()
                + ", " + target.getBlockZ() + ". Your compass now points to it.");''',
     '''player.sendMessage(Messages.get("gate.nearest",
                "Nearest The Sift gate: %coords%. Your compass now points to it.").replace("%coords%",
                target.getBlockX() + ", " + target.getBlockY() + ", " + target.getBlockZ()));'''),
    ('''event.getPlayer().sendMessage("The Sift gate cannot awaken: required providers are unresolved. Run /sift doctor.");''',
     '''Messages.send(event.getPlayer(), "gate.cannot-awaken",
                "The Sift gate cannot awaken: required providers are unresolved. Run /sift doctor.");'''),
    ('''event.getPlayer().sendMessage("The Singer could not appear. Check the required mob provider with /sift doctor.");''',
     '''Messages.send(event.getPlayer(), "gate.singer-failed",
                "The Singer could not appear. Check the required mob provider with /sift doctor.");'''),
    ('''event.getPlayer().sendMessage("The Singer's song awakens the Sonorous Deepslate.");''',
     '''Messages.send(event.getPlayer(), "gate.awakened", "The Singer's song awakens the Sonorous Deepslate.");'''),
    ('''event.getPlayer().sendMessage("Play a Goat Horn near the Ancient City gate to awaken its Sonorous Deepslate first.");''',
     '''Messages.send(event.getPlayer(), "gate.horn-first",
                "Play a Goat Horn near the Ancient City gate to awaken its Sonorous Deepslate first.");'''),
    ('event.getPlayer().sendActionBar("§cAwaken this city\'s Sonorous Deepslate with a Goat Horn first.");',
     'Messages.actionBar(event.getPlayer(), "sonorous.awaken-first", "§cAwaken this city\'s Sonorous Deepslate with a Goat Horn first.");'),
    ('event.getPlayer().sendActionBar("§bSift note: " + (next + 1));',
     'event.getPlayer().sendActionBar(Messages.get("sonorous.note", "§bSift note: %note%")\n                    .replace("%note%", String.valueOf(next + 1)));'),
    ('event.getPlayer().sendActionBar("§cWrong note; the gate melody resets.");',
     'Messages.actionBar(event.getPlayer(), "sonorous.wrong-note", "§cWrong note; the gate melody resets.");'),
    ('event.getPlayer().sendMessage("§bThe Ancient City portal opens into The Sift!");',
     'Messages.send(event.getPlayer(), "gate.opened", "§bThe Ancient City portal opens into The Sift!");'),
    ('''} else event.getPlayer().sendActionBar("§bAncient City melody: "
                    + melodies.progress(event.getPlayer().getUniqueId(), gate.key) + "/8");''',
     '''} else event.getPlayer().sendActionBar(Messages.get("sonorous.melody", "§bAncient City melody: %progress%/8")
                    .replace("%progress%", String.valueOf(melodies.progress(event.getPlayer().getUniqueId(), gate.key))));'''),
    ('''player.sendMessage("Place Note Blocks on all 8 Sonorous Deepslate blocks; missing " + missing.size() + ".");''',
     '''player.sendMessage(Messages.get("gate.missing-notes",
                    "Place Note Blocks on all 8 Sonorous Deepslate blocks; missing %count%.")
                    .replace("%count%", String.valueOf(missing.size())));'''),
    ('} else player.sendMessage("Set the 8 note blocks, then play 1 → 3 → 7 → 6 → 5 → 2 → 4 → 8.");',
     '} else Messages.send(player, "gate.set-notes", "Set the 8 note blocks, then play 1 → 3 → 7 → 6 → 5 → 2 → 4 → 8.");'),
    ('''opener.sendMessage("The Sift portal is locked because provider requirements are unresolved. Run /sift doctor.");''',
     '''Messages.send(opener, "gate.locked", "The Sift portal is locked because provider requirements are unresolved. Run /sift doctor.");'''),
    ('''opener.sendMessage("No safe 1:1 return-portal site is available at these X/Z coordinates in The Sift.");''',
     '''Messages.send(opener, "gate.no-return-site",
                "No safe 1:1 return-portal site is available at these X/Z coordinates in The Sift.");'''),
    ('''opener.sendMessage("The 1:1 return frame could not be built safely; no blocks were changed.");''',
     '''Messages.send(opener, "gate.return-frame-failed",
                "The 1:1 return frame could not be built safely; no blocks were changed.");'''),
    ('''player.sendMessage(source.world.getKey().equals(SiftDimensionManager.DIMENSION_KEY)
                ? "You returned to the Overworld through the Ancient City gate."
                : "You crossed the Ancient City gate into The Sift.");''',
     '''player.sendMessage(source.world.getKey().equals(SiftDimensionManager.DIMENSION_KEY)
                ? Messages.get("gate.returned", "You returned to the Overworld through the Ancient City gate.")
                : Messages.get("gate.crossed", "You crossed the Ancient City gate into The Sift."));'''),
])

# --- ProviderAccessGuard / SoulBlockListener / SiftiteRecoveryManager / ResourcePackBridge
patch(F + "ProviderAccessGuard.java", [
    ('event.getPlayer().sendMessage("The Sift is locked because required provider IDs are unavailable. Run /sift doctor.");',
     'Messages.send(event.getPlayer(), "sift.locked", "The Sift is locked because required provider IDs are unavailable. Run /sift doctor.");'),
    ('player.sendMessage("The Sift is locked, and no safe world is loaded. Ask an administrator to load a map chunk or connect an admin.");',
     'Messages.send(player, "sift.locked-no-world", "The Sift is locked, and no safe world is loaded. Ask an administrator to load a map chunk or connect an admin.");'),
    ('player.sendMessage("The Sift is locked because required provider IDs are unavailable. Run /sift doctor.");',
     'Messages.send(player, "sift.locked", "The Sift is locked because required provider IDs are unavailable. Run /sift doctor.");'),
])
patch(F + "SoulBlockListener.java", [
    ('event.getPlayer().sendMessage("Soul Block item is unavailable from the configured provider. Run /sift doctor.");',
     'Messages.send(event.getPlayer(), "soul.no-provider", "Soul Block item is unavailable from the configured provider. Run /sift doctor.");'),
])
patch(F + "SiftiteRecoveryManager.java", [
    ('player.sendMessage("§5A Mini Rift returned your Siftite equipment.");',
     'Messages.send(player, "siftite.mini-rift-returned", "§5A Mini Rift returned your Siftite equipment.");'),
])
patch(F + "ResourcePackBridge.java", [
    ('''origin.sendMessage("The Sift resource pack sent to " + Bukkit.getOnlinePlayers().size()
                    + " player(s) via " + (provider == null ? "native Bukkit API" : provider.getName()) + ".");''',
     '''origin.sendMessage(Messages.get("sift.resourcepack-broadcast",
                    "The Sift resource pack sent to %count% player(s) via %provider%.")
                    .replace("%count%", String.valueOf(Bukkit.getOnlinePlayers().size()))
                    .replace("%provider%", provider == null ? "native Bukkit API" : provider.getName()));'''),
])
print("ALL JAVA PATCHES APPLIED")

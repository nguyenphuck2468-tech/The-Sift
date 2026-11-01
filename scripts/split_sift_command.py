"""One-off R5: split handleSiftCommand's if-chain into a Brigadier-style dispatch table.

Faithful transformation: each branch body moves verbatim into its own void executor
(only `return true;` becomes `return;`); the dispatcher keeps the exact arg-length
guards so /sift <sub> with wrong arity still falls through to the usage line.
"""
import re
from pathlib import Path

p = Path("src/main/java/com/gamepathics/thesift/foundation/TheSiftFoundationPlugin.java")
lines = p.read_text(encoding="utf-8").splitlines(keepends=True)

start = next(i for i, l in enumerate(lines) if "private boolean handleSiftCommand" in l)
# Method ends after the usage line: find it, then the following "return true;" and "    }".
usage = next(i for i in range(start, len(lines)) if '"sift.usage"' in lines[i])
end = usage + 3  # usage line, "return true;", "    }"
assert lines[end - 2].strip() == "return true;", lines[end - 2]
assert lines[end - 1].strip() == "}", lines[end - 1]

branch_re = re.compile(r'^        if \(args\.length')
starts = [i for i in range(start + 1, usage) if branch_re.match(lines[i])]
assert len(starts) == 14, starts

bodies = []
for idx, s in enumerate(starts):
    e = starts[idx + 1] if idx + 1 < len(starts) else usage
    # Collect the full condition FIRST (advancing s past continuation lines), then the
    # body starts after it — otherwise the continuation line lands in both places.
    cond_lines = [lines[s]]
    while cond_lines[-1].count("(") > cond_lines[-1].count(")"):
        s += 1
        cond_lines.append(lines[s])
    body = lines[s + 1:e]
    # The slice ends with this branch's closing brace, then a lone "return true;" line.
    while body and body[-1].strip() == "":
        body.pop()
    assert body[-1].strip() == "}", (s, body[-3:])
    body.pop()
    while body and body[-1].strip() == "":
        body.pop()
    assert body[-1].strip() == "return true;", (s, body[-3:])
    body = body[:-1]  # drop the branch's trailing return true
    body = [l.replace("return true;", "return;") for l in body]
    bodies.append((cond_lines, body))

names = ["executeReload", "executeDatapack", "executeResourcepack", "executeDoctor",
         "executeDimension", "executeLocate", "executeReturn", "executeGive",
         "executeGive", "executeGive", "executeSummon", "executeSummon",
         "executeSummon", "executeSummon"]
assert len(bodies) == len(names)

methods = []
merged = {}
for (cond, body), name in zip(bodies, names):
    merged.setdefault(name, []).append((cond, body))

for name, branches in merged.items():
    method = [f"    private void {name}(CommandSender sender, String[] args) {{\n"]
    for cond_lines, body in branches:
        # Re-emit the original condition verbatim, then the body and the branch close.
        method.extend(cond_lines)
        method.extend(body)
        method.append("            return;\n        }\n")
    method.append("    }\n\n")
    methods.append("".join(method))

dispatcher = """    private boolean handleSiftCommand(CommandSender sender, String[] args) {
        if (requiresAdminPermission(args) && !sender.hasPermission("thesift.foundation.admin")) {
            Messages.send(sender, "doctor.perm",
                    "You do not have permission to use this The Sift command.");
            return true;
        }
        // Brigadier-style dispatch: one handler per subcommand (see SiftSubcommand for the
        // tree). Arg-length guards stay here so a wrong-arity call still hits the usage line.
        String sub = args.length == 0 ? "" : args[0].toLowerCase(java.util.Locale.ROOT);
        switch (sub) {
            case "reload" -> {
                if (args.length == 1) {
                    executeReload(sender, args);
                    return true;
                }
            }
            case "datapack" -> {
                if (args.length >= 2) {
                    executeDatapack(sender, args);
                    return true;
                }
            }
            case "resourcepack" -> {
                if (args.length >= 2) {
                    executeResourcepack(sender, args);
                    return true;
                }
            }
            case "doctor" -> {
                if (args.length == 1) {
                    executeDoctor(sender, args);
                    return true;
                }
            }
            case "dimension", "enter" -> {
                if (args.length == 1) {
                    executeDimension(sender, args);
                    return true;
                }
            }
            case "locate" -> {
                if (args.length == 1) {
                    executeLocate(sender, args);
                    return true;
                }
            }
            case "return" -> {
                if (args.length == 1) {
                    executeReturn(sender, args);
                    return true;
                }
            }
            case "give" -> {
                if (args.length == 3) {
                    executeGive(sender, args);
                    return true;
                }
            }
            case "summon" -> {
                if (args.length >= 2) {
                    executeSummon(sender, args);
                    return true;
                }
            }
        }
        Messages.send(sender, "sift.usage", "Usage: /sift <doctor|reload|datapack <list|enable|disable>|resourcepack [force]|dimension|locate|return|give <item> <player>|summon <species> [player]>");
        return true;
    }

"""

new_method_lines = [dispatcher] + [m for m in methods]
# The last executor keeps no trailing blank line difference; trim one trailing newline.
region = "".join(new_method_lines).rstrip("\n") + "\n"
out = lines[:start] + [region] + lines[end:]
p.write_text("".join(out), encoding="utf-8")
print("handleSiftCommand split into", len(merged), "executors:", sorted(merged.keys()))

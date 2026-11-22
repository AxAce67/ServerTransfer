package sh.aki.servertransfer.text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.Map;
import java.util.regex.Pattern;

/** Text from the config: MiniMessage, or legacy {@code &} colour codes when the string uses them. */
public final class Text {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character('&').hexColors().build();
    private static final Pattern LEGACY_CODE = Pattern.compile("[&§]([0-9a-fk-orA-FK-OR]|#[0-9a-fA-F]{6})");

    private Text() {
    }

    public static boolean isLegacy(String s) {
        return LEGACY_CODE.matcher(s).find();
    }

    public static Component parse(String s) {
        if (s == null || s.isEmpty()) {
            return Component.empty();
        }
        return isLegacy(s) ? LEGACY.deserialize(s.replace('§', '&')) : MM.deserialize(s);
    }

    /** {@code {name}} placeholders are replaced before parsing, so values must not contain markup. */
    public static Component parse(String s, Map<String, String> placeholders) {
        if (s == null) {
            return Component.empty();
        }
        for (Map.Entry<String, String> p : placeholders.entrySet()) {
            s = s.replace("{" + p.getKey() + "}", p.getValue());
        }
        return parse(s);
    }

    /** Item names and lore are italic by default; config text should look the way it was written. */
    public static Component item(Component c) {
        return c.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public static String plain(String s) {
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(parse(s));
    }
}

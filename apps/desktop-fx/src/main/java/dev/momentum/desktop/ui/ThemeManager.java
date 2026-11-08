package dev.momentum.desktop.ui;

import dev.momentum.desktop.host.ServiceHost;
import java.util.List;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.paint.Color;

/**
 * MOMENTUM color themes. The accent color is a CSS looked-up color ("-accent")
 * applied as an inline style on the scene root; the full theme.css stylesheet
 * is derived from it (gradients, borders, glows all use derive(-accent, ...)).
 *
 * The selected theme persists in the local SQLite database (settings table).
 */
public final class ThemeManager {

    /** A named accent theme. */
    public record Theme(String id, String name, String accent) {}

    public static final List<Theme> PRESETS = List.of(
            new Theme("cyan", "Neon Cyan", "#22d3ee"),
            new Theme("violet", "Ultraviolet", "#8b5cf6"),
            new Theme("green", "Matrix Green", "#34d399"),
            new Theme("amber", "Solar Amber", "#f59e0b"),
            new Theme("rose", "Hot Rose", "#fb7185"));

    /** Receives the root inline style whenever the theme changes. */
    public interface RootStyleInjector {
        void accept(String style);
    }

    private final ObjectProperty<Theme> current = new SimpleObjectProperty<>(PRESETS.get(0));
    private final RootStyleInjector injector;
    private final ServiceHost host;

    public ThemeManager(ServiceHost host, RootStyleInjector injector) {
        this.host = host;
        this.injector = injector;
        restore();
        apply(current.get());
    }

    public Theme current() {
        return current.get();
    }

    public ObjectProperty<Theme> currentProperty() {
        return current;
    }

    /** Applies the theme immediately and persists it to SQLite. */
    public void apply(Theme theme) {
        current.set(theme);
        injector.accept("-accent:" + theme.accent()
                + ";-accent-dim:" + hexDerive(theme.accent(), 0.42)
                + ";-accent-faint:" + hexAlpha(theme.accent(), 0.14) + ";");
        host.settingsPut("theme", "{\"theme\":\"" + theme.id() + "\"}");
    }

    public void applyById(String id) {
        PRESETS.stream().filter(theme -> theme.id().equals(id)).findFirst().ifPresent(this::apply);
    }

    private void restore() {
        try {
            host.settingsGet("theme").ifPresent(json -> {
                int marker = json.indexOf("\"theme\":\"");
                if (marker >= 0) {
                    String id = json.substring(marker + 9, json.indexOf('"', marker + 9));
                    PRESETS.stream().filter(theme -> theme.id().equals(id)).findFirst().ifPresent(current::set);
                }
            });
        } catch (RuntimeException e) {
            // First run: no settings row yet — default theme stays.
        }
    }

    /** Darkens a hex color by mixing it toward near-black (used for -accent-dim). */
    static String hexDerive(String hex, double brightness) {
        Color color = Color.web(hex);
        Color derived = Color.color(color.getRed() * brightness, color.getGreen() * brightness, color.getBlue() * brightness);
        return format(derived);
    }

    /** Adds transparency to a hex color (used for -accent-faint). */
    static String hexAlpha(String hex, double alpha) {
        Color color = Color.web(hex);
        return "rgba(" + Math.round(color.getRed() * 255) + ","
                + Math.round(color.getGreen() * 255) + "," + Math.round(color.getBlue() * 255) + "," + alpha + ")";
    }

    private static String format(Color color) {
        return String.format("#%02x%02x%02x",
                (int) Math.round(color.getRed() * 255),
                (int) Math.round(color.getGreen() * 255),
                (int) Math.round(color.getBlue() * 255)).toUpperCase();
    }
}

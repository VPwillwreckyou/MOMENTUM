package dev.momentum.desktop.ui;

/** Central navigation registry — every destination of the MOMENTUM shell. */
public enum NavDestination {
    DASHBOARD("Dashboard", "\u2302"),
    GOALS("Goals", "\u25C9"),
    TASKS("Tasks", "\u2611"),
    CALENDAR("Calendar", "\u25A6"),
    AI_PLANNER("AI Planner", "\u2726"),
    FINANCE("Finance", "$"),
    ACTIVITY("Activity", "\u21BB"),
    ARCHIVE("Archive", "\u2752"),
    TRASH("Trash", "\u2716"),
    SETTINGS("Settings", "\u2699");

    public final String title;
    public final String glyph;

    NavDestination(String title, String glyph) {
        this.title = title;
        this.glyph = glyph;
    }
}

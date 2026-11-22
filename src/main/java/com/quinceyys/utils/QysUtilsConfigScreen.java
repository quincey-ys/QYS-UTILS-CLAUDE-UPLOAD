package com.quinceyys.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * QYS-UTILS settings screen.
 *
 * Layout rules (these are what fix the overlap / misplaced text):
 *  - One setting per row: label on the left, control on the right. The label is truncated
 *    to the space left of the control, so text can never run under a button.
 *  - Controls only ever show short values (ON / OFF, "14", "3.2"), never the label.
 *  - Rows are whole units. Rows that do not fully fit in the viewport are hidden, so
 *    nothing is half-clipped or drawn over the panel border / Done button.
 *  - Tabs live in their own bar with < > buttons and scroll when they do not all fit.
 *  - Settings scroll with the mouse wheel or the ^ / v buttons.
 *
 * To add a module tab, write one buildXxxTab() method and call it from init().
 */
public final class QysUtilsConfigScreen extends Screen {
    private static final int ROW_HEIGHT = 24;
    private static final int CONTROL_WIDTH = 120;
    private static final int TAB_WIDTH = 104;
    private static final int TAB_GAP = 4;

    private final Screen parent;

    // Persist across window resizes (init() runs again on resize).
    private int selectedTab;
    private int tabScroll;
    private int rowScroll;

    private final List<Tab> tabs = new ArrayList<>();
    private final List<Button> tabButtons = new ArrayList<>();
    private Button tabPrev;
    private Button tabNext;
    private Button rowUp;
    private Button rowDown;

    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;

    private int tabBarX;
    private int tabBarWidth;
    private int tabBarY;
    private int viewLeft;
    private int viewRight;
    private int viewTop;
    private int viewHeight;
    private int visibleRows = 1;
    private int visibleTabs = 1;

    private Tab building;

    public QysUtilsConfigScreen(Screen parent) {
        super(Component.literal("QYS-UTILS"));
        this.parent = parent;
    }

    // ------------------------------------------------------------------ setup

    @Override
    protected void init() {
        tabs.clear();
        tabButtons.clear();

        panelWidth = Math.min(420, this.width - 16);
        panelHeight = Math.min(300, this.height - 16);
        panelX = (this.width - panelWidth) / 2;
        panelY = (this.height - panelHeight) / 2;

        buildAutoTotemTab();
        buildMaceHitterTab();
        // buildYourNextTab();   <- add new module tabs here

        // Tab bar: [<] [tab][tab][tab] [>]
        tabBarY = panelY + 28;
        tabPrev = addRenderableWidget(Button.builder(Component.literal("<"), b -> scrollTabs(-1))
                .bounds(panelX + 12, tabBarY, 18, 20).build());
        tabNext = addRenderableWidget(Button.builder(Component.literal(">"), b -> scrollTabs(1))
                .bounds(panelX + panelWidth - 30, tabBarY, 18, 20).build());
        tabBarX = panelX + 34;
        tabBarWidth = panelWidth - 68;
        visibleTabs = Math.max(1, (tabBarWidth + TAB_GAP) / (TAB_WIDTH + TAB_GAP));

        for (int i = 0; i < tabs.size(); i++) {
            final int index = i;
            Button button = Button.builder(Component.literal(tabs.get(i).name), b -> selectTab(index))
                    .bounds(tabBarX, tabBarY, TAB_WIDTH, 20).build();
            tabButtons.add(addRenderableWidget(button));
        }

        // Settings viewport.
        viewLeft = panelX + 14;
        viewRight = panelX + panelWidth - 14 - 24;   // 24px reserved for the scroll column
        viewTop = panelY + 68;
        int viewBottom = panelY + panelHeight - 40;  // leaves room for the Done button
        viewHeight = Math.max(ROW_HEIGHT, viewBottom - viewTop);
        visibleRows = Math.max(1, viewHeight / ROW_HEIGHT);

        int scrollX = panelX + panelWidth - 14 - 20;
        rowUp = addRenderableWidget(Button.builder(Component.literal("^"), b -> scrollRows(-1))
                .bounds(scrollX, viewTop, 20, 20).build());
        rowDown = addRenderableWidget(Button.builder(Component.literal("v"), b -> scrollRows(1))
                .bounds(scrollX, viewTop + visibleRows * ROW_HEIGHT - 20, 20, 20).build());

        addRenderableWidget(Button.builder(Component.literal("DONE - SAVE SETTINGS"), b -> onClose())
                .bounds(panelX + panelWidth / 2 - 94, panelY + panelHeight - 30, 188, 20).build());

        selectedTab = Math.max(0, Math.min(selectedTab, tabs.size() - 1));
        layout();
    }

    private void buildAutoTotemTab() {
        beginTab("AUTOTOTEM");
        toggle("AutoTotem Enabled", () -> AutoTotemModule.enabled, v -> AutoTotemModule.enabled = v);
        cycle("Switch Mode", new String[]{"Smart", "Switch", "PickUp"},
                () -> AutoTotemModule.switchMode, v -> AutoTotemModule.switchMode = v);
        toggle("Health Trigger", () -> AutoTotemModule.healthMode, v -> AutoTotemModule.healthMode = v);
        intStepper("Health Threshold", 0, 20, 1,
                () -> AutoTotemModule.healthThreshold, v -> AutoTotemModule.healthThreshold = v);
        toggle("Fall Prediction", () -> AutoTotemModule.predictFall, v -> AutoTotemModule.predictFall = v);
        toggle("Missing Armor Trigger", () -> AutoTotemModule.missingArmor, v -> AutoTotemModule.missingArmor = v);
        toggle("Ignore While Elytra Flying", () -> AutoTotemModule.ignoreElytra, v -> AutoTotemModule.ignoreElytra = v);
        toggle("Send Directly (no delay)", () -> AutoTotemModule.sendDirectly, v -> AutoTotemModule.sendDirectly = v);
        intStepper("Reaction Ticks", 0, 20, 1,
                () -> AutoTotemModule.reactionTicks, v -> AutoTotemModule.reactionTicks = v);
        intStepper("Switch Delay (ms)", 0, 5000, 50,
                () -> AutoTotemModule.switchDelayMs, v -> AutoTotemModule.switchDelayMs = v);
        toggle("Switch Back", () -> AutoTotemModule.switchBack, v -> AutoTotemModule.switchBack = v);
        toggle("Safety Check", () -> AutoTotemModule.safety, v -> AutoTotemModule.safety = v);
        intStepper("Safe Health", 0, 20, 1,
                () -> AutoTotemModule.safeHealthThreshold, v -> AutoTotemModule.safeHealthThreshold = v);
        intStepper("Switch Back Delay (ms)", 0, 5000, 50,
                () -> AutoTotemModule.switchBackDelayMs, v -> AutoTotemModule.switchBackDelayMs = v);
        intStepper("Hole Blast Resistance", 0, 2400, 100,
                () -> AutoTotemModule.holeResistance, v -> AutoTotemModule.holeResistance = v);
    }

    private void buildMaceHitterTab() {
        beginTab("MACE HITTER");
        toggle("Mace Hitter Enabled", () -> MaceHitterModule.enabled, v -> MaceHitterModule.enabled = v);
        toggle("Players Only", () -> MaceHitterModule.playersOnly, v -> MaceHitterModule.playersOnly = v);
        toggle("Ignore Teammates", () -> MaceHitterModule.ignoreTeammates, v -> MaceHitterModule.ignoreTeammates = v);
        toggle("Ignore Walls", () -> MaceHitterModule.ignoreWalls, v -> MaceHitterModule.ignoreWalls = v);
        doubleStepper("Target Range", 1.0, 6.0, 0.1,
                () -> MaceHitterModule.range, v -> MaceHitterModule.range = v);
        toggle("Rotate to Target", () -> MaceHitterModule.rotate, v -> MaceHitterModule.rotate = v);
        toggle("Auto-Swap to Mace", () -> MaceHitterModule.autoSwap, v -> MaceHitterModule.autoSwap = v);
        toggle("Require Mace", () -> MaceHitterModule.requireMace, v -> MaceHitterModule.requireMace = v);
        toggle("Auto-Jump", () -> MaceHitterModule.autoJump, v -> MaceHitterModule.autoJump = v);
        toggle("Falling Only", () -> MaceHitterModule.fallingOnly, v -> MaceHitterModule.fallingOnly = v);
        toggle("Shield-Stun Priority", () -> MaceHitterModule.shieldStun, v -> MaceHitterModule.shieldStun = v);
        intStepper("Attack Delay (ticks)", 0, 20, 1,
                () -> MaceHitterModule.delayTicks, v -> MaceHitterModule.delayTicks = v);
        doubleStepper("Slam Now Range", 1.0, 8.0, 0.5,
                () -> MaceHitterModule.slamRange, v -> MaceHitterModule.slamRange = v);
        action("Slam Now  [G]", "SLAM", MaceHitterModule::slamNow);
    }

    // ------------------------------------------------------------ row builders

    private void beginTab(String name) {
        building = new Tab(name);
        tabs.add(building);
    }

    private void toggle(String label, BooleanSupplier getter, Consumer<Boolean> setter) {
        Button button = Button.builder(onOff(getter.getAsBoolean()), b -> {
            boolean value = !getter.getAsBoolean();
            setter.accept(value);
            b.setMessage(onOff(value));
            persist();
        }).bounds(0, 0, CONTROL_WIDTH, 20).build();
        addRow(label, new int[]{0}, button);
    }

    private void cycle(String label, String[] options, Supplier<String> getter, Consumer<String> setter) {
        Button button = Button.builder(Component.literal(getter.get()), b -> {
            int index = 0;
            for (int i = 0; i < options.length; i++) {
                if (options[i].equalsIgnoreCase(getter.get())) index = i;
            }
            String next = options[(index + 1) % options.length];
            setter.accept(next);
            b.setMessage(Component.literal(next));
            persist();
        }).bounds(0, 0, CONTROL_WIDTH, 20).build();
        addRow(label, new int[]{0}, button);
    }

    private void action(String label, String buttonText, Runnable run) {
        Button button = Button.builder(Component.literal(buttonText), b -> run.run())
                .bounds(0, 0, CONTROL_WIDTH, 20).build();
        addRow(label, new int[]{0}, button);
    }

    private void intStepper(String label, int min, int max, int step, IntSupplier getter, IntConsumer setter) {
        Button[] value = new Button[1];
        Button minus = Button.builder(Component.literal("-"), b -> {
            setter.accept(Math.max(min, getter.getAsInt() - step));
            value[0].setMessage(Component.literal(Integer.toString(getter.getAsInt())));
            persist();
        }).bounds(0, 0, 22, 20).build();
        value[0] = Button.builder(Component.literal(Integer.toString(getter.getAsInt())), b -> {})
                .bounds(0, 0, CONTROL_WIDTH - 48, 20).build();
        Button plus = Button.builder(Component.literal("+"), b -> {
            setter.accept(Math.min(max, getter.getAsInt() + step));
            value[0].setMessage(Component.literal(Integer.toString(getter.getAsInt())));
            persist();
        }).bounds(0, 0, 22, 20).build();
        addRow(label, new int[]{0, 24, CONTROL_WIDTH - 22}, minus, value[0], plus);
    }

    private void doubleStepper(String label, double min, double max, double step,
                               DoubleSupplier getter, DoubleConsumer setter) {
        Button[] value = new Button[1];
        Button minus = Button.builder(Component.literal("-"), b -> {
            setter.accept(Math.max(min, round1(getter.getAsDouble() - step)));
            value[0].setMessage(decimal(getter.getAsDouble()));
            persist();
        }).bounds(0, 0, 22, 20).build();
        value[0] = Button.builder(decimal(getter.getAsDouble()), b -> {})
                .bounds(0, 0, CONTROL_WIDTH - 48, 20).build();
        Button plus = Button.builder(Component.literal("+"), b -> {
            setter.accept(Math.min(max, round1(getter.getAsDouble() + step)));
            value[0].setMessage(decimal(getter.getAsDouble()));
            persist();
        }).bounds(0, 0, 22, 20).build();
        addRow(label, new int[]{0, 24, CONTROL_WIDTH - 22}, minus, value[0], plus);
    }

    private void addRow(String label, int[] offsets, Button... widgets) {
        for (Button widget : widgets) {
            addRenderableWidget(widget);
            widget.visible = false;
        }
        building.rows.add(new Row(label, offsets, widgets));
    }

    // ----------------------------------------------------------------- layout

    private void selectTab(int index) {
        selectedTab = index;
        rowScroll = 0;
        layout();
    }

    private void scrollTabs(int delta) {
        tabScroll += delta;
        layout();
    }

    private void scrollRows(int delta) {
        rowScroll += delta;
        layout();
    }

    /** Positions and shows exactly the widgets that belong on screen right now. */
    private void layout() {
        // Tab bar
        int maxTabScroll = Math.max(0, tabs.size() - visibleTabs);
        tabScroll = Math.max(0, Math.min(tabScroll, maxTabScroll));
        // keep the selected tab in view
        if (selectedTab < tabScroll) tabScroll = selectedTab;
        if (selectedTab >= tabScroll + visibleTabs) tabScroll = selectedTab - visibleTabs + 1;
        tabScroll = Math.max(0, Math.min(tabScroll, maxTabScroll));

        tabPrev.visible = maxTabScroll > 0;
        tabNext.visible = maxTabScroll > 0;
        tabPrev.active = tabScroll > 0;
        tabNext.active = tabScroll < maxTabScroll;

        for (int i = 0; i < tabButtons.size(); i++) {
            Button button = tabButtons.get(i);
            boolean shown = i >= tabScroll && i < tabScroll + visibleTabs;
            button.visible = shown;
            if (shown) {
                button.setX(tabBarX + (i - tabScroll) * (TAB_WIDTH + TAB_GAP));
                button.setY(tabBarY);
            }
            button.setMessage(Component.literal(i == selectedTab
                    ? "[ " + tabs.get(i).name + " ]" : tabs.get(i).name));
        }

        // Rows
        for (Tab tab : tabs) {
            for (Row row : tab.rows) {
                for (AbstractWidget widget : row.widgets) widget.visible = false;
            }
        }
        Tab tab = tabs.get(selectedTab);
        int maxRowScroll = Math.max(0, tab.rows.size() - visibleRows);
        rowScroll = Math.max(0, Math.min(rowScroll, maxRowScroll));

        int controlX = viewRight - CONTROL_WIDTH - 6;
        for (int i = 0; i < visibleRows && rowScroll + i < tab.rows.size(); i++) {
            Row row = tab.rows.get(rowScroll + i);
            int y = viewTop + i * ROW_HEIGHT + 2;
            for (int w = 0; w < row.widgets.length; w++) {
                row.widgets[w].setX(controlX + row.offsets[w]);
                row.widgets[w].setY(y);
                row.widgets[w].visible = true;
            }
        }

        boolean scrollable = maxRowScroll > 0;
        rowUp.visible = scrollable;
        rowDown.visible = scrollable;
        rowUp.active = rowScroll > 0;
        rowDown.active = rowScroll < maxRowScroll;
    }

    /**
     * Mouse wheel: over the tab bar scrolls tabs, anywhere else scrolls the settings.
     * (No @Override on purpose: if this Minecraft version's signature differs, the wheel
     * is simply ignored and the < > ^ v buttons still work, instead of failing the build.
     * If your mappings use mouseScrolled(double, double, double, double), add @Override.)
     */
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY == 0.0) return false;
        int direction = scrollY > 0 ? -1 : 1;
        if (mouseY >= tabBarY && mouseY < tabBarY + 20) scrollTabs(direction);
        else scrollRows(direction);
        return true;
    }

    // -------------------------------------------------------------- rendering

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        graphics.fill(0, 0, this.width, this.height, 0xB8000000);
        fillSquircle(graphics, panelX, panelY, panelWidth, panelHeight, 18, 0xFF141923);
        graphics.fill(panelX + 18, panelY + 2, panelX + panelWidth - 18, panelY + 24, 0xFF293449);
        graphics.fill(panelX + 2, panelY + 14, panelX + panelWidth - 2, panelY + 24, 0xFF293449);

        fillSquircle(graphics, panelX + 9, viewTop - 3, panelWidth - 18,
                visibleRows * ROW_HEIGHT + 6, 10, 0xFF1C2431);

        Tab tab = tabs.get(selectedTab);
        for (int i = 0; i < visibleRows && rowScroll + i < tab.rows.size(); i++) {
            int y = viewTop + i * ROW_HEIGHT;
            graphics.fill(viewLeft, y + 1, viewRight, y + ROW_HEIGHT - 1,
                    (i % 2 == 0) ? 0xFF222B3A : 0xFF1E2634);
        }

        // Scrollbar track + thumb (under the ^ and v buttons).
        int maxScroll = Math.max(0, tab.rows.size() - visibleRows);
        if (maxScroll > 0) {
            int trackX = panelX + panelWidth - 14 - 12;
            int trackTop = viewTop + 22;
            int trackBottom = viewTop + visibleRows * ROW_HEIGHT - 22;
            if (trackBottom - trackTop > 8) {
                graphics.fill(trackX, trackTop, trackX + 4, trackBottom, 0xFF10151D);
                int trackHeight = trackBottom - trackTop;
                int thumbHeight = Math.max(10, trackHeight * visibleRows / tab.rows.size());
                int thumbY = trackTop + (trackHeight - thumbHeight) * rowScroll / maxScroll;
                graphics.fill(trackX, thumbY, trackX + 4, thumbY + thumbHeight, 0xFF5D7396);
            }
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractRenderState(graphics, mouseX, mouseY, a);

        // Header: subtitle is only drawn if it fits next to the title.
        int titleWidth = this.font.width("QYS-UTILS");
        graphics.text(this.font, "QYS-UTILS", panelX + 16, panelY + 8, 0xFFFFFFFF, true);
        String subtitle = "UNIFIED CLIENT UTILITIES / 26.3";
        int subtitleX = panelX + 16 + titleWidth + 10;
        if (subtitleX + this.font.width(subtitle) <= panelX + panelWidth - 16) {
            graphics.text(this.font, subtitle, subtitleX, panelY + 8, 0xFFD5DCEC, false);
        }

        Tab tab = tabs.get(selectedTab);

        // Section line: tab name left, "a-b of n" right (right part dropped if it doesn't fit).
        int lineY = panelY + 54;
        int first = tab.rows.isEmpty() ? 0 : rowScroll + 1;
        int last = Math.min(tab.rows.size(), rowScroll + visibleRows);
        String range = first + "-" + last + " of " + tab.rows.size();
        int rangeWidth = this.font.width(range);
        int rangeX = panelX + panelWidth - 16 - rangeWidth;
        String heading = fit(tab.name + " SETTINGS", rangeX - (panelX + 16) - 8);
        graphics.text(this.font, heading, panelX + 16, lineY, 0xFFE4EAF5, false);
        graphics.text(this.font, range, rangeX, lineY, 0xFF8EA0BD, false);

        // Row labels, truncated so they can never reach the control.
        int controlX = viewRight - CONTROL_WIDTH - 6;
        int labelWidth = controlX - (viewLeft + 8) - 6;
        for (int i = 0; i < visibleRows && rowScroll + i < tab.rows.size(); i++) {
            Row row = tab.rows.get(rowScroll + i);
            int textY = viewTop + i * ROW_HEIGHT + (ROW_HEIGHT - 8) / 2;
            graphics.text(this.font, fit(row.label, labelWidth), viewLeft + 8, textY, 0xFFE4EAF5, false);
        }
    }

    /** Truncates text with ".." so it is never wider than maxWidth pixels. */
    private String fit(String text, int maxWidth) {
        if (maxWidth <= 0) return "";
        if (this.font.width(text) <= maxWidth) return text;
        String ellipsis = "..";
        int limit = maxWidth - this.font.width(ellipsis);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            if (this.font.width(out.toString() + text.charAt(i)) > limit) break;
            out.append(text.charAt(i));
        }
        return out.toString().stripTrailing() + ellipsis;
    }

    private void fillSquircle(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
                              int radius, int color) {
        int r = Math.max(1, Math.min(radius, Math.min(width, height) / 2));
        for (int row = 0; row < height; row++) {
            int fromEdge = Math.min(row, height - 1 - row);
            int inset = 0;
            if (fromEdge < r) {
                double t = 1.0 - fromEdge / (double) r;
                double curve = 1.0 - Math.pow(t, 4.0);
                inset = (int) Math.round(r * (1.0 - Math.pow(curve, 0.25)));
            }
            graphics.fill(x + inset, y + row, x + width - inset, y + row + 1, color);
        }
    }

    // ---------------------------------------------------------------- helpers

    private static Component onOff(boolean value) {
        return Component.literal(value ? "ON" : "OFF");
    }

    private static Component decimal(double value) {
        return Component.literal(String.format(Locale.ROOT, "%.1f", value));
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private static void persist() {
        AutoTotemModule.saveConfig();
        MaceHitterModule.saveConfig();
    }

    @Override
    public void onClose() {
        persist();
        Minecraft.getInstance().gui.setScreen(parent);
    }

    private static final class Tab {
        final String name;
        final List<Row> rows = new ArrayList<>();

        Tab(String name) {
            this.name = name;
        }
    }

    private static final class Row {
        final String label;
        final int[] offsets;
        final Button[] widgets;

        Row(String label, int[] offsets, Button[] widgets) {
            this.label = label;
            this.offsets = offsets;
            this.widgets = widgets;
        }
    }
}

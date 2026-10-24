package com.byflx.excavation3d.client.screen;

import com.byflx.excavation3d.client.ClientState;
import com.byflx.excavation3d.client.PresetRepository;
import com.byflx.excavation3d.client.QuickShapeAction;
import com.byflx.excavation3d.client.Excavation3DClient;
import com.byflx.excavation3d.data.ToolShapeData;
import com.byflx.excavation3d.editor.QuickMenuLogic;
import com.byflx.excavation3d.registry.ModComponents;
import com.byflx.excavation3d.shape.ShapeConfig;
import com.byflx.excavation3d.shape.ShapeGenerator;
import com.byflx.excavation3d.preview.ShapeThumbnail;
import com.byflx.excavation3d.text.ShapeTexts;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

/** Hold-to-select wheel. Hit testing stays at its final size throughout the opening animation. */
public final class QuickShapeScreen extends Screen {
    private static final int TEXT = 0xFFF1F3F5;
    private static final int MUTED = 0xFFA4ACB4;
    private static final int ACCENT = 0xFFE0B020;
    private record WheelEntry(QuickMenuLogic.Entry entry, ShapeConfig config, Component name,
            Component details, ShapeThumbnail thumbnail, boolean active, boolean enabled) { }

    private final List<WheelEntry> entries;
    private final int slot;
    private final ItemStack tool;
    private final boolean firstOn;
    private final ShapeConfig activeConfig;
    private final boolean shapeEnabled;
    private final QuickMenuLogic.FavoriteDelete deletion = new QuickMenuLogic.FavoriteDelete();
    private boolean rightClicked;
    private final Component currentType;
    private final Component currentSize;
    private final double[] hover;
    private long openedAt, lastFrame;
    private boolean initialized, finished;
    private int page;

    public QuickShapeScreen(Player player) {
        super(Component.translatable("gui.excavation3d.quick.title"));
        slot = player.getInventory().selected;
        tool = player.getMainHandItem();
        ToolShapeData data = tool.get(ModComponents.TOOL_SHAPE.get());
        shapeEnabled = data != null && data.enabled();
        ShapeConfig active = data == null ? null : data.config().orElse(null);
        activeConfig = active;
        firstOn = QuickMenuLogic.firstSegmentOn(active != null, shapeEnabled);
        currentType = active == null || !shapeEnabled ? Component.translatable("gui.excavation3d.quick.off") : ShapeTexts.typeName(active.type());
        currentSize = active != null && ShapeGenerator.generate(active, ClientState.limits()) instanceof ShapeGenerator.Valid valid
                ? Component.translatable("gui.excavation3d.quick.size", valid.shape().width(), valid.shape().height(), valid.shape().depth())
                : Component.empty();
        // Snapshot configs and generated summaries once; rendering never generates shapes or reads disk.
        var menu = QuickMenuLogic.entries(PresetRepository.get().names());
        int activeIndex = QuickMenuLogic.activeIndex(menu, active, shapeEnabled, PresetRepository.get()::find);
        entries = menu.stream().map(entry -> {
            if (entry.kind() == QuickMenuLogic.Kind.HINT) return emptyHint();
            ShapeConfig config = switch (entry.kind()) {
                case OFF -> firstOn ? active : null;
                case PRESET -> PresetRepository.get().find(entry.name()).orElse(null);
                case HINT -> null;
            };
            Component name = switch (entry.kind()) {
                case OFF -> Component.translatable(firstOn ? "gui.excavation3d.quick.on" : "gui.excavation3d.quick.off");
                case PRESET -> Component.literal(entry.name());
                case HINT -> Component.empty();
            };
            boolean enabled = entry.kind() == QuickMenuLogic.Kind.OFF;
            Component details = switch (entry.kind()) {
                case OFF -> Component.translatable("gui.excavation3d.quick.mine_normally");
                default -> Component.translatable("gui.excavation3d.quick.unavailable");
            };
            ShapeThumbnail thumbnail = null;
            if (config != null && ShapeGenerator.generate(config, ClientState.limits()) instanceof ShapeGenerator.Valid valid) {
                var shape = valid.shape();
                details = Component.translatable("hud.excavation3d.preview.short", shape.count());
                thumbnail = ShapeThumbnail.of(shape.mask());
                enabled = true;
            }
            return new WheelEntry(entry, config, name, details, thumbnail,
                    activeIndex >= 0 && menu.get(activeIndex).equals(entry), enabled);
        }).collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
        hover = new double[entries.size()];
    }

    private static WheelEntry emptyHint() {
        return new WheelEntry(new QuickMenuLogic.Entry(QuickMenuLogic.Kind.HINT, ""), null,
                Component.translatable("gui.excavation3d.quick.empty", Excavation3DClient.editorKeyLabel()),
                Component.empty(), null, false, false);
    }

    @Override protected void init() {
        // setScreen releases the mouse before init, preventing camera movement while the wheel is open.
        // GLFW's callback also updates MouseHandler's coordinates before release-to-select is polled.
        GLFW.glfwSetCursorPos(minecraft.getWindow().getWindow(),
                minecraft.getWindow().getScreenWidth() / 2.0, minecraft.getWindow().getScreenHeight() / 2.0);
        if (!initialized) {
            openedAt = lastFrame = Util.getMillis();
            initialized = true;
        }
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { finish(-1); }

    private QuickMenuLogic.Layout layout() { return QuickMenuLogic.layout(width, height, entries.size(), page); }

    private boolean validTool() {
        return minecraft.isWindowActive() && minecraft.player != null
                && minecraft.player.getInventory().selected == slot && minecraft.player.getMainHandItem() == tool;
    }

    @Override public void tick() {
        if (!validTool()) { onClose(); return; }
        if (!Excavation3DClient.isEditorKeyDown()) {
            double mx = minecraft.mouseHandler.xpos() * width / minecraft.getWindow().getScreenWidth();
            double my = minecraft.mouseHandler.ypos() * height / minecraft.getWindow().getScreenHeight();
            finish(rightClicked ? -1 : layout().highlighted(mx, my));
        }
    }

    private void finish(int index) {
        if (finished) return;
        finished = true;
        boolean canApply = validTool();
        Excavation3DClient.cancelEditorGesture();
        minecraft.setScreen(null);
        if (!canApply || index < 0 || index >= entries.size()) return;
        WheelEntry selection = entries.get(index);
        if (!selection.enabled()) return;
        if (selection.entry().kind() == QuickMenuLogic.Kind.OFF) {
            QuickShapeAction.start(null);
        } else {
            QuickShapeAction.startPreset(selection.config(), selection.entry().name());
        }
    }

    @Override public boolean mouseClicked(double mx, double my, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            finish(layout().highlighted(mx, my));
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            rightClicked = true;
            int index = layout().highlighted(mx, my);
            QuickMenuLogic.Entry entry = index < 0 ? null : entries.get(index).entry();
            if (deletion.rightClick(entry) && PresetRepository.get().delete(entry.name())) {
                entries.remove(index);
                if (entries.size() == 1) entries.add(emptyHint());
                page = layout().page();
                boolean marked = activeConfig == null || !shapeEnabled;
                for (int i = 0; i < entries.size(); i++) {
                    WheelEntry e = entries.get(i);
                    boolean active = i == 0 ? marked : !marked && e.config() != null
                            && e.config().withRevision(0).equals(activeConfig.withRevision(0));
                    if (active) marked = true;
                    entries.set(i, new WheelEntry(e.entry(), e.config(), e.name(), e.details(), e.thumbnail(), active, e.enabled()));
                }
            }
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override public void mouseMoved(double mx, double my) {
        int index = layout().highlighted(mx, my);
        deletion.hover(index < 0 ? null : entries.get(index).entry());
        super.mouseMoved(mx, my);
    }

    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        if (key >= GLFW.GLFW_KEY_1 && key <= GLFW.GLFW_KEY_8) {
            int index = layout().numberSelection(key - GLFW.GLFW_KEY_1 + 1);
            if (index >= 0 && entries.get(index).enabled()) finish(index);
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        deletion.hover(null);
        page = QuickMenuLogic.layout(width, height, entries.size(), page - (int) Math.signum(sy)).page();
        return true;
    }

    /** No vanilla menu blur, panorama or full-screen dark rectangle. */
    @Override public void renderBackground(GuiGraphics g, int mx, int my, float partialTick) { }

    @Override public void render(GuiGraphics g, int mx, int my, float partialTick) {
        QuickMenuLogic.Layout l = layout();
        int selected = l.highlighted(mx, my);
        long now = Util.getMillis();
        double elapsed = QuickMenuLogic.animationProgress(now, lastFrame, QuickMenuLogic.HOVER_MS);
        lastFrame = now;
        deletion.hover(selected < 0 ? null : entries.get(selected).entry());
        for (int i = 0; i < entries.size(); i++) {
            hover[i] = Math.clamp(hover[i] + (i == selected && entries.get(i).enabled() ? elapsed : -elapsed), 0, 1);
        }
        double progress = QuickMenuLogic.animationProgress(now, openedAt, QuickMenuLogic.OPEN_MS);
        double fade = 1 - Math.pow(1 - progress, 3);
        float scale = (float) (0.92 + 0.08 * fade);
        g.pose().pushPose();
        g.pose().translate(l.centerX(), l.centerY(), 0);
        g.pose().scale(scale, scale, 1);
        g.pose().translate(-l.centerX(), -l.centerY(), 0);
        // Each radial band shares vertices at its edge: a smooth local fade without stacked halos.
        arc(g, l, 0, 28 * l.scale(), 0, Math.PI * 2, alpha(0x5006080A, fade), alpha(0x4006080A, fade));
        arc(g, l, 28 * l.scale(), 88 * l.scale(), 0, Math.PI * 2, alpha(0x4006080A, fade), alpha(0x2806080A, fade));
        arc(g, l, 88 * l.scale(), 102 * l.scale(), 0, Math.PI * 2, alpha(0x2806080A, fade), 0x0006080A);
        arc(g, l, 0, l.deadZone() - 2 * l.scale(), 0, Math.PI * 2, alpha(0xD0101418, fade));
        for (int segment = 0; segment < l.segments(); segment++) {
            int index = l.index(segment);
            double h = hover[index] * hover[index] * (3 - 2 * hover[index]);
            double half = Math.PI / l.segments();
            double gap = l.segments() == 1 ? 0 : QuickMenuLogic.SEGMENT_GAP;
            double start = l.angle(segment) - half + gap, end = l.angle(segment) + half - gap;
            double outer = l.radius() + 4 * l.scale() * h;
            int outline = alpha(mix(0x70424A52, ACCENT, h), fade);
            int fill = alpha(mix(0xB0101418, 0xD02C3239, h), fade);
            sector(g, l, outer, start, end, outline, fill);
            if (entries.get(index).active()) {
                double r = outer - 3 * l.scale();
                double x = l.centerX() + Math.cos(l.angle(segment)) * r;
                double y = l.centerY() + Math.sin(l.angle(segment)) * r;
                dot(g, x, y, 1.7 * l.scale(), alpha(ACCENT, fade));
            }
        }
        g.flush();
        for (int segment = 0; segment < l.segments(); segment++) {
            int index = l.index(segment);
            WheelEntry entry = entries.get(index);
            int color = alpha(entry.enabled() ? TEXT : MUTED, fade * (entry.enabled() ? 1 : 0.65));
            g.pose().pushPose();
            g.pose().translate(l.labelX(segment), l.labelY(segment), 0);
            g.pose().scale((float) l.scale(), (float) l.scale(), 1);
            switch (entry.entry().kind()) {
                case OFF -> {
                    if (firstOn) ShapeTypeIcons.draw(g, entry.config().type(), -5, -5, 2, color);
                    else ShapeTypeIcons.drawOff(g, -7, -7, 2, color);
                }
                case PRESET -> ShapeTypeIcons.draw(g, entry.config() == null ? null : entry.config().type(), -5, -5, 2, color);
                case HINT -> { }
            }
            g.pose().popPose();
            line(g, entry.name(), l.labelX(segment), l.labelY(segment) + 8 * l.scale(),
                    l.labelWidth(segment), 0.65 * l.scale(), color);
            line(g, Component.literal(Integer.toString(segment + 1)),
                    l.shortcutX(segment), l.shortcutY(segment),
                    10 * l.scale(), 0.6 * l.scale(), alpha(MUTED, fade));
        }
        renderHub(g, l, selected < 0 ? null : entries.get(selected), fade);
        g.pose().popPose();
        if (l.showInstructions()) line(g,
                Component.translatable("gui.excavation3d.quick.hint", Excavation3DClient.editorKeyLabel()),
                l.centerX(), l.instructionsY(), width - 8, 0.8 * l.scale(), alpha(TEXT, fade));
        if (l.pages() > 1) {
            line(g, Component.translatable("gui.excavation3d.quick.page", l.page() + 1, l.pages()),
                    l.centerX(), Math.min(height - 10, l.instructionsY() + 12 * l.scale()), width - 8, 0.7 * l.scale(), alpha(MUTED, fade));
        }
        g.flush();
    }

    private void renderHub(GuiGraphics g, QuickMenuLogic.Layout l, WheelEntry hovered, double fade) {
        double s = l.scale();
        double cx = l.centerX(), cy = l.centerY();
        if (hovered == null) {
            line(g, Component.translatable("gui.excavation3d.quick.current_label"), cx, cy - 18 * s,
                    36 * s, 0.65 * s, alpha(MUTED, fade));
            line(g, currentType, cx, cy - 6 * s, 48 * s, 0.65 * s, alpha(TEXT, fade));
            line(g, currentSize, cx, cy + 6 * s, 46 * s, 0.65 * s, alpha(TEXT, fade));
            line(g, Component.translatable("gui.excavation3d.quick.cancel"), cx, cy + 17 * s,
                    30 * s, 0.55 * s, alpha(MUTED, fade));
            return;
        }
        if (deletion.pending() != null || hovered.entry().kind() == QuickMenuLogic.Kind.HINT) {
            Component message = deletion.pending() == null ? hovered.name()
                    : Component.translatable("gui.excavation3d.quick.delete_confirm", deletion.pending());
            var lines = font.split(Component.literal(QuickMenuLogic.singleLine(message.getString()))
                    .withStyle(message.getStyle()), 70);
            g.pose().pushPose();
            g.pose().translate(cx, cy - lines.size() * 3 * s, 0);
            g.pose().scale((float) (0.6 * s), (float) (0.6 * s), 1);
            for (int i = 0; i < lines.size(); i++) {
                g.drawString(font, lines.get(i), -font.width(lines.get(i)) / 2, i * 10, alpha(TEXT, fade), false);
            }
            g.pose().popPose();
            return;
        }
        if (hovered.entry().kind() == QuickMenuLogic.Kind.OFF && !firstOn) {
            g.pose().pushPose();
            g.pose().translate(cx - 10.5 * s, cy - 10.5 * s, 0);
            g.pose().scale((float) s, (float) s, 1);
            ShapeTypeIcons.drawOff(g, 0, 0, 3, alpha(TEXT, fade));
            g.pose().popPose();
            return;
        }
        // Keep the name on the upper chord and the count on the lower chord of the inner circle.
        fullName(g, hovered.name().copy().withStyle(ChatFormatting.BOLD), cx, cy - 24 * s, s,
                alpha(TEXT, fade));
        if (hovered.thumbnail() != null) {
            VertexConsumer vertices = g.bufferSource().getBuffer(RenderType.gui());
            Matrix4f matrix = g.pose().last().pose();
            for (var face : hovered.thumbnail().faces()) {
                int color = alpha(0xFF000000 | face.rgb(), fade);
                for (var point : face.points()) {
                    vertices.addVertex(matrix, (float) (cx + point.x() * 12 * s),
                            (float) (cy + point.y() * 12 * s), 0).setColor(color);
                }
            }
            g.flush();
        }
        Component detail = hovered.details();
        double detailScale = hovered.thumbnail() == null ? 0.65 * s
                : Math.min(0.65 * s, 32 * s / Math.max(1, font.width(detail)));
        line(g, detail, cx, cy + 14 * s, 32 * s, detailScale, alpha(MUTED, fade));
    }

    /** Fits the complete name in at most two lines; segment labels may still abbreviate. */
    private void fullName(GuiGraphics g, Component name, double x, double y, double scale, int color) {
        var lines = QuickMenuLogic.wrapName(name.getString(),
                text -> font.width(Component.literal(text).withStyle(name.getStyle())),
                (text, wrapWidth) -> font.split(Component.literal(text).withStyle(name.getStyle()), wrapWidth),
                text -> Component.literal(text).withStyle(name.getStyle()).getVisualOrderText());
        int widest = lines.stream().mapToInt(font::width).max().orElse(1);
        float textScale = (float) Math.min(0.7 * scale, 36 * scale / Math.max(1, widest));
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(textScale, textScale, 1);
        for (int i = 0; i < lines.size(); i++) {
            g.drawString(font, lines.get(i), -font.width(lines.get(i)) / 2,
                    i * font.lineHeight, color, false);
        }
        g.pose().popPose();
    }

    private void line(GuiGraphics g, Component text, double x, double y, double maxWidth, double scale, int color) {
        if (scale <= 0 || (color >>> 24) < 4) return; // Font treats near-zero alpha as opaque.
        String fitted = QuickMenuLogic.fitText(text.getString(), (int) Math.floor(maxWidth / scale),
                s -> font.width(Component.literal(s).withStyle(text.getStyle())));
        Component label = Component.literal(fitted).withStyle(text.getStyle());
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale((float) scale, (float) scale, 1);
        g.pose().translate(-font.width(label) / 2.0, 0, 0);
        g.drawString(font, label, 0, 0, color, false);
        g.pose().popPose();
    }

    private static int alpha(int color, double opacity) {
        return ((int) Math.round((color >>> 24) * opacity) << 24) | (color & 0xFFFFFF);
    }

    private static int mix(int from, int to, double amount) {
        int result = 0;
        for (int shift = 0; shift <= 24; shift += 8) {
            int a = (from >>> shift) & 255, b = (to >>> shift) & 255;
            result |= (int) Math.round(a + (b - a) * amount) << shift;
        }
        return result;
    }

    private static void dot(GuiGraphics g, double x, double y, double radius, int color) {
        var l = new QuickMenuLogic.Layout(x, y, radius, 0, 1, 0);
        arc(g, l, 0, radius, 0, Math.PI * 2, color);
    }

    /** Shared angular vertices for every border/fill band: neither gaps nor alpha overlaps at seams. */
    private static void sector(GuiGraphics g, QuickMenuLogic.Layout l, double outer,
            double start, double end, int outline, int fill) {
        VertexConsumer vertices = g.bufferSource().getBuffer(RenderType.gui());
        Matrix4f matrix = g.pose().last().pose();
        int steps = Math.max(12, (int) Math.ceil((end - start) * outer / 1.5));
        double border = l.scale();
        for (int step = 0; step < steps; step++) {
            double a = start + (end - start) * step / steps;
            double b = start + (end - start) * (step + 1) / steps;
            quad(vertices, matrix, l, l.deadZone(), l.deadZone() + border, a, b, outline, outline);
            quad(vertices, matrix, l, outer - border, outer, a, b, outline, outline);
            int middle = l.segments() > 1 && (step == 0 || step == steps - 1) ? outline : fill;
            quad(vertices, matrix, l, l.deadZone() + border, outer - border, a, b, middle, middle);
        }
    }

    private static void arc(GuiGraphics g, QuickMenuLogic.Layout l, double inner, double outer,
            double start, double end, int color) {
        arc(g, l, inner, outer, start, end, color, color);
    }

    /** Disjoint quads, at most 1.5 GUI pixels per arc edge, with optional radial alpha interpolation. */
    private static void arc(GuiGraphics g, QuickMenuLogic.Layout l, double inner, double outer,
            double start, double end, int innerColor, int outerColor) {
        VertexConsumer vertices = g.bufferSource().getBuffer(RenderType.gui());
        Matrix4f matrix = g.pose().last().pose();
        // All vignette bands use the same mesh, including their shared circular boundaries.
        int steps = Math.max(12, (int) Math.ceil((end - start) * Math.max(outer, 102 * l.scale()) / 1.5));
        for (int step = 0; step < steps; step++) {
            double a = start + (end - start) * step / steps;
            double b = start + (end - start) * (step + 1) / steps;
            quad(vertices, matrix, l, inner, outer, a, b, innerColor, outerColor);
        }
    }

    private static void quad(VertexConsumer vertices, Matrix4f matrix, QuickMenuLogic.Layout l,
            double inner, double outer, double a, double b, int innerColor, int outerColor) {
        vertex(vertices, matrix, l, inner, a, innerColor);
        vertex(vertices, matrix, l, inner, b, innerColor);
        vertex(vertices, matrix, l, outer, b, outerColor);
        vertex(vertices, matrix, l, outer, a, outerColor);
    }

    private static void vertex(VertexConsumer vertices, Matrix4f matrix, QuickMenuLogic.Layout l,
            double radius, double angle, int color) {
        vertices.addVertex(matrix, (float) (l.centerX() + Math.cos(angle) * radius),
                (float) (l.centerY() + Math.sin(angle) * radius), 0).setColor(color);
    }
}

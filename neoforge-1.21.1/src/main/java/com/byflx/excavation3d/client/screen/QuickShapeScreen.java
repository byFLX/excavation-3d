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
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

/** Hold-to-select wheel. Hit testing stays at its final size throughout the opening animation. */
public final class QuickShapeScreen extends Screen {
    private static final int TEXT = 0xFFF1F3F5;
    private static final int MUTED = 0xFFA4ACB4;
    private static final int ACCENT = 0xFFE0B020;
    private record WheelEntry(QuickMenuLogic.Entry entry, ShapeConfig config, Component name,
            Component details, ShapeThumbnail thumbnail, boolean active, boolean enabled) { }

    private List<WheelEntry> entries;
    private final int slot;
    private final ItemStack tool;
    private final ShapeConfig activeConfig;
    private final boolean shapeEnabled;
    private final QuickMenuLogic.FavoriteDelete deletion = new QuickMenuLogic.FavoriteDelete();
    private boolean rightClicked;
    private double[] hover;
    private long openedAt, lastFrame;
    private boolean initialized, finished;
    private int page;

    public QuickShapeScreen(Player player) {
        super(Component.translatable("gui.excavation3d.quick.title"));
        slot = player.getInventory().selected;
        tool = player.getMainHandItem();
        ToolShapeData data = tool.get(ModComponents.TOOL_SHAPE.get());
        shapeEnabled = data != null && data.enabled();
        activeConfig = data == null ? null : data.config().orElse(null);
        rebuildEntries();
        // Reopening shows the active favorite even when it lives on a later page.
        for (int i = 1; i < entries.size(); i++) {
            if (entries.get(i).active()) page = (i - 1) / (QuickMenuLogic.PAGE_SIZE - 1);
        }
    }

    private void rebuildEntries() {
        // Snapshot configs and generated summaries once; rendering never generates shapes or reads disk.
        var presets = PresetRepository.get();
        var menu = QuickMenuLogic.entries(presets.names(), activeConfig, shapeEnabled, presets::find);
        int activeIndex = QuickMenuLogic.activeIndex(menu, activeConfig, shapeEnabled, presets::find);
        entries = menu.stream().map(entry -> {
            if (entry.kind() == QuickMenuLogic.Kind.HINT) return emptyHint();
            ShapeConfig config = switch (entry.kind()) {
                case RESUME -> activeConfig;
                case PRESET -> presets.find(entry.name()).orElse(null);
                case OFF, HINT -> null;
            };
            Component name = switch (entry.kind()) {
                case OFF -> Component.translatable("gui.excavation3d.quick.off");
                case RESUME -> Component.translatable("gui.excavation3d.quick.resume");
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
        }).toList();
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

    /** Suppress both the normal crosshair and its debug variant for this screen's lifetime. */
    public static void onRenderGuiLayer(RenderGuiLayerEvent.Pre event) {
        if (Minecraft.getInstance().screen instanceof QuickShapeScreen
                && event.getName().equals(VanillaGuiLayers.CROSSHAIR)) event.setCanceled(true);
    }

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
        switch (selection.entry().kind()) {
            case OFF -> QuickShapeAction.setEnabled(false);
            case RESUME -> QuickShapeAction.setEnabled(true);
            case PRESET -> QuickShapeAction.startPreset(selection.config(), selection.entry().name());
            case HINT -> { }
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
                // Deleting the paused shape's last matching favorite must expose Resume immediately.
                rebuildEntries();
                page = layout().page();
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
            int baseFill = mix(0xB0101418, 0xD02C3239, h);
            // Composite a 25% gold tint into the fill, retaining the hover's brighter base.
            int fill = alpha(entries.get(index).active() ? mix(baseFill, 0xFFE0B020, 0.25) : baseFill, fade);
            sector(g, l, outer, start, end, outline, fill);
        }
        g.flush();
        for (int segment = 0; segment < l.segments(); segment++) {
            int index = l.index(segment);
            WheelEntry entry = entries.get(index);
            int color = alpha(entry.active() ? ACCENT : entry.enabled() ? TEXT : MUTED,
                    fade * (entry.enabled() ? 1 : 0.65));
            if (l.segments() == 2) {
                segmentName(g, entry, l.labelX(segment), l.labelY(segment) - 3 * l.scale(),
                        l.labelWidth(segment), l.scale(), color, true);
            } else {
                g.pose().pushPose();
                g.pose().translate(l.labelX(segment), l.labelY(segment), 0);
                g.pose().scale((float) l.scale(), (float) l.scale(), 1);
                switch (entry.entry().kind()) {
                    case OFF -> ShapeTypeIcons.drawOff(g, -7, -7, 2, color);
                    case RESUME, PRESET -> ShapeTypeIcons.draw(g, entry.config() == null ? null : entry.config().type(), -5, -5, 2, color);
                    case HINT -> { }
                }
                g.pose().popPose();
                segmentName(g, entry, l.labelX(segment), l.labelY(segment) + 8 * l.scale(),
                        l.labelWidth(segment), l.scale(), color, false);
            }
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
                    l.centerX(), Math.max(4, Math.min(height - 10, l.instructionsY() + 12 * l.scale())),
                    width - 8, 0.7 * l.scale(), alpha(MUTED, fade));
        }
        g.flush();
    }

    private void renderHub(GuiGraphics g, QuickMenuLogic.Layout l, WheelEntry hovered, double fade) {
        if ((alpha(TEXT, fade) >>> 24) < 4) return;
        double s = l.scale();
        double cx = l.centerX(), cy = l.centerY();
        if (hovered == null) {
            line(g, Component.translatable("gui.excavation3d.quick.cancel"), cx, cy - font.lineHeight * 0.35 * s,
                    36 * s, 0.7 * s, alpha(MUTED, fade));
            return;
        }
        boolean messageOnly = deletion.pending() != null || hovered.entry().kind() == QuickMenuLogic.Kind.HINT;
        Component name = deletion.pending() != null
                ? Component.translatable("gui.excavation3d.quick.delete_confirm", deletion.pending())
                : hovered.name().copy().withStyle(ChatFormatting.BOLD);
        Component detail = messageOnly ? Component.empty() : hovered.details();
        boolean off = !messageOnly && hovered.entry().kind() == QuickMenuLogic.Kind.OFF;
        double imageSize = off ? 14 : !messageOnly && hovered.thumbnail() != null ? 20 : 0;
        var lines = QuickMenuLogic.wrapName(name.getString(),
                text -> font.width(Component.literal(text).withStyle(name.getStyle())),
                (text, wrapWidth) -> font.split(Component.literal(text).withStyle(name.getStyle()), wrapWidth),
                text -> Component.literal(text).withStyle(name.getStyle()).getVisualOrderText());
        int widest = lines.stream().mapToInt(font::width).max().orElse(1);
        double nameScale = Math.min(0.7, 36.0 / Math.max(1, widest));
        double detailScale = Math.min(0.6, 36.0 / Math.max(1, font.width(detail)));
        double detailHeight = detail.getString().isEmpty() ? 0 : font.lineHeight * detailScale;
        var hub = QuickMenuLogic.hubLayout(l.deadZone() / s - 6, widest * nameScale,
                lines.size() * font.lineHeight * nameScale, imageSize, font.width(detail) * detailScale, detailHeight);
        // One measured block, with equal top/bottom padding and every corner inside the padded circle.
        g.pose().pushPose();
        g.pose().translate(cx, cy, 10);
        g.pose().scale((float) (s * hub.scale()), (float) (s * hub.scale()), 1);
        g.pose().pushPose();
        g.pose().translate(0, hub.nameY(), 0);
        g.pose().scale((float) nameScale, (float) nameScale, 1);
        for (int i = 0; i < lines.size(); i++) {
            g.drawString(font, lines.get(i), -font.width(lines.get(i)) / 2, i * font.lineHeight,
                    alpha(deletion.pending() == null ? TEXT : ACCENT, fade), false);
        }
        g.pose().popPose();
        g.flush();
        if (off) {
            g.pose().pushPose();
            g.pose().translate(-7, hub.imageY(), 0);
            ShapeTypeIcons.drawOff(g, 0, 0, 2, alpha(TEXT, fade));
            g.pose().popPose();
        } else if (imageSize > 0) {
            VertexConsumer vertices = g.bufferSource().getBuffer(RenderType.gui());
            Matrix4f matrix = g.pose().last().pose();
            for (var face : hovered.thumbnail().faces()) {
                int color = alpha(0xFF000000 | face.rgb(), fade);
                for (var point : face.points()) {
                    vertices.addVertex(matrix, (float) (point.x() * imageSize / 2),
                            (float) (hub.imageY() + imageSize / 2 + point.y() * imageSize / 2), 0).setColor(color);
                }
            }
            g.flush();
        }
        if (detailHeight > 0) line(g, detail, 0, hub.detailY(), 36, detailScale, alpha(MUTED, fade));
        g.pose().popPose();
    }

    /** Reserve room for a drawn check mark so it stays visible even beside abbreviated names. */
    private void segmentName(GuiGraphics g, WheelEntry entry, double x, double y,
            double maxWidth, double scale, int color, boolean inlineIcon) {
        if ((color >>> 24) < 4) return;
        double iconWidth = inlineIcon && entry.entry().kind() != QuickMenuLogic.Kind.HINT ? 18 : 0;
        double checkWidth = entry.active() ? 9 : 0;
        String fitted = QuickMenuLogic.fitText(entry.name().getString(),
                (int) Math.floor(Math.max(0, maxWidth / scale - iconWidth - checkWidth) / 0.65), font::width);
        double textWidth = font.width(fitted) * 0.65;
        g.pose().pushPose();
        g.pose().translate(x - (iconWidth + textWidth + checkWidth) * scale / 2, y, 0);
        g.pose().scale((float) scale, (float) scale, 1);
        if (iconWidth > 0) {
            if (entry.entry().kind() == QuickMenuLogic.Kind.OFF) ShapeTypeIcons.drawOff(g, 0, -4, 2, color);
            else ShapeTypeIcons.draw(g, entry.config() == null ? null : entry.config().type(), 2, -2, 2, color);
        }
        g.pose().pushPose();
        g.pose().translate(iconWidth, 0, 0);
        g.pose().scale(0.65F, 0.65F, 1);
        g.drawString(font, fitted, 0, 0, color, false);
        g.pose().popPose();
        if (entry.active()) {
            g.pose().translate(iconWidth + textWidth + 2, 1, 0);
            // Pixel check, independent of the selected language/font's glyph coverage.
            g.fill(0, 2, 1, 4, color);
            g.fill(1, 3, 2, 5, color);
            g.fill(2, 2, 3, 4, color);
            g.fill(3, 1, 4, 3, color);
            g.fill(4, 0, 5, 2, color);
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

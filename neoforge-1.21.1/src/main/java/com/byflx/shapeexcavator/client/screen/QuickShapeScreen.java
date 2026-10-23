package com.byflx.shapeexcavator.client.screen;

import com.byflx.shapeexcavator.client.ClientState;
import com.byflx.shapeexcavator.client.PresetRepository;
import com.byflx.shapeexcavator.client.QuickShapeAction;
import com.byflx.shapeexcavator.client.ShapeExcavatorClient;
import com.byflx.shapeexcavator.data.ToolShapeData;
import com.byflx.shapeexcavator.editor.QuickMenuLogic;
import com.byflx.shapeexcavator.registry.ModComponents;
import com.byflx.shapeexcavator.shape.ShapeConfig;
import com.byflx.shapeexcavator.shape.ShapeGenerator;
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
            Component details, boolean active, boolean enabled) { }

    private final List<WheelEntry> entries;
    private final int slot;
    private final ItemStack tool;
    private final ClientState.RememberedShape last;
    private final boolean worldPreview;
    private final double[] hover;
    private long openedAt, lastFrame;
    private boolean initialized, finished;
    private int page;

    public QuickShapeScreen(Player player) {
        super(Component.translatable("gui.shapeexcavator.quick.title"));
        slot = player.getInventory().selected;
        tool = player.getMainHandItem();
        last = ClientState.lastShape(player.getUUID()).orElse(null);
        ToolShapeData data = tool.get(ModComponents.TOOL_SHAPE.get());
        worldPreview = data != null && data.worldPreview();
        ShapeConfig active = data == null ? null : data.config().orElse(null);
        // Snapshot configs and generated summaries once; rendering never generates shapes or reads disk.
        entries = QuickMenuLogic.entries(PresetRepository.get().names(), last != null).stream().map(entry -> {
            ShapeConfig config = switch (entry.kind()) {
                case OFF, EMPTY -> null;
                case LAST -> last.config();
                case PRESET -> PresetRepository.get().find(entry.name()).orElse(null);
            };
            Component name = switch (entry.kind()) {
                case LAST -> Component.translatable("gui.shapeexcavator.quick.last");
                case OFF -> Component.translatable("gui.shapeexcavator.quick.off");
                case EMPTY -> Component.translatable("gui.shapeexcavator.quick.empty", ShapeExcavatorClient.editorKeyLabel());
                case PRESET -> Component.literal(entry.name());
            };
            boolean enabled = entry.kind() == QuickMenuLogic.Kind.OFF;
            Component details = switch (entry.kind()) {
                case OFF -> Component.translatable("gui.shapeexcavator.quick.mine_normally");
                case EMPTY -> Component.translatable("gui.shapeexcavator.quick.empty", ShapeExcavatorClient.editorKeyLabel());
                default -> Component.translatable("gui.shapeexcavator.quick.unavailable");
            };
            if (config != null && ShapeGenerator.generate(config, ClientState.limits()) instanceof ShapeGenerator.Valid valid) {
                var shape = valid.shape();
                details = Component.translatable("gui.shapeexcavator.quick.details",
                        shape.width(), shape.height(), shape.depth(), shape.count());
                enabled = true;
            }
            return new WheelEntry(entry, config, name, details,
                    QuickMenuLogic.isActive(entry, active, config), enabled);
        }).toList();
        hover = new double[entries.size()];
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
        if (!ShapeExcavatorClient.isEditorKeyDown()) {
            double mx = minecraft.mouseHandler.xpos() * width / minecraft.getWindow().getScreenWidth();
            double my = minecraft.mouseHandler.ypos() * height / minecraft.getWindow().getScreenHeight();
            finish(layout().highlighted(mx, my));
        }
    }

    private void finish(int index) {
        if (finished) return;
        finished = true;
        boolean canApply = validTool();
        ShapeExcavatorClient.cancelEditorGesture();
        minecraft.setScreen(null);
        if (!canApply || index < 0 || index >= entries.size()) return;
        WheelEntry selection = entries.get(index);
        if (!selection.enabled()) return;
        if (selection.entry().kind() == QuickMenuLogic.Kind.OFF) {
            QuickShapeAction.start(null, false, selection.name());
        } else {
            boolean preview = selection.entry().kind() == QuickMenuLogic.Kind.LAST ? last.worldPreview() : worldPreview;
            QuickShapeAction.start(selection.config(), preview, selection.name());
        }
    }

    @Override public boolean mouseClicked(double mx, double my, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            finish(layout().highlighted(mx, my));
            return true;
        }
        return super.mouseClicked(mx, my, button);
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
        for (int i = 0; i < hover.length; i++) {
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
            int index = l.offset() + segment;
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
            int index = l.offset() + segment;
            WheelEntry entry = entries.get(index);
            int color = alpha(entry.enabled() ? TEXT : MUTED, fade * (entry.enabled() ? 1 : 0.65));
            g.pose().pushPose();
            g.pose().translate(l.labelX(segment), l.labelY(segment), 0);
            g.pose().scale((float) l.scale(), (float) l.scale(), 1);
            switch (entry.entry().kind()) {
                case OFF -> ShapeTypeIcons.drawOff(g, -7, -7, 2, color);
                case LAST -> ShapeTypeIcons.drawLast(g, -7, -7, 2, color);
                case EMPTY -> ShapeTypeIcons.drawSave(g, -7, -7, 2, color);
                case PRESET -> ShapeTypeIcons.draw(g, entry.config() == null ? null : entry.config().type(), -5, -5, 2, color);
            }
            g.pose().popPose();
            line(g, entry.name(), l.labelX(segment), l.labelY(segment) + 8 * l.scale(),
                    l.labelWidth(segment), 0.65 * l.scale(), color);
        }
        renderHub(g, l, selected < 0 ? null : entries.get(selected), fade);
        g.pose().popPose();
        line(g, Component.translatable("gui.shapeexcavator.quick.hint", ShapeExcavatorClient.editorKeyLabel()),
                l.centerX(), l.footerY(), width - 8, 0.8 * l.scale(), alpha(TEXT, fade));
        if (l.pages() > 1) {
            line(g, Component.translatable("gui.shapeexcavator.quick.page", l.page() + 1, l.pages()),
                    l.centerX(), l.footerY() + 12 * l.scale(), width - 8, 0.7 * l.scale(), alpha(MUTED, fade));
        }
        g.flush();
    }

    private void renderHub(GuiGraphics g, QuickMenuLogic.Layout l, WheelEntry hovered, double fade) {
        Component name = hovered == null ? Component.translatable("gui.shapeexcavator.quick.cancel") : hovered.name();
        Component details = hovered == null ? Component.empty() : hovered.details();
        // Fit the full summary by scaling down to the hub's chord; keep it on exactly one line.
        double available = 2 * l.deadZone() - 8 * l.scale();
        double nameScale = 0.75 * l.scale();
        double detailScale = Math.min(0.6 * l.scale(), available / Math.max(1, font.width(details)));
        line(g, name.copy().withStyle(ChatFormatting.BOLD), l.centerX(),
                l.centerY() - (hovered == null ? 3 : 10) * l.scale(), available, nameScale, alpha(TEXT, fade));
        if (hovered != null) line(g, details, l.centerX(), l.centerY() + 3 * l.scale(),
                available, detailScale, alpha(MUTED, fade));
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

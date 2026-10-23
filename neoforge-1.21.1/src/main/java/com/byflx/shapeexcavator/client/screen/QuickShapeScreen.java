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
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;

/** A hold-to-select wheel, anchored to the crosshair. The centre cancels and scrolling pages. */
public final class QuickShapeScreen extends Screen {
    private static final int TEXT = 0xFFF1F5FC;
    private static final int MUTED = 0xFFB1BED0;
    private static final int GOLD = 0xFFF1C76B;
    private static final int HIGHLIGHT = 0xFF9CDFFF;
    private record WheelEntry(QuickMenuLogic.Entry entry, ShapeConfig config, Component name,
            Component details, boolean active) { }

    private final List<WheelEntry> entries;
    private final int slot;
    private final ItemStack tool;
    private final ClientState.RememberedShape last;
    private final boolean worldPreview;
    private final boolean hasPresets;
    private int page;

    public QuickShapeScreen(Player player) {
        super(Component.translatable("gui.shapeexcavator.quick.title"));
        slot = player.getInventory().selected;
        tool = player.getMainHandItem();
        last = ClientState.lastShape(player.getUUID()).orElse(null);
        ToolShapeData data = tool.get(ModComponents.TOOL_SHAPE.get());
        worldPreview = data != null && data.worldPreview();
        ShapeConfig active = data == null ? null : data.config().orElse(null);
        List<String> presets = PresetRepository.get().names();
        hasPresets = !presets.isEmpty();
        // Snapshot configs and generated summaries once; rendering never generates a shape or reads disk.
        entries = QuickMenuLogic.entries(presets, last != null).stream().map(entry -> {
            ShapeConfig config = switch (entry.kind()) {
                case OFF -> null;
                case LAST -> last.config();
                case PRESET -> PresetRepository.get().find(entry.name()).orElse(null);
            };
            Component name = switch (entry.kind()) {
                case LAST -> Component.translatable("gui.shapeexcavator.quick.last");
                case OFF -> Component.translatable("gui.shapeexcavator.quick.off");
                case PRESET -> Component.literal(entry.name());
            };
            Component details = Component.empty();
            if (config != null) {
                var result = ShapeGenerator.generate(config, ClientState.limits());
                if (result instanceof ShapeGenerator.Valid valid) {
                    var shape = valid.shape();
                    details = Component.translatable("gui.shapeexcavator.quick.details",
                            shape.width(), shape.height(), shape.depth(), shape.count());
                } else details = Component.translatable("gui.shapeexcavator.quick.unavailable");
            }
            return new WheelEntry(entry, config, name, details,
                    QuickMenuLogic.isActive(entry, active, config));
        }).toList();
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override public void onClose() {
        ShapeExcavatorClient.cancelEditorGesture();
        minecraft.setScreen(null);
    }

    private QuickMenuLogic.Layout layout() { return QuickMenuLogic.layout(width, height, entries.size(), page); }

    @Override public void tick() {
        if (!minecraft.isWindowActive() || minecraft.player == null || minecraft.player.getInventory().selected != slot
                || minecraft.player.getMainHandItem() != tool) { onClose(); return; }
        if (!ShapeExcavatorClient.isEditorKeyDown()) {
            double mx = minecraft.mouseHandler.xpos() * width / minecraft.getWindow().getScreenWidth();
            double my = minecraft.mouseHandler.ypos() * height / minecraft.getWindow().getScreenHeight();
            int selected = layout().highlighted(mx, my);
            ShapeExcavatorClient.cancelEditorGesture();
            minecraft.setScreen(null);
            if (selected >= 0) choose(entries.get(selected));
        }
    }

    private void choose(WheelEntry selection) {
        if (selection.entry().kind() == QuickMenuLogic.Kind.OFF) {
            QuickShapeAction.start(null, false, selection.name());
        } else if (selection.config() != null) {
            boolean preview = selection.entry().kind() == QuickMenuLogic.Kind.LAST ? last.worldPreview() : worldPreview;
            QuickShapeAction.start(selection.config(), preview, selection.name());
        }
    }

    @Override public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        page = QuickMenuLogic.layout(width, height, entries.size(), page - (int) Math.signum(sy)).page();
        return true;
    }

    /** Keep the world visible: no vanilla menu blur, panorama or full-screen dark rectangle. */
    @Override public void renderBackground(GuiGraphics g, int mx, int my, float partialTick) { }

    @Override public void render(GuiGraphics g, int mx, int my, float partialTick) {
        QuickMenuLogic.Layout l = layout();
        int selected = l.highlighted(mx, my);
        // A faint local halo and translucent circular hub keep the world visible around the wheel.
        arc(g, l, l.radius(), l.radius() + 5, 0, Math.PI * 2, 0x16050810);
        arc(g, l, 0, l.deadZone() - 3, 0, Math.PI * 2, 0xCC101722);
        arc(g, l, l.deadZone() - 4, l.deadZone() - 3, 0, Math.PI * 2,
                selected < 0 ? MUTED : 0x80586A80);
        for (int segment = 0; segment < l.segments(); segment++) {
            int index = l.offset() + segment;
            WheelEntry entry = entries.get(index);
            boolean hovered = index == selected;
            double half = Math.PI / l.segments();
            double gap = l.segments() == 1 ? 0 : 0.018;
            double start = l.angle(segment) - half + gap;
            double end = l.angle(segment) + half - gap;
            int outline = hovered ? HIGHLIGHT : entry.active() ? GOLD : 0x90586A80;
            arc(g, l, l.deadZone(), l.radius(), start, end, outline);
            arc(g, l, l.deadZone() + 1.5, l.radius() - 1.5, start + gap, end - gap,
                    hovered ? 0xF23A617F : 0xD51A2635);
        }
        // Flush all sector geometry before text/icons so their rendering order is unambiguous.
        g.flush();
        for (int segment = 0; segment < l.segments(); segment++) {
            int index = l.offset() + segment;
            WheelEntry entry = entries.get(index);
            int x = l.labelX(segment), y = l.labelY(segment);
            int color = index == selected ? HIGHLIGHT : entry.active() ? GOLD : TEXT;
            ShapeTypeIcons.draw(g, entry.config() == null ? null : entry.config().type(), x - 5, y - 11, 2, color);
            if (entry.active()) g.fill(x + 9, y - 10, x + 12, y - 7, GOLD);
            line(g, entry.name().getString(), x, y + 4, l.labelWidth(segment), color);
        }
        WheelEntry hovered = selected < 0 ? null : entries.get(selected);
        renderHub(g, l, hovered);
        int detailY = l.centerY() - l.radius() - 28;
        Component heading = hovered == null ? title : hovered.name();
        if (hovered != null && hovered.active()) {
            heading = heading.copy().append(Component.translatable("gui.shapeexcavator.quick.active"));
        }
        line(g, heading.getString(), l.centerX(), detailY, width - 16, TEXT);
        Component detail = hovered == null ? Component.translatable("gui.shapeexcavator.quick.aim") : hovered.details();
        line(g, detail.getString(), l.centerX(), detailY + 12, width - 16, MUTED);
        int footerY = l.footerY();
        if (l.pages() > 1) {
            line(g, Component.translatable("gui.shapeexcavator.quick.page", l.page() + 1, l.pages()).getString(),
                    l.centerX(), footerY, width - 16, MUTED);
            footerY += 12;
        }
        String hint = QuickMenuLogic.fitHint(
                Component.translatable("gui.shapeexcavator.quick.hint", ShapeExcavatorClient.editorKeyLabel()).getString(),
                Component.translatable("gui.shapeexcavator.quick.hint.short", ShapeExcavatorClient.editorKeyLabel()).getString(),
                width - 16, font::width);
        line(g, hint, l.centerX(), footerY, width - 16, TEXT);
        if (!hasPresets) {
            line(g, Component.translatable("gui.shapeexcavator.quick.empty", ShapeExcavatorClient.editorKeyLabel()).getString(),
                    l.centerX(), footerY + 12, width - 16, MUTED);
        }
    }

    private void renderHub(GuiGraphics g, QuickMenuLogic.Layout l, WheelEntry hovered) {
        // Slightly smaller hub text preserves the whole instruction at the minimum effective GUI size.
        float scale = 0.75F;
        g.pose().pushPose();
        g.pose().translate(l.centerX(), l.centerY(), 0);
        g.pose().scale(scale, scale, 1);
        int available = (int) ((l.deadZone() * 2 - 14) / scale);
        String name = hovered == null ? title.getString()
                : hovered.entry().kind() == QuickMenuLogic.Kind.OFF
                        ? Component.translatable("gui.shapeexcavator.quick.off.short").getString() : hovered.name().getString();
        String instruction = hovered == null
                ? Component.translatable("gui.shapeexcavator.quick.cancel").getString()
                : Component.translatable("gui.shapeexcavator.quick.release", ShapeExcavatorClient.editorKeyLabel()).getString();
        if (font.width(instruction) <= available) {
            line(g, name, 0, -12, available, hovered != null && hovered.active() ? GOLD : TEXT);
            line(g, instruction, 0, 5, available, MUTED);
        } else {
            line(g, name, 0, -19, available, hovered != null && hovered.active() ? GOLD : TEXT);
            line(g, Component.translatable(hovered == null ? "gui.shapeexcavator.quick.release.short"
                            : "gui.shapeexcavator.quick.release.key", ShapeExcavatorClient.editorKeyLabel()).getString(),
                    0, -3, available, MUTED);
            line(g, Component.translatable(hovered == null ? "gui.shapeexcavator.quick.cancel.short"
                            : "gui.shapeexcavator.quick.apply").getString(), 0, 9, available, MUTED);
        }
        g.pose().popPose();
    }

    private void line(GuiGraphics g, String text, int centerX, int y, int maxWidth, int color) {
        g.drawCenteredString(font, QuickMenuLogic.fitText(text, maxWidth, font::width), centerX, y, color);
    }

    /** Tessellated annular sector, using the same GUI render type and winding as GuiGraphics.fill. */
    private static void arc(GuiGraphics g, QuickMenuLogic.Layout l, double inner, double outer,
            double start, double end, int color) {
        VertexConsumer vertices = g.bufferSource().getBuffer(RenderType.gui());
        Matrix4f matrix = g.pose().last().pose();
        int steps = Math.max(1, (int) Math.ceil((end - start) * outer / 3));
        for (int step = 0; step < steps; step++) {
            double a = start + (end - start) * step / steps;
            double b = start + (end - start) * (step + 1) / steps;
            vertex(vertices, matrix, l, inner, a, color);
            vertex(vertices, matrix, l, inner, b, color);
            vertex(vertices, matrix, l, outer, b, color);
            vertex(vertices, matrix, l, outer, a, color);
        }
    }

    private static void vertex(VertexConsumer vertices, Matrix4f matrix, QuickMenuLogic.Layout l,
            double radius, double angle, int color) {
        vertices.addVertex(matrix, (float) (l.centerX() + Math.cos(angle) * radius),
                (float) (l.centerY() + Math.sin(angle) * radius), 0).setColor(color);
    }
}

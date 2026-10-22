package com.byflx.shapeexcavator.client.screen;

import com.byflx.shapeexcavator.client.ClientState;
import com.byflx.shapeexcavator.client.PresetRepository;
import com.byflx.shapeexcavator.data.ToolShapeData;
import com.byflx.shapeexcavator.editor.CustomMaskDraft;
import com.byflx.shapeexcavator.editor.EditorDraft;
import com.byflx.shapeexcavator.net.ApplyResultPayload;
import com.byflx.shapeexcavator.net.ApplyShapePayload;
import com.byflx.shapeexcavator.net.BeginEditPayload;
import com.byflx.shapeexcavator.net.EditSessionPayload;
import com.byflx.shapeexcavator.registry.ModComponents;
import com.byflx.shapeexcavator.shape.Cell;
import com.byflx.shapeexcavator.shape.GeneratedShape;
import com.byflx.shapeexcavator.shape.NumericInput;
import com.byflx.shapeexcavator.shape.ShapeConfig;
import com.byflx.shapeexcavator.shape.ShapeGenerator;
import com.byflx.shapeexcavator.shape.ShapeLimits;
import com.byflx.shapeexcavator.shape.ShapeType;
import com.byflx.shapeexcavator.text.ShapeTexts;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The shape editor (plan §2–§6). Left: shape type, free numeric fields with optional +/- buttons,
 * direction, transform and the custom layer editor. Right: the 3D preview of the selected blocks.
 * Bottom: summary, presets and the actions.
 *
 * <p>Everything the user changes lives in an {@link EditorDraft}; widgets are rebuilt from it in
 * {@link #init()}, so window resizing and GUI scale changes keep the draft. The draft only reaches
 * the tool through "Apply", and only the server's confirmation changes the tool. Cancel/Escape
 * never touch the tool; with unsaved changes a discard confirmation appears first.
 */
public final class ShapeEditorScreen extends Screen {
    private static final int ROW = 22;
    private static final int MARGIN = 6;
    private static final int FIELD_WIDTH = 40;
    private static final int STEP_WIDTH = 16;
    private static final int TEXT = 0xE0E0E0;
    private static final int MUTED = 0x909090;
    private static final int OK = 0x55FF55;
    private static final int ERROR = 0xFF5555;
    private static final int WARN = 0xFFDD55;

    private record RowLabel(Component text, int baseY) {
    }

    private final int slot;
    private EditorDraft draft;
    private boolean worldPreview;
    private boolean dirty;
    private boolean resultView;
    private EditorDraft.Evaluation evaluation;
    private GeneratedShape lastValid;
    private final PreviewCamera camera = new PreviewCamera();
    private final ShapePreviewRenderer preview = new ShapePreviewRenderer();

    private boolean sessionRequested;
    private int sessionId;
    private long revision;
    private boolean awaitingReply;
    private boolean closeAfterApply;
    private Component status = Component.translatable("gui.shapeexcavator.status.connecting");
    private int statusColor = MUTED;

    private int layer;
    private boolean anchorMode;
    private CustomMaskDraft.ResizeCheck pendingResize;
    private Boolean paintValue;
    private boolean draggingCamera;
    private String presetCursor = "";

    private int leftX;
    private int leftW;
    private int contentTop;
    private int contentBottom;
    private int contentHeight;
    private int scroll;
    private int previewX0;
    private int previewY0;
    private int previewX1;
    private int previewY1;
    private int summaryY;
    private int gridBaseY = -1;
    private int cellSize;

    private final Map<AbstractWidget, Integer> scrolledWidgets = new LinkedHashMap<>();
    private final List<RowLabel> labels = new ArrayList<>();
    private final Map<String, EditBox> fieldBoxes = new HashMap<>();
    private EditBox layerBox;
    private EditBox presetName;
    private Button applyButton;
    private Button applyShowButton;
    private Button undoButton;
    private Button redoButton;
    private Button pasteButton;
    private Button anchorButton;
    private Button resizeConfirmButton;
    private Button resizeCancelButton;

    public ShapeEditorScreen(Player player) {
        super(Component.translatable("gui.shapeexcavator.title"));
        this.slot = player.getInventory().selected;
        ToolShapeData data = player.getMainHandItem().get(ModComponents.TOOL_SHAPE.get());
        this.draft = data != null && data.config().isPresent()
                ? EditorDraft.fromConfig(data.config().get())
                : EditorDraft.defaults();
        this.worldPreview = data != null && data.worldPreview();
        this.revision = data == null ? 0L : data.revision();
        if (data != null && data.isDamaged()) {
            status = Component.translatable("gui.shapeexcavator.status.damaged");
            statusColor = WARN;
        }
        ClientState.setEditor(this);
    }

    @Override
    public boolean isPauseScreen() {
        // The integrated server must keep running to answer the editor session.
        return false;
    }

    @Override
    protected void init() {
        scrolledWidgets.clear();
        labels.clear();
        fieldBoxes.clear();
        layerBox = null;
        undoButton = null;
        redoButton = null;
        pasteButton = null;
        anchorButton = null;
        resizeConfirmButton = null;
        resizeCancelButton = null;
        gridBaseY = -1;
        layout();
        if (!sessionRequested) {
            sessionRequested = true;
            PacketDistributor.sendToServer(new BeginEditPayload(slot));
        }

        int y = contentTop;
        CycleButton<ShapeType> typeButton = CycleButton.<ShapeType>builder(ShapeTexts::typeName)
                .withValues(ShapeType.values())
                .withInitialValue(draft.type())
                .create(leftX, y, leftW, 20, Component.translatable("gui.shapeexcavator.shape"),
                        (button, value) -> changeType(value));
        scrolled(typeButton, y);
        y += ROW;
        for (String field : EditorDraft.fields(draft.type())) {
            fieldRow(field, y);
            y += ROW;
        }
        switch (draft.type()) {
            case CUBOID -> {
                buttonRow(y,
                        Button.builder(Component.translatable("gui.shapeexcavator.template.surface"), b -> {
                            draft.applySurfaceTemplate();
                            syncFieldBoxes();
                        }),
                        Button.builder(Component.translatable("gui.shapeexcavator.template.line"), b -> {
                            draft.applyLineTemplate();
                            syncFieldBoxes();
                        }));
                y += ROW;
            }
            case STAIR, CORRIDOR -> {
                boolean stair = draft.type() == ShapeType.STAIR;
                CycleButton<Boolean> direction = CycleButton.booleanBuilder(
                                Component.translatable(stair ? "gui.shapeexcavator.ascending" : "gui.shapeexcavator.upward"),
                                Component.translatable(stair ? "gui.shapeexcavator.descending" : "gui.shapeexcavator.downward"))
                        .withInitialValue(stair ? draft.stairAscending() : draft.corridorUpward())
                        .create(leftX, y, leftW, 20, Component.translatable("gui.shapeexcavator.direction"),
                                (button, value) -> {
                                    if (stair) {
                                        draft.setStairAscending(value);
                                    } else {
                                        draft.setCorridorUpward(value);
                                    }
                                    changed();
                                });
                scrolled(direction, y);
                y += ROW;
                labels.add(new RowLabel(Component.translatable("gui.shapeexcavator.stair_orientation"), y - 4));
                y += 12;
            }
            case CUSTOM -> y = customRows(y);
            default -> {
            }
        }
        buttonRow(y,
                Button.builder(Component.translatable("gui.shapeexcavator.rotate"), b -> {
                    draft.rotateClockwise();
                    changed();
                }),
                Button.builder(Component.translatable("gui.shapeexcavator.mirror"), b -> {
                    draft.toggleMirror();
                    changed();
                }));
        y += ROW;
        if (draft.type() != ShapeType.CUSTOM) {
            buttonRow(y, Button.builder(Component.translatable("gui.shapeexcavator.to_custom"), b -> convertToCustom()));
            y += ROW;
        } else {
            gridBaseY = y;
        }
        contentHeight = y - contentTop;
        layoutGrid();
        scroll = Mth.clamp(scroll, 0, maxScroll());

        initViewRow();
        initPresetRow();
        initActionRow();
        refresh();
    }

    // ---------------------------------------------------------------- layout

    private void layout() {
        leftX = MARGIN;
        leftW = Mth.clamp((int) (width * 0.42F), 150, 230);
        int actionY = height - 26;
        int presetY = actionY - 22;
        summaryY = presetY - 24;
        contentTop = 20;
        contentBottom = summaryY - 4;
        previewX0 = leftX + leftW + 10;
        previewX1 = width - MARGIN;
        previewY0 = contentTop;
        previewY1 = Math.max(previewY0 + 20, contentBottom - 22);
    }

    private void fieldRow(String field, int y) {
        int plusX = leftX + leftW - STEP_WIDTH;
        int boxX = plusX - 2 - FIELD_WIDTH;
        int minusX = boxX - 2 - STEP_WIDTH;
        labels.add(new RowLabel(ShapeTexts.fieldName(field), y));
        scrolled(Button.builder(Component.literal("-"), b -> step(field, -1))
                .bounds(minusX, y, STEP_WIDTH, 20).build(), y);
        EditBox box = new EditBox(font, boxX + 1, y + 1, FIELD_WIDTH - 2, 18, ShapeTexts.fieldName(field));
        // One character over the parser's limit, so an over-long entry is shown as an error, not cut off.
        box.setMaxLength(NumericInput.MAX_LENGTH + 1);
        box.setValue(draft.text(field));
        box.setResponder(text -> onFieldEdited(field, text));
        scrolled(box, y + 1);
        fieldBoxes.put(field, box);
        scrolled(Button.builder(Component.literal("+"), b -> step(field, 1))
                .bounds(plusX, y, STEP_WIDTH, 20).build(), y);
    }

    private int customRows(int y) {
        CustomMaskDraft custom = draft.custom();
        layer = Mth.clamp(layer, 0, custom.depth() - 1);
        labels.add(new RowLabel(Component.translatable("gui.shapeexcavator.layer"), y));
        int nextX = leftX + leftW - STEP_WIDTH;
        int boxX = nextX - 2 - FIELD_WIDTH;
        int prevX = boxX - 2 - STEP_WIDTH;
        scrolled(Button.builder(Component.literal("<"), b -> setLayer(layer - 1))
                .bounds(prevX, y, STEP_WIDTH, 20).build(), y);
        layerBox = new EditBox(font, boxX + 1, y + 1, FIELD_WIDTH - 2, 18, Component.translatable("gui.shapeexcavator.layer"));
        layerBox.setMaxLength(NumericInput.MAX_LENGTH + 1);
        layerBox.setValue(Integer.toString(layer + 1));
        layerBox.setResponder(this::onLayerEdited);
        scrolled(layerBox, y + 1);
        scrolled(Button.builder(Component.literal(">"), b -> setLayer(layer + 1))
                .bounds(nextX, y, STEP_WIDTH, 20).build(), y);
        y += ROW;

        List<Button> layerOps = buttonRow(y,
                Button.builder(Component.translatable("gui.shapeexcavator.layer.copy"), b -> {
                    draft.custom().copyLayer(layer);
                    refresh();
                }),
                Button.builder(Component.translatable("gui.shapeexcavator.layer.paste"),
                        b -> customEdited(draft.custom().pasteLayer(layer))),
                Button.builder(Component.translatable("gui.shapeexcavator.layer.fill"),
                        b -> customEdited(draft.custom().fillLayer(layer, true))),
                Button.builder(Component.translatable("gui.shapeexcavator.layer.clear"),
                        b -> customEdited(draft.custom().fillLayer(layer, false))));
        pasteButton = layerOps.get(1);
        y += ROW;

        List<Button> history = buttonRow(y,
                Button.builder(Component.translatable("gui.shapeexcavator.undo"), b -> {
                    if (draft.custom().undo()) {
                        afterCustomStructureChange();
                    }
                }),
                Button.builder(Component.translatable("gui.shapeexcavator.redo"), b -> {
                    if (draft.custom().redo()) {
                        afterCustomStructureChange();
                    }
                }),
                Button.builder(Component.translatable("gui.shapeexcavator.anchor"), b -> {
                    anchorMode = !anchorMode;
                    refresh();
                }));
        undoButton = history.get(0);
        redoButton = history.get(1);
        anchorButton = history.get(2);
        y += ROW;

        List<Button> resize = buttonRow(y,
                Button.builder(Component.empty(), b -> confirmResize()),
                Button.builder(Component.translatable("gui.shapeexcavator.resize.keep"), b -> cancelResize()));
        resizeConfirmButton = resize.get(0);
        resizeCancelButton = resize.get(1);
        y += ROW;
        labels.add(new RowLabel(Component.translatable("gui.shapeexcavator.grid_hint"), y - 4));
        y += 12;
        return y;
    }

    private List<Button> buttonRow(int y, Button.Builder... builders) {
        int gap = 2;
        int w = (leftW - gap * (builders.length - 1)) / builders.length;
        List<Button> buttons = new ArrayList<>(builders.length);
        for (int i = 0; i < builders.length; i++) {
            Button button = builders[i].bounds(leftX + i * (w + gap), y, w, 20).build();
            scrolled(button, y);
            buttons.add(button);
        }
        return buttons;
    }

    private void initViewRow() {
        boolean stairLike = draft.type() == ShapeType.STAIR || draft.type() == ShapeType.CORRIDOR;
        List<Button.Builder> builders = new ArrayList<>(List.of(
                Button.builder(Component.translatable("gui.shapeexcavator.view.front"), b -> camera.front()),
                Button.builder(Component.translatable("gui.shapeexcavator.view.side"), b -> camera.side()),
                Button.builder(Component.translatable("gui.shapeexcavator.view.top"), b -> camera.top()),
                Button.builder(Component.translatable("gui.shapeexcavator.view.reset"), b -> camera.reset())));
        if (stairLike) {
            builders.add(Button.builder(Component.translatable("gui.shapeexcavator.view.result"), b -> {
                resultView = !resultView;
            }).tooltip(Tooltip.create(Component.translatable("gui.shapeexcavator.view.result.tooltip"))));
        } else {
            resultView = false;
        }
        int gap = 2;
        int total = previewX1 - previewX0;
        int w = (total - gap * (builders.size() - 1)) / builders.size();
        for (int i = 0; i < builders.size(); i++) {
            addRenderableWidget(builders.get(i).bounds(previewX0 + i * (w + gap), previewY1 + 2, w, 20).build());
        }
    }

    private void initPresetRow() {
        int y = height - 26 - 22;
        int x = MARGIN;
        int nameW = Mth.clamp((width - 2 * MARGIN) / 3, 80, 160);
        presetName = new EditBox(font, x + 1, y + 1, nameW - 2, 18, Component.translatable("gui.shapeexcavator.preset.name"));
        presetName.setMaxLength(PresetRepository.MAX_NAME_LENGTH);
        presetName.setHint(Component.translatable("gui.shapeexcavator.preset.name"));
        presetName.setValue(presetCursor);
        presetName.setResponder(text -> presetCursor = text);
        addRenderableWidget(presetName);
        x += nameW + 2;
        int rest = width - MARGIN - x;
        int small = 16;
        int w = (rest - 2 * small - 4 * 2) / 3;
        addRenderableWidget(Button.builder(Component.literal("<"), b -> cyclePreset(-1)).bounds(x, y, small, 20).build());
        x += small + 2;
        addRenderableWidget(Button.builder(Component.literal(">"), b -> cyclePreset(1)).bounds(x, y, small, 20).build());
        x += small + 2;
        addRenderableWidget(Button.builder(Component.translatable("gui.shapeexcavator.preset.load"), b -> loadPreset())
                .bounds(x, y, w, 20).build());
        x += w + 2;
        addRenderableWidget(Button.builder(Component.translatable("gui.shapeexcavator.preset.save"), b -> savePreset())
                .bounds(x, y, w, 20).build());
        x += w + 2;
        addRenderableWidget(Button.builder(Component.translatable("gui.shapeexcavator.preset.delete"), b -> deletePreset())
                .bounds(x, y, w, 20).build());
    }

    private void initActionRow() {
        int y = height - 26;
        int gap = 2;
        int w = (width - 2 * MARGIN - 3 * gap) / 4;
        int x = MARGIN;
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose())
                .bounds(x, y, w, 20).build());
        x += w + gap;
        addRenderableWidget(CycleButton.onOffBuilder(worldPreview)
                .create(x, y, w, 20, Component.translatable("gui.shapeexcavator.world_preview"), (button, value) -> {
                    worldPreview = value;
                    dirty = true;
                }));
        x += w + gap;
        applyButton = addRenderableWidget(Button.builder(Component.translatable("gui.shapeexcavator.apply"),
                b -> apply(false)).bounds(x, y, w, 20).build());
        x += w + gap;
        applyShowButton = addRenderableWidget(Button.builder(Component.translatable("gui.shapeexcavator.apply_show"),
                b -> apply(true)).bounds(x, y, w, 20).build());
    }

    private <T extends AbstractWidget> T scrolled(T widget, int baseY) {
        scrolledWidgets.put(widget, baseY);
        return addRenderableWidget(widget);
    }

    private int maxScroll() {
        return Math.max(0, contentHeight - (contentBottom - contentTop));
    }

    private void applyScroll() {
        for (Map.Entry<AbstractWidget, Integer> entry : scrolledWidgets.entrySet()) {
            AbstractWidget widget = entry.getKey();
            int y = entry.getValue() - scroll;
            widget.setY(y);
            boolean inside = y >= contentTop && y + widget.getHeight() <= contentBottom;
            boolean wanted = (widget != resizeConfirmButton && widget != resizeCancelButton) || pendingResize != null;
            widget.visible = inside && wanted;
        }
    }

    private void layoutGrid() {
        if (gridBaseY < 0) {
            return;
        }
        CustomMaskDraft custom = draft.custom();
        cellSize = Mth.clamp(Math.min(leftW / custom.width(), 220 / custom.height()), 3, 12);
        contentHeight = gridBaseY + cellSize * custom.height() + 4 - contentTop;
    }

    private int gridX() {
        return leftX + (leftW - cellSize * draft.custom().width()) / 2;
    }

    // ---------------------------------------------------------------- state changes

    private ShapeLimits limits() {
        return ClientState.limits();
    }

    private void changed() {
        dirty = true;
        refresh();
    }

    private void changeType(ShapeType type) {
        draft.setType(type);
        anchorMode = false;
        pendingResize = null;
        scroll = 0;
        dirty = true;
        rebuildWidgets();
    }

    private void onFieldEdited(String field, String text) {
        draft.setText(field, text);
        dirty = true;
        if (draft.type() == ShapeType.CUSTOM) {
            handleCustomResize();
        }
        refresh();
    }

    private void step(String field, int delta) {
        draft.stepped(field, delta, limits()).ifPresent(text -> fieldBoxes.get(field).setValue(text));
    }

    /** Writes the draft's texts back into the boxes, e.g. after a template or undo changed them. */
    private void syncFieldBoxes() {
        for (Map.Entry<String, EditBox> entry : fieldBoxes.entrySet()) {
            String text = draft.text(entry.getKey());
            if (!entry.getValue().getValue().equals(text)) {
                entry.getValue().setValue(text);
            }
        }
        changed();
    }

    private void handleCustomResize() {
        pendingResize = null;
        Optional<CustomMaskDraft.ResizeCheck> request = draft.requestedCustomResize(limits());
        if (request.isEmpty()) {
            return;
        }
        CustomMaskDraft.ResizeCheck check = request.get();
        if (!check.anchorInside()) {
            setStatus(Component.translatable("gui.shapeexcavator.resize.anchor_outside"), ERROR);
            return;
        }
        if (check.lostCells() > 0) {
            // Shrinking would delete selected cells: wait for an explicit confirmation.
            pendingResize = check;
            return;
        }
        draft.custom().resize(check.width(), check.height(), check.depth());
        afterResize();
    }

    private void confirmResize() {
        if (pendingResize != null) {
            draft.custom().resize(pendingResize.width(), pendingResize.height(), pendingResize.depth());
            pendingResize = null;
            dirty = true;
            afterResize();
            refresh();
        }
    }

    private void cancelResize() {
        pendingResize = null;
        draft.syncCustomTexts();
        syncFieldBoxes();
    }

    private void afterResize() {
        setLayer(layer);
        layoutGrid();
        scroll = Mth.clamp(scroll, 0, maxScroll());
    }

    /** After undo/redo the grid size may differ; bring fields, layer and grid in line. */
    private void afterCustomStructureChange() {
        pendingResize = null;
        draft.syncCustomTexts();
        afterResize();
        syncFieldBoxes();
    }

    private void customEdited(boolean changedSomething) {
        if (changedSomething) {
            changed();
        }
    }

    private void setLayer(int newLayer) {
        layer = Mth.clamp(newLayer, 0, draft.custom().depth() - 1);
        if (layerBox != null && !layerBox.getValue().equals(Integer.toString(layer + 1))) {
            layerBox.setValue(Integer.toString(layer + 1));
        }
    }

    private void onLayerEdited(String text) {
        if (NumericInput.parse(text, 1, draft.custom().depth()) instanceof NumericInput.Valid valid) {
            layer = valid.value() - 1;
            layerBox.setTextColor(TEXT);
        } else {
            layerBox.setTextColor(ERROR);
        }
    }

    private void convertToCustom() {
        if (draft.convertToCustom(limits())) {
            layer = 0;
            scroll = 0;
            dirty = true;
            rebuildWidgets();
        } else {
            setStatus(Component.translatable("gui.shapeexcavator.to_custom.invalid"), ERROR);
        }
    }

    private void setStatus(Component message, int color) {
        status = message;
        statusColor = color;
    }

    private void refresh() {
        evaluation = draft.evaluate(limits());
        evaluation.shape().ifPresent(shape -> lastValid = shape);
        for (Map.Entry<String, EditBox> entry : fieldBoxes.entrySet()) {
            NumericInput.Problem problem = evaluation.fieldErrors().get(entry.getKey());
            entry.getValue().setTextColor(problem == null ? TEXT : ERROR);
            entry.getValue().setTooltip(problem == null ? null
                    : Tooltip.create(ShapeTexts.problem(entry.getKey(), problem, 1, limits().maxAxis())));
        }
        if (undoButton != null) {
            undoButton.active = draft.custom().canUndo();
            redoButton.active = draft.custom().canRedo();
            pasteButton.active = draft.custom().hasClipboard();
            anchorButton.setMessage(Component.translatable(anchorMode
                    ? "gui.shapeexcavator.anchor.picking" : "gui.shapeexcavator.anchor"));
        }
        if (resizeConfirmButton != null && pendingResize != null) {
            resizeConfirmButton.setMessage(
                    Component.translatable("gui.shapeexcavator.resize.confirm", pendingResize.lostCells()));
        }
        if (applyButton != null) {
            boolean canApply = canApply();
            applyButton.active = canApply;
            applyShowButton.active = canApply;
        }
        applyScroll();
    }

    private boolean canApply() {
        return sessionId != 0 && !awaitingReply && pendingResize == null && evaluation != null && evaluation.isValid();
    }

    // ---------------------------------------------------------------- presets

    private void cyclePreset(int direction) {
        List<String> names = PresetRepository.get().names();
        if (names.isEmpty()) {
            setStatus(Component.translatable("gui.shapeexcavator.preset.none"), MUTED);
            return;
        }
        int index = names.indexOf(presetName.getValue().trim());
        int next = index < 0 ? (direction > 0 ? 0 : names.size() - 1) : Math.floorMod(index + direction, names.size());
        presetName.setValue(names.get(next));
    }

    private void loadPreset() {
        String name = presetName.getValue();
        Optional<ShapeConfig> preset = PresetRepository.get().find(name);
        if (preset.isEmpty()) {
            setStatus(Component.translatable("gui.shapeexcavator.preset.missing", name.trim()), ERROR);
            return;
        }
        // Loaded as a draft; the evaluation applies this server's limits again.
        draft = EditorDraft.fromConfig(preset.get());
        pendingResize = null;
        anchorMode = false;
        layer = 0;
        scroll = 0;
        dirty = true;
        setStatus(Component.translatable("gui.shapeexcavator.preset.loaded", name.trim()), OK);
        rebuildWidgets();
    }

    private void savePreset() {
        if (evaluation == null || evaluation.config() == null) {
            setStatus(Component.translatable("gui.shapeexcavator.preset.unsavable"), ERROR);
            return;
        }
        String name = presetName.getValue();
        switch (PresetRepository.get().save(name, evaluation.config())) {
            case SAVED -> setStatus(Component.translatable("gui.shapeexcavator.preset.saved", name.trim()), OK);
            case INVALID_NAME -> setStatus(Component.translatable("gui.shapeexcavator.preset.invalid_name",
                    PresetRepository.MAX_NAME_LENGTH), ERROR);
            case FULL -> setStatus(Component.translatable("gui.shapeexcavator.preset.full",
                    PresetRepository.MAX_PRESETS), ERROR);
            case IO_ERROR -> setStatus(Component.translatable("gui.shapeexcavator.preset.io_error"), ERROR);
        }
    }

    private void deletePreset() {
        String name = presetName.getValue();
        if (PresetRepository.get().delete(name)) {
            setStatus(Component.translatable("gui.shapeexcavator.preset.deleted", name.trim()), OK);
        } else {
            setStatus(Component.translatable("gui.shapeexcavator.preset.missing", name.trim()), ERROR);
        }
    }

    // ---------------------------------------------------------------- server protocol

    private void apply(boolean showInWorld) {
        if (!canApply()) {
            return;
        }
        awaitingReply = true;
        closeAfterApply = showInWorld;
        if (showInWorld) {
            worldPreview = true;
        }
        setStatus(Component.translatable("gui.shapeexcavator.status.applying"), MUTED);
        PacketDistributor.sendToServer(new ApplyShapePayload(sessionId, slot, revision, evaluation.config(),
                worldPreview));
        refresh();
    }

    public void onSession(EditSessionPayload payload) {
        if (payload.ok()) {
            sessionId = payload.sessionId();
            revision = payload.revision();
            if (statusColor == MUTED) {
                setStatus(Component.empty(), MUTED);
            }
        } else {
            sessionId = 0;
            setStatus(payload.message(), ERROR);
        }
        refresh();
    }

    public void onApplyResult(ApplyResultPayload payload) {
        if (payload.sessionId() != sessionId) {
            return;
        }
        awaitingReply = false;
        if (payload.accepted()) {
            revision = payload.revision();
            dirty = false;
            setStatus(payload.message(), OK);
            if (closeAfterApply) {
                closeAfterApply = false;
                closeNow();
                return;
            }
        } else {
            closeAfterApply = false;
            setStatus(payload.message(), ERROR);
        }
        refresh();
    }

    @Override
    public void onClose() {
        if (!dirty) {
            closeNow();
            return;
        }
        minecraft.setScreen(new ConfirmScreen(discard -> {
            if (discard) {
                closeNow();
            } else {
                minecraft.setScreen(this);
            }
        }, Component.translatable("gui.shapeexcavator.discard.title"),
                Component.translatable("gui.shapeexcavator.discard.message")));
    }

    private void closeNow() {
        if (ClientState.editor() == this) {
            ClientState.setEditor(null);
        }
        minecraft.setScreen(null);
    }

    // ---------------------------------------------------------------- input

    private boolean inPreview(double mx, double my) {
        return mx >= previewX0 && mx < previewX1 && my >= previewY0 && my < previewY1;
    }

    /** The custom grid cell under the mouse in the current layer, or null. */
    private Cell gridCell(double mx, double my) {
        if (gridBaseY < 0 || my < contentTop || my >= contentBottom) {
            return null;
        }
        CustomMaskDraft custom = draft.custom();
        int gx = gridX();
        int gy = gridBaseY - scroll;
        int col = (int) Math.floor((mx - gx) / cellSize);
        int row = (int) Math.floor((my - gy) / cellSize);
        if (col < 0 || col >= custom.width() || row < 0 || row >= custom.height()) {
            return null;
        }
        return new Cell(col, custom.height() - 1 - row, layer);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) {
            return true;
        }
        if (button == 0 && inPreview(mx, my)) {
            draggingCamera = true;
            return true;
        }
        Cell cell = gridCell(mx, my);
        if (cell != null && (button == 0 || button == 1)) {
            setFocused(null);
            if (anchorMode && button == 0) {
                anchorMode = false;
                customEdited(draft.custom().setAnchor(cell.x(), cell.y(), cell.z()));
                refresh();
                return true;
            }
            paintValue = button == 0;
            draft.custom().beginStroke();
            paint(cell);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (draggingCamera) {
            camera.drag(dx, dy);
            return true;
        }
        if (paintValue != null) {
            Cell cell = gridCell(mx, my);
            if (cell != null) {
                paint(cell);
            }
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (paintValue != null) {
            draft.custom().endStroke();
            paintValue = null;
        }
        draggingCamera = false;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (inPreview(mx, my)) {
            camera.scroll(scrollY);
            return true;
        }
        if (mx >= leftX && mx < leftX + leftW && my >= contentTop && my < contentBottom && maxScroll() > 0) {
            scroll = Mth.clamp(scroll - (int) Math.round(scrollY * ROW), 0, maxScroll());
            applyScroll();
            return true;
        }
        return super.mouseScrolled(mx, my, scrollX, scrollY);
    }

    /** Sets, never toggles, so dragging over a cell twice keeps it as painted (plan §6.5). */
    private void paint(Cell cell) {
        if (draft.custom().set(cell.x(), cell.y(), cell.z(), paintValue)) {
            changed();
        }
    }

    // ---------------------------------------------------------------- rendering

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(font, title, leftX, 6, 0xFFFFFF);

        graphics.enableScissor(leftX, contentTop, leftX + leftW, contentBottom);
        for (RowLabel label : labels) {
            int y = label.baseY() - scroll;
            if (y + 20 > contentTop && y < contentBottom) {
                graphics.drawString(font, label.text(), leftX, y + 6, TEXT);
            }
        }
        renderGrid(graphics, mouseX, mouseY);
        graphics.disableScissor();
        if (maxScroll() > 0) {
            int track = contentBottom - contentTop;
            int thumb = Math.max(10, track * track / (track + maxScroll()));
            int thumbY = contentTop + (track - thumb) * scroll / maxScroll();
            graphics.fill(leftX + leftW + 3, contentTop, leftX + leftW + 5, contentBottom, 0x40FFFFFF);
            graphics.fill(leftX + leftW + 3, thumbY, leftX + leftW + 5, thumbY + thumb, 0xC0FFFFFF);
        }

        renderPreview(graphics);
        renderSummary(graphics);
    }

    private void renderGrid(GuiGraphics graphics, int mouseX, int mouseY) {
        if (gridBaseY < 0) {
            return;
        }
        CustomMaskDraft custom = draft.custom();
        int gx = gridX();
        int gy = gridBaseY - scroll;
        int w = custom.width();
        int h = custom.height();
        graphics.fill(gx - 1, gy - 1, gx + w * cellSize, gy + h * cellSize, 0xFF101010);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int sx = gx + x * cellSize;
                int sy = gy + (h - 1 - y) * cellSize;
                int color;
                if (custom.get(x, y, layer)) {
                    color = 0xFF4CAF50;
                } else if (custom.get(x, y, layer - 1) || custom.get(x, y, layer + 1)) {
                    // Neighbouring layers shine through faintly.
                    color = 0xFF2F4A35;
                } else {
                    color = 0xFF2A2A2A;
                }
                graphics.fill(sx, sy, sx + cellSize - 1, sy + cellSize - 1, color);
            }
        }
        Cell anchor = custom.anchor();
        if (anchor.z() == layer) {
            int sx = gx + anchor.x() * cellSize;
            int sy = gy + (h - 1 - anchor.y()) * cellSize;
            graphics.renderOutline(sx - 1, sy - 1, cellSize + 1, cellSize + 1, 0xFFF1C232);
        }
        Cell hovered = gridCell(mouseX, mouseY);
        if (hovered != null) {
            int sx = gx + hovered.x() * cellSize;
            int sy = gy + (h - 1 - hovered.y()) * cellSize;
            graphics.renderOutline(sx, sy, cellSize - 1, cellSize - 1, anchorMode ? 0xFFF1C232 : 0xFFFFFFFF);
        }
    }

    private void renderPreview(GuiGraphics graphics) {
        graphics.fill(previewX0, previewY0, previewX1, previewY1, 0xFF0E1116);
        graphics.renderOutline(previewX0, previewY0, previewX1 - previewX0, previewY1 - previewY0, 0xFF3A3F47);
        GeneratedShape current = evaluation == null ? null : evaluation.shape().orElse(null);
        GeneratedShape shown = current != null ? current : lastValid;
        boolean stairLike = draft.type() == ShapeType.STAIR || draft.type() == ShapeType.CORRIDOR;
        if (shown != null) {
            preview.render(graphics, font, previewX0 + 1, previewY0 + 1, previewX1 - 1, previewY1 - 1, shown, camera,
                    resultView && stairLike);
        }
        if (current == null && lastValid != null) {
            graphics.drawString(font, Component.translatable("gui.shapeexcavator.last_valid"), previewX0 + 4,
                    previewY0 + 4, WARN);
        } else if (resultView && stairLike) {
            graphics.drawString(font, Component.translatable("gui.shapeexcavator.view.result.label"), previewX0 + 4,
                    previewY0 + 4, WARN);
        } else {
            graphics.drawString(font, Component.translatable("gui.shapeexcavator.preview.selection"), previewX0 + 4,
                    previewY0 + 4, MUTED);
        }
    }

    private void renderSummary(GuiGraphics graphics) {
        GeneratedShape current = evaluation == null ? null : evaluation.shape().orElse(null);
        GeneratedShape shown = current != null ? current : lastValid;
        if (shown != null) {
            Component line = ShapeTexts.extents(shown).append("    ").append(ShapeTexts.selected(shown));
            graphics.drawString(font, line, leftX, summaryY, current != null ? TEXT : MUTED);
        }
        Component problem = currentProblem();
        if (problem != null) {
            graphics.drawString(font, problem, leftX, summaryY + 11, ERROR);
        } else if (!status.getString().isEmpty()) {
            graphics.drawString(font, status, leftX, summaryY + 11, statusColor);
        }
    }

    private Component currentProblem() {
        if (evaluation == null) {
            return null;
        }
        if (!evaluation.fieldErrors().isEmpty()) {
            Map.Entry<String, NumericInput.Problem> first = evaluation.fieldErrors().entrySet().iterator().next();
            return ShapeTexts.problem(first.getKey(), first.getValue(), 1, limits().maxAxis());
        }
        if (pendingResize != null) {
            return Component.translatable("gui.shapeexcavator.resize.pending", pendingResize.lostCells());
        }
        if (evaluation.resizePending()) {
            return Component.translatable("gui.shapeexcavator.resize.size_mismatch");
        }
        if (evaluation.result() instanceof ShapeGenerator.Invalid invalid) {
            return ShapeTexts.issue(invalid.issues().get(0));
        }
        return null;
    }
}

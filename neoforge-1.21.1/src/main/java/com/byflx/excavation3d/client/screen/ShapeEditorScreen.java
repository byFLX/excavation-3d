package com.byflx.excavation3d.client.screen;

import com.byflx.excavation3d.client.ClientState;
import com.byflx.excavation3d.client.PresetRepository;
import com.byflx.excavation3d.data.ToolShapeData;
import com.byflx.excavation3d.editor.CustomMaskDraft;
import com.byflx.excavation3d.editor.ApplyAvailability;
import com.byflx.excavation3d.editor.DraftSaveState;
import com.byflx.excavation3d.editor.EditorDraft;
import com.byflx.excavation3d.editor.EditorLayout;
import com.byflx.excavation3d.editor.SessionHandshake;
import com.byflx.excavation3d.editor.SizeRowStep;
import com.byflx.excavation3d.net.ApplyResultPayload;
import com.byflx.excavation3d.net.ApplyShapePayload;
import com.byflx.excavation3d.net.BeginEditPayload;
import com.byflx.excavation3d.net.EditSessionPayload;
import com.byflx.excavation3d.preview.ShapeDiff;
import com.byflx.excavation3d.preview.CustomCanvas;
import com.byflx.excavation3d.preview.PreviewPicking;
import com.byflx.excavation3d.preview.PreviewGesture;
import com.byflx.excavation3d.registry.ModComponents;
import com.byflx.excavation3d.shape.Cell;
import com.byflx.excavation3d.shape.GeneratedShape;
import com.byflx.excavation3d.shape.NumericInput;
import com.byflx.excavation3d.shape.ShapeGenerator;
import com.byflx.excavation3d.shape.ShapeLimits;
import com.byflx.excavation3d.shape.ShapeType;
import com.byflx.excavation3d.text.ShapeTexts;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/** Local draft editor. Done applies and enables through the confirmed server session; Escape discards. */
public final class ShapeEditorScreen extends Screen {
    private static final int ROW = 22;
    private static final int MARGIN = 6;
    private static final int FIELD_WIDTH = 32;
    private static final int STEP_WIDTH = 14;
    private static final int ICON_SIZE = 20;
    private static final int TEXT = 0xE0E0E0;
    private static final int MUTED = 0x909090;
    private static final int OK = 0x55FF55;
    private static final int ERROR = 0xFF5555;
    private static final int WARN = 0xFFDD55;

    private record RowLabel(Component text, int baseY) {
    }

    private enum Menu {
        NONE, PRESETS, LAYER
    }

    private final int slot;
    private EditorDraft draft;
    private final DraftSaveState saveState = new DraftSaveState();
    private long submittedGeneration;
    private EditorDraft.Evaluation evaluation;
    private GeneratedShape lastValid;
    private GeneratedShape customShown;
    /** Set by size edits; the next refresh flashes the added and removed cells. */
    private boolean sizeEdited;
    private final PreviewCamera camera = new PreviewCamera();
    private final ShapePreviewRenderer preview = new ShapePreviewRenderer();
    private final PreviewGesture previewGesture = new PreviewGesture();
    private boolean preservePreviewClick;
    private Component previewNotice;
    private long previewNoticeUntil;

    private final SessionHandshake handshake = new SessionHandshake();
    private long revision;
    private boolean awaitingReply;
    private Component status = Component.translatable("gui.excavation3d.status.connecting");
    private Component sessionFailure;
    private int statusColor = MUTED;

    private int layer;
    private boolean anchorMode;
    private CustomMaskDraft.ResizeCheck pendingResize;
    private Boolean paintValue;
    private int paintButton = -1;
    private boolean closeAfterApply;
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
    /** The 3D scene inside the preview, between the caption strip on top and the hint strip below. */
    private int sceneY0;
    private int sceneY1;
    private int captionY;
    private int captionWidth;
    private int hintY;
    private int statusY;
    private int actionY;
    private int gridBaseY = -1;
    private int resizeBaseY = -1;
    private int cellSize;

    private final Map<AbstractWidget, Integer> scrolledWidgets = new LinkedHashMap<>();
    private final List<Button> stepButtons = new ArrayList<>();
    /** Widgets on the preview; drawn after it, so the preview never covers them. */
    private final List<AbstractWidget> previewWidgets = new ArrayList<>();
    private final List<RowLabel> labels = new ArrayList<>();
    private record SizeRow(String field, int y, int valueX) { }
    private final List<SizeRow> sizeRows = new ArrayList<>();
    private String overwriteName;
    private EditBox layerBox;
    private Button layerMenuButton;
    private Button applyButton;
    private Button undoButton;
    private Button redoButton;
    private Button anchorButton;
    private Button resizeConfirmButton;
    private Button resizeCancelButton;

    private Menu openMenu = Menu.NONE;
    private DropdownMenu menu;
    private EditBox presetName;
    private Button presetSave;

    public ShapeEditorScreen(Player player) {
        super(Component.translatable("gui.excavation3d.title"));
        this.slot = player.getInventory().selected;
        ToolShapeData data = player.getMainHandItem().get(ModComponents.TOOL_SHAPE.get());
        this.draft = data != null && data.config().isPresent()
                ? EditorDraft.fromConfig(data.config().get())
                : EditorDraft.defaults();
        this.revision = data == null ? 0L : data.revision();
        if (data != null && data.isDamaged()) {
            status = Component.translatable("gui.excavation3d.status.damaged");
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
        cancelPointer();
        if (!preservePreviewClick) previewGesture.reset();
        scrolledWidgets.clear();
        stepButtons.clear();
        previewWidgets.clear();
        labels.clear();
        sizeRows.clear();
        layerBox = null;
        layerMenuButton = null;
        undoButton = null;
        redoButton = null;
        anchorButton = null;
        resizeConfirmButton = null;
        resizeCancelButton = null;
        // A rebuild (resize, GUI scale, type change, preset load) closes an open menu.
        openMenu = Menu.NONE;
        menu = null;
        presetName = null;
        presetSave = null;
        gridBaseY = -1;
        resizeBaseY = -1;
        layout();
        if (handshake.state() == SessionHandshake.State.IDLE) {
            PacketDistributor.sendToServer(new BeginEditPayload(handshake.start(), slot));
        }

        int y = contentTop;
        typeRow(y);
        y += ROW;
        for (String field : EditorDraft.fields(draft.type())) {
            y = fieldRow(field, y);
        }
        switch (draft.type()) {
            case STAIR, CORRIDOR -> {
                boolean stair = draft.type() == ShapeType.STAIR;
                CycleButton<Boolean> direction = CycleButton.booleanBuilder(
                                Component.translatable(stair ? "gui.excavation3d.ascending" : "gui.excavation3d.upward"),
                                Component.translatable(stair ? "gui.excavation3d.descending" : "gui.excavation3d.downward"))
                        .withInitialValue(stair ? draft.stairAscending() : draft.corridorUpward())
                        .create(leftX, y, leftW, 20, Component.translatable("gui.excavation3d.direction"),
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
                Component orientation = Component.translatable("gui.excavation3d.stair_orientation");
                labels.add(new RowLabel(orientation, y - 4));
                y += font.split(orientation, leftW).size() * font.lineHeight + 3;
            }
            case CUSTOM -> {
                y = customRows(y);
                gridBaseY = y;
            }
            default -> {
            }
        }
        if (draft.type() != ShapeType.CUSTOM && draft.canRedo()) y = historyRow(y);
        contentHeight = y - contentTop;
        layoutGrid();
        scroll = Mth.clamp(scroll, 0, maxScroll());

        initPreviewButtons();
        initHeader();
        initBottom();
        refresh();
    }

    // ---------------------------------------------------------------- layout

    private void layout() {
        EditorLayout layout = EditorLayout.fit(width, height, MARGIN, font.lineHeight);
        leftX = MARGIN;
        leftW = layout.leftWidth();
        actionY = layout.actionY();
        statusY = layout.statusY();
        contentTop = 22;
        contentBottom = layout.contentBottom();
        previewX0 = layout.previewLeft();
        previewX1 = layout.previewRight();
        previewY0 = contentTop;
        previewY1 = Math.max(previewY0 + 20, contentBottom);
    }

    /** One icon per shape type; the custom icon turns a valid template into a custom shape. */
    private void typeRow(int y) {
        ShapeType[] types = ShapeType.values();
        int gap = 2;
        int w = (leftW - gap * (types.length - 1)) / types.length;
        for (int i = 0; i < types.length; i++) {
            ShapeType type = types[i];
            Component label = CommonComponents.optionNameValue(Component.translatable("gui.excavation3d.shape"),
                    ShapeTexts.typeName(type));
            IconButton button = IconButton.pattern(leftX + i * (w + gap), y, w, 20, label, b -> selectType(type),
                    ShapeTypeIcons.pattern(type));
            button.setSelected(type == draft.type());
            scrolled(button, y);
        }
    }

    private int fieldRow(String field, int y) {
        int plusX = leftX + leftW - STEP_WIDTH;
        int boxX = plusX - 2 - FIELD_WIDTH;
        int minusX = boxX - 2 - STEP_WIDTH;
        boolean stacked = font.width(ShapeTexts.fieldName(field)) + 4 > minusX - leftX;
        labels.add(new RowLabel(ShapeTexts.fieldName(field), stacked ? y - 5 : y));
        if (stacked) y += 12;
        stepButton(field, -1, minusX, y);
        sizeRows.add(new SizeRow(field, y, boxX + FIELD_WIDTH / 2));
        stepButton(field, 1, plusX, y);
        return y + ROW + font.lineHeight + 2;
    }

    private void stepButton(String field, int delta, int x, int y) {
        Button button = Button.builder(Component.literal(delta < 0 ? "-" : "+"), b -> step(field, delta))
                .bounds(x, y, STEP_WIDTH, 20).build();
        stepButtons.add(scrolled(button, y));
    }

    private int customRows(int y) {
        CustomMaskDraft custom = draft.custom();
        layer = Mth.clamp(layer, 0, custom.depth() - 1);
        labels.add(new RowLabel(Component.translatable("gui.excavation3d.layer"), y));
        int nextX = leftX + leftW - STEP_WIDTH;
        int boxX = nextX - 2 - FIELD_WIDTH;
        int prevX = boxX - 2 - STEP_WIDTH;
        scrolled(Button.builder(Component.literal("<"), b -> setLayer(layer - 1))
                .bounds(prevX, y, STEP_WIDTH, 20).build(), y);
        layerBox = new EditBox(font, boxX + 1, y + 1, FIELD_WIDTH - 2, 18, Component.translatable("gui.excavation3d.layer"));
        layerBox.setMaxLength(NumericInput.MAX_LENGTH + 1);
        layerBox.setValue(Integer.toString(layer + 1));
        layerBox.setResponder(this::onLayerEdited);
        scrolled(layerBox, y + 1);
        scrolled(Button.builder(Component.literal(">"), b -> setLayer(layer + 1))
                .bounds(nextX, y, STEP_WIDTH, 20).build(), y);
        y += ROW;

        List<Button> tools = buttonRow(y,
                Button.builder(Component.translatable("gui.excavation3d.layer.menu"), b -> toggleLayerMenu()),
                Button.builder(Component.translatable("gui.excavation3d.anchor"), b -> {
                    anchorMode = !anchorMode;
                    refresh();
                }));
        layerMenuButton = tools.get(0);
        anchorButton = tools.get(1);
        y += ROW;

        y = historyRow(y);

        resizeBaseY = y;
        List<Button> resize = buttonRow(y,
                Button.builder(Component.empty(), b -> confirmResize()),
                Button.builder(Component.translatable("gui.excavation3d.resize.keep"), b -> cancelResize()));
        resizeConfirmButton = resize.get(0);
        resizeCancelButton = resize.get(1);
        return y + (pendingResize != null ? ROW : 0) + 2;
    }

    private int historyRow(int y) {
        List<Button> history = buttonRow(y,
                Button.builder(Component.translatable("gui.excavation3d.undo"), b -> history(false)),
                Button.builder(Component.translatable("gui.excavation3d.redo"), b -> history(true)));
        undoButton = history.get(0);
        redoButton = history.get(1);
        return y + ROW;
    }

    private void history(boolean redo) {
        ShapeType before = draft.type();
        if (!(redo ? draft.redo() : draft.undo())) return;
        previewNotice = null;
        if (draft.type() != before) {
            anchorMode = false;
            pendingResize = null;
            scroll = 0;
            saveState.edited();
            rebuildWidgets();
        } else afterCustomStructureChange();
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

    /** Custom-only transforms in the preview corner. */
    private void initPreviewButtons() {
        layoutCaptions(previewX1);
        if (draft.type() != ShapeType.CUSTOM) return;
        int x = previewX1 - 2 - ICON_SIZE;
        int y = previewY0 + 2;
        int buttonsX = x - (ICON_SIZE + 2);
        layoutCaptions(buttonsX);
        previewWidget(IconButton.rotate(x, y, ICON_SIZE, Component.translatable("gui.excavation3d.rotate"), b -> {
            draft.rotateClockwise();
            changed();
        }));
        x -= ICON_SIZE + 2;
        previewWidget(IconButton.mirror(x, y, ICON_SIZE, Component.translatable("gui.excavation3d.mirror"), b -> {
            draft.toggleMirror();
            changed();
        }));
    }

    /** The captions the preview can show; the strip is sized for the longest, so it never jumps. */
    private List<Component> previewCaptions() {
        List<Component> captions = new ArrayList<>(3);
        captions.add(Component.translatable("gui.excavation3d.preview.selection"));
        captions.add(Component.translatable("gui.excavation3d.last_valid"));
        return captions;
    }

    /**
     * Gives the captions their own strips, so neither the icon buttons nor the shape cover them. A
     * caption sits beside the buttons when every caption fits there, otherwise below them, wrapped
     * to the panel width. The camera hint wraps upwards from the bottom edge.
     */
    private void layoutCaptions(int buttonsX) {
        int textX = previewX0 + 4;
        int fullWidth = Math.max(20, previewX1 - 4 - textX);
        int besideWidth = buttonsX - 4 - textX;
        List<Component> captions = previewCaptions();
        int widest = 0;
        for (Component caption : captions) {
            widest = Math.max(widest, font.width(caption));
        }
        boolean beside = widest <= besideWidth;
        captionWidth = beside ? besideWidth : fullWidth;
        captionY = beside ? previewY0 + 6 : previewY0 + 2 + ICON_SIZE + 3;
        int lines = 1;
        for (Component caption : captions) {
            lines = Math.max(lines, font.split(caption, captionWidth).size());
        }
        sceneY0 = Math.max(previewY0 + 2 + ICON_SIZE + 2, captionY + lines * font.lineHeight + 2);
        int hintLines = controlHints().stream().mapToInt(line -> font.split(line, fullWidth).size()).sum();
        hintY = previewY1 - 6 - hintLines * (font.lineHeight + 2);
        sceneY1 = Math.max(sceneY0 + 1, hintY - font.lineHeight - 9);
    }

    private List<Component> controlHints() {
        return List.of(Component.translatable("gui.excavation3d.preview.custom_edit"));
    }

    private void drawWrapped(GuiGraphics graphics, Component text, int y, int maxWidth, int color) {
        for (FormattedCharSequence line : font.split(text, maxWidth)) {
            graphics.drawString(font, line, previewX0 + 4, y, color);
            y += font.lineHeight;
        }
    }

    private void previewWidget(AbstractWidget widget) {
        previewWidgets.add(widget);
        addWidget(widget);
    }

    private void initHeader() {
        Component label = Component.translatable("gui.excavation3d.favorite.save_as");
        int w = Math.max(70, font.width(label) + 16);
        addRenderableWidget(Button.builder(label, b -> togglePresetsMenu())
                .bounds(width - MARGIN - w, 2, w, 18).build());
    }

    private void initBottom() {
        int w = Mth.clamp(width / 4, 60, 100);
        applyButton = addRenderableWidget(Button.builder(Component.translatable("gui.done"),
                b -> apply()).bounds(width - MARGIN - w, actionY, w, 20).build());
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
        gridBaseY = resizeBaseY + (pendingResize != null ? ROW : 0) + font.lineHeight + 10;
        cellSize = Mth.clamp(Math.min(leftW / custom.width(), 220 / custom.height()), 3, 12);
        contentHeight = gridBaseY + cellSize * custom.height() + 4 - contentTop;
    }

    private int gridX() {
        return leftX + (leftW - cellSize * draft.custom().width()) / 2;
    }

    // ---------------------------------------------------------------- menus

    private void closeMenu() {
        if (presetName != null) {
            setFocused(null);
            removeWidget(presetName);
            removeWidget(presetSave);
        }
        presetName = null;
        presetSave = null;
        menu = null;
        openMenu = Menu.NONE;
        overwriteName = null;
    }

    private void togglePresetsMenu() {
        boolean wasOpen = openMenu == Menu.PRESETS;
        closeMenu();
        if (!wasOpen) {
            openPresetsMenu();
        }
    }

    /** Small inline favorite name field, with a second Save confirming overwrite. */
    private void openPresetsMenu() {
        int menuW = Math.min(width - 2 * MARGIN, 180);
        int x = width - MARGIN - menuW;
        int y = contentTop;
        int header = 24;
        menu = new DropdownMenu(x, y, menuW, header + 14, height - 30, List.of(), Component.empty());
        openMenu = Menu.PRESETS;

        Component saveLabel = Component.translatable("gui.excavation3d.preset.save");
        int saveW = Math.max(40, font.width(saveLabel) + 10);
        presetName = new EditBox(font, x + 4, y + 4, menuW - 8 - saveW - 2, 16,
                Component.translatable("gui.excavation3d.preset.name"));
        presetName.setMaxLength(PresetRepository.MAX_NAME_LENGTH);
        presetName.setHint(Component.translatable("gui.excavation3d.preset.name"));
        presetName.setValue(presetCursor);
        // Kept as typed, so a resize or GUI scale change (which rebuilds the screen) never loses it.
        presetName.setResponder(text -> {
            presetCursor = text;
            overwriteName = null;
        });
        presetSave = Button.builder(saveLabel, b -> savePreset())
                .bounds(x + menuW - 4 - saveW, y + 3, saveW, 18).build();
        addWidget(presetName);
        addWidget(presetSave);
        setFocused(presetName);
    }

    private void toggleLayerMenu() {
        boolean wasOpen = openMenu == Menu.LAYER;
        closeMenu();
        if (wasOpen || layerMenuButton == null) {
            return;
        }
        List<DropdownMenu.Entry> entries = List.of(
                DropdownMenu.Entry.of(Component.translatable("gui.excavation3d.layer.copy"), () -> {
                    draft.custom().copyLayer(layer);
                    closeMenu();
                    refresh();
                }),
                new DropdownMenu.Entry(Component.translatable("gui.excavation3d.layer.paste"),
                        () -> draft.custom().hasClipboard(), () -> {
                            closeMenu();
                            customEdited(draft.custom().pasteLayer(layer));
                        }, null),
                DropdownMenu.Entry.of(Component.translatable("gui.excavation3d.layer.fill"), () -> {
                    closeMenu();
                    customEdited(draft.custom().fillLayer(layer, true));
                }),
                DropdownMenu.Entry.of(Component.translatable("gui.excavation3d.layer.clear"), () -> {
                    closeMenu();
                    customEdited(draft.custom().fillLayer(layer, false));
                }));
        int menuW = Math.max(layerMenuButton.getWidth(), 90);
        menu = new DropdownMenu(layerMenuButton.getX(), layerMenuButton.getY() + 20, menuW, 0, height - 4, entries,
                Component.empty());
        openMenu = Menu.LAYER;
    }

    // ---------------------------------------------------------------- state changes

    private ShapeLimits limits() {
        return ClientState.limits();
    }

    private void changed() {
        saveState.edited();
        refresh();
    }

    private void selectType(ShapeType type) {
        if (type == draft.type()) {
            return;
        }
        if (type == ShapeType.CUSTOM) {
            convertToCustom();
        } else {
            changeType(type);
        }
    }

    private void changeType(ShapeType type) {
        draft.setType(type);
        anchorMode = false;
        pendingResize = null;
        scroll = 0;
        saveState.edited();
        rebuildWidgets();
    }

    private void onFieldEdited(String field, String text) {
        draft.setText(field, text);
        saveState.edited();
        sizeEdited = true;
        if (draft.type() == ShapeType.CUSTOM) {
            handleCustomResize();
        }
        refresh();
    }

    private void step(String field, int delta) {
        if (NumericInput.parse(draft.text(field), 1, ShapeLimits.TECHNICAL_MAX_AXIS) instanceof NumericInput.Valid valid) {
            int next = SizeRowStep.next(valid.value(), delta, hasShiftDown(), 1, limits().maxAxis());
            if (next != valid.value()) onFieldEdited(field, Integer.toString(next));
        }
    }

    private void handleCustomResize() {
        pendingResize = null;
        // Resize errors are derived from the current request, never left over from an earlier shrink.
        setStatus(Component.empty(), MUTED);
        Optional<CustomMaskDraft.ResizeCheck> request = draft.requestedCustomResize(limits());
        if (request.isEmpty()) {
            return;
        }
        CustomMaskDraft.ResizeCheck check = request.get();
        if (!check.anchorInside()) {
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
            saveState.edited();
            afterResize();
            sizeEdited = true;
            refresh();
        }
    }

    private void cancelResize() {
        pendingResize = null;
        draft.syncCustomTexts();
        changed();
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
        changed();
    }

    private void customEdited(boolean changedSomething) {
        if (changedSomething) {
            // Painting or moving the anchor can change what a pending shrink would lose.
            handleCustomResize();
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

    /**
     * The custom icon on a template: a valid template becomes the custom grid (one undo step, so
     * Undo brings back the previous custom shape). An invalid template switches to the existing
     * custom draft and says why it was not converted.
     */
    private void convertToCustom() {
        if (draft.convertToCustom(limits())) {
            layer = 0;
            scroll = 0;
            anchorMode = false;
            pendingResize = null;
            saveState.edited();
            rebuildWidgets();
        } else {
            changeType(ShapeType.CUSTOM);
            setStatus(Component.translatable("gui.excavation3d.to_custom.invalid"), ERROR);
        }
    }

    private void setStatus(Component message, int color) {
        status = message;
        statusColor = color;
    }

    private void refresh() {
        GeneratedShape before = lastValid;
        evaluation = draft.evaluate(limits());
        customShown = draft.type() == ShapeType.CUSTOM
                ? new GeneratedShape(draft.transform().apply(draft.custom().toMask())) : null;
        Optional<GeneratedShape> shape = evaluation.shape();
        if (sizeEdited && before != null && shape.isPresent() && !shape.get().equals(before)) {
            preview.highlight(ShapeDiff.between(before, shape.get()), shape.get());
        }
        sizeEdited = false;
        shape.ifPresent(s -> lastValid = s);
        if (undoButton != null) {
            undoButton.active = draft.canUndo();
            redoButton.active = draft.canRedo();
        }
        if (anchorButton != null) {
            anchorButton.setMessage(Component.translatable(anchorMode
                    ? "gui.excavation3d.anchor.picking" : "gui.excavation3d.anchor"));
        }
        if (resizeConfirmButton != null && pendingResize != null) {
            resizeConfirmButton.setMessage(
                    Component.translatable("gui.excavation3d.resize.confirm", pendingResize.lostCells()));
        }
        refreshApply();
        layoutGrid();
        scroll = Mth.clamp(scroll, 0, maxScroll());
        applyScroll();
    }

    private boolean canApply() {
        return applyAvailability() == ApplyAvailability.READY;
    }

    private ApplyAvailability applyAvailability() {
        return ApplyAvailability.of(evaluation, pendingResize != null, awaitingReply, handshake.sessionId() != 0);
    }

    private void refreshApply() {
        if (applyButton == null) return;
        applyButton.active = canApply();
        Component reason = applyProblem();
        applyButton.setTooltip(reason == null ? null : Tooltip.create(reason));
    }

    private Component applyProblem() {
        return switch (applyAvailability()) {
            case READY -> null;
            case INVALID_DRAFT, RESIZE_PENDING -> {
                Component problem = currentProblem();
                yield problem != null ? problem : Component.translatable("gui.excavation3d.to_custom.invalid");
            }
            case WAITING_FOR_RESULT -> Component.translatable("gui.excavation3d.status.applying");
            case SESSION_UNAVAILABLE -> switch (handshake.state()) {
                case NO_REPLY -> Component.translatable("gui.excavation3d.status.no_reply");
                case REFUSED -> sessionFailure != null ? sessionFailure
                        : Component.translatable("message.excavation3d.reject.session");
                default -> handshake.attempts() > 1
                        ? Component.translatable("gui.excavation3d.status.retrying", handshake.attempts(),
                                SessionHandshake.MAX_ATTEMPTS)
                        : Component.translatable("gui.excavation3d.status.connecting");
            };
        };
    }

    // ---------------------------------------------------------------- favorites

    private void savePreset() {
        if (evaluation == null || !evaluation.isValid() || pendingResize != null) {
            setStatus(Component.translatable("gui.excavation3d.preset.unsavable"), ERROR);
            return;
        }
        String name = presetName == null ? presetCursor : presetName.getValue();
        if (PresetRepository.get().find(name).isPresent() && !name.trim().equals(overwriteName)) {
            overwriteName = name.trim();
            return;
        }
        switch (PresetRepository.get().save(name, evaluation.config())) {
            case SAVED -> {
                setStatus(Component.translatable("gui.excavation3d.preset.saved", name.trim()), OK);
                closeMenu();
            }
            case INVALID_NAME -> setStatus(Component.translatable("gui.excavation3d.preset.invalid_name",
                    PresetRepository.MAX_NAME_LENGTH), ERROR);
            case FULL -> setStatus(Component.translatable("gui.excavation3d.preset.full",
                    PresetRepository.MAX_PRESETS), ERROR);
            case IO_ERROR -> setStatus(Component.translatable("gui.excavation3d.preset.io_error"), ERROR);
        }
    }

    // ---------------------------------------------------------------- server protocol

    private void apply() {
        if (!canApply()) {
            return;
        }
        cancelPointer();
        closeAfterApply = true;
        awaitingReply = true;
        submittedGeneration = saveState.submit();
        setStatus(Component.translatable("gui.excavation3d.status.applying"), MUTED);
        PacketDistributor.sendToServer(new ApplyShapePayload(handshake.sessionId(), slot, revision,
                evaluation.config()));
        refresh();
    }

    @Override
    public void tick() {
        super.tick();
        endStalePointer();
        // Only runs while this screen is shown; behind the discard dialog the wait pauses.
        int retry = handshake.tick();
        if (retry != 0) {
            setStatus(Component.translatable("gui.excavation3d.status.retrying", handshake.attempts(),
                    SessionHandshake.MAX_ATTEMPTS), MUTED);
            PacketDistributor.sendToServer(new BeginEditPayload(retry, slot));
        } else if (handshake.state() == SessionHandshake.State.NO_REPLY && statusColor != ERROR) {
            setStatus(Component.translatable("gui.excavation3d.status.no_reply"), ERROR);
        }
        refreshApply();
    }

    public void onSession(EditSessionPayload payload) {
        // Replies to an earlier attempt or to an editor that was closed meanwhile are ignored.
        if (!handshake.onReply(payload.requestId(), payload.ok(), payload.sessionId())) {
            return;
        }
        if (handshake.state() == SessionHandshake.State.OPEN) {
            revision = payload.revision();
            if (statusColor == MUTED || handshake.attempts() > 1) {
                setStatus(Component.empty(), MUTED);
            }
        } else {
            sessionFailure = payload.message();
            setStatus(payload.message(), ERROR);
        }
        refresh();
    }

    public void onApplyResult(ApplyResultPayload payload) {
        if (!awaitingReply || payload.sessionId() != handshake.sessionId()) {
            return;
        }
        awaitingReply = false;
        if (payload.accepted()) com.byflx.excavation3d.client.ToolHud.confirmedChange();

        if (payload.accepted()) {
            revision = payload.revision();
            if (!saveState.accepted(submittedGeneration)) {
                // Edits made while waiting are not part of the confirmed shape: keep them, stay open.
                setStatus(Component.translatable("gui.excavation3d.status.applied_newer_unsaved"), WARN);
            } else {
                setStatus(payload.message(), OK);
                if (closeAfterApply) {
                    closeNow();
                    return;
                }
            }
        } else {
            setStatus(payload.message(), ERROR);
        }
        closeAfterApply = false;
        refresh();
    }

    @Override
    public void onClose() {
        cancelPointer();
        if (!saveState.isDirty()) {
            closeNow();
            return;
        }
        minecraft.setScreen(new ConfirmScreen(discard -> {
            if (discard) {
                closeNow();
            } else {
                minecraft.setScreen(this);
            }
        }, Component.translatable("gui.excavation3d.discard.title"),
                Component.translatable("gui.excavation3d.discard.message")));
    }

    private void closeNow() {
        if (ClientState.editor() == this) {
            ClientState.setEditor(null);
        }
        minecraft.setScreen(null);
    }

    // ---------------------------------------------------------------- input

    private boolean inPreview(double mx, double my) {
        return mx > previewX0 && mx < previewX1 - 1 && my >= sceneY0 && my < sceneY1;
    }

    private boolean buttonHeld(int button) {
        return minecraft.isWindowActive() && button >= 0
                && GLFW.glfwGetMouseButton(minecraft.getWindow().getWindow(), button) == GLFW.GLFW_PRESS;
    }

    private void endStalePointer() {
        boolean previewEnded = previewGesture.endIfReleased(buttonHeld(previewGesture.button()));
        if (!minecraft.isWindowActive() || previewEnded || (paintButton >= 0 && !buttonHeld(paintButton))) {
            cancelPointer();
            previewGesture.reset();
        } else if (isDragging() && !buttonHeld(0)) {
            // Screen's focused-widget drag must not survive a missing release either.
            setDragging(false);
        }
    }

    private void reconcilePointer(double mx, double my) {
        endStalePointer();
        if (previewGesture.button() >= 0 && !inPreview(mx, my)) {
            cancelPointer();
            previewGesture.reset();
        }
    }

    private void cancelPointer() {
        previewGesture.cancel();
        setDragging(false);
        if (paintValue != null) draft.custom().endStroke();
        paintValue = null;
        paintButton = -1;
    }

    @Override
    public void removed() {
        cancelPointer();
        super.removed();
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
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (awaitingReply) return true;
        if (menu != null) {
            // Escape closes the open menu first, not the editor.
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                closeMenu();
                return true;
            }
            if (presetName != null && getFocused() == presetName
                    && (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)) {
                savePreset();
                return true;
            }
        }
        if (menu == null && (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)) {
            if (getFocused() instanceof EditBox box && box.isFocused()) {
                // Enter commits the field only. A subsequent Enter with no field focused applies.
                setFocused(null);
                return true;
            }
            if (canApply()) {
                closeAfterApply = true;
                apply();
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char character, int modifiers) {
        return awaitingReply || super.charTyped(character, modifiers);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (awaitingReply) return true;
        cancelPointer();
        if (menu != null || !inPreview(mx, my)) previewGesture.reset();
        if (menu != null) {
            // The open menu takes every click; a click outside only closes it.
            if (presetName != null && presetName.mouseClicked(mx, my, button)) {
                setFocused(presetName);
                return true;
            }
            if (presetSave != null && presetSave.mouseClicked(mx, my, button)) {
                return true;
            }
            if (button == 0 && menu.click(mx, my)) {
                return true;
            }
            if (!menu.contains(mx, my)) {
                closeMenu();
            }
            return true;
        }
        // Dispatch every +/- press directly, before fields or widget double-click handling.
        if (button == 0) {
            for (Button stepper : stepButtons) {
                if (stepper.active && stepper.visible && stepper.isMouseOver(mx, my)) {
                    setFocused(stepper);
                    stepper.playDownSound(minecraft.getSoundManager());
                    stepper.onPress();
                    return true;
                }
            }
        }
        if (super.mouseClicked(mx, my, button)) {
            return true;
        }
        if ((button == 0 || button == 1) && inPreview(mx, my)) {
            setFocused(null);
            previewGesture.press(button, mx, my, true);
            return true;
        }
        Cell cell = gridCell(mx, my);
        if (cell != null && (button == 0 || button == 1)) {
            setFocused(null);
            if (anchorMode && button == 0) {
                if (draft.custom().get(cell.x(), cell.y(), cell.z())) {
                    anchorMode = false;
                    customEdited(draft.custom().setAnchor(cell.x(), cell.y(), cell.z()));
                    refresh();
                }
                return true;
            }
            paintButton = button;
            paintValue = button == 0;
            draft.custom().beginStroke();
            paint(cell);
            return true;
        }
        return false;
    }

    @Override
    public void mouseMoved(double mx, double my) {
        reconcilePointer(mx, my);
        super.mouseMoved(mx, my);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (awaitingReply) return true;
        reconcilePointer(mx, my);
        if (!buttonHeld(button)) return false;
        PreviewGesture.Motion motion = previewGesture.drag(button, mx, my, dx, dy,
                buttonHeld(previewGesture.button()));
        if (motion.consumed()) {
            if (button == 0) camera.drag(motion.dx(), motion.dy());
            return true;
        }
        if (paintValue != null && button == paintButton) {
            Cell cell = gridCell(mx, my);
            if (cell != null) paint(cell);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (awaitingReply) {
            cancelPointer();
            return super.mouseReleased(mx, my, button);
        }
        PreviewGesture.Release released = previewGesture.release(button, mx, my, inPreview(mx, my), Util.getMillis());
        // Always let Screen clear its widget drag too, including releases outside the panel.
        boolean widgetReleased = super.mouseReleased(mx, my, button);
        if (paintValue != null && button == paintButton) cancelPointer();
        if (released == PreviewGesture.Release.RESET) {
            camera.reset();
            preview.resetScale();
        } else if (released == PreviewGesture.Release.CLICK) {
            editPreview(mx, my, button);
        }
        return released != PreviewGesture.Release.NONE || widgetReleased;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (awaitingReply) return true;
        if (menu != null) {
            if (menu.contains(mx, my)) {
                menu.scroll(scrollY);
            } else {
                closeMenu();
            }
            return true;
        }
        if (inPreview(mx, my)) {
            camera.scroll(scrollY);
            return true;
        }
        if (mx >= leftX && mx < leftX + leftW && my >= contentTop && my < contentBottom) {
            for (SizeRow row : sizeRows) {
                int y = row.y() - scroll;
                if (my >= y && my < y + ROW + font.lineHeight + 2) {
                    if (scrollY != 0) step(row.field(), (int) Math.signum(scrollY));
                    return true;
                }
            }
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
        customEdited(draft.custom().set(cell.x(), cell.y(), cell.z(), paintValue));
    }

    private GeneratedShape customPreview() {
        return customShown;
    }

    private void editPreview(double mx, double my, int button) {
        if (draft.type() != ShapeType.CUSTOM && (evaluation == null || !evaluation.isValid())) {
            previewNotice("gui.excavation3d.to_custom.invalid");
            return;
        }
        preview.pick(mx, my, draft.type() == ShapeType.CUSTOM ? layer : 0).ifPresent(hit -> {
            if (draft.type() != ShapeType.CUSTOM) {
                switch (draft.editTemplatePreview(hit, button == 0, limits())) {
                    case INVALID -> previewNotice("gui.excavation3d.to_custom.invalid");
                    case LIMIT -> previewNotice("gui.excavation3d.quick.unavailable");
                    case CHANGED -> {
                        anchorMode = false;
                        pendingResize = null;
                        layer = 0;
                        scroll = 0;
                        saveState.edited();
                        // Keep the first release available to the existing double-click reset gesture.
                        preservePreviewClick = true;
                        try { rebuildWidgets(); }
                        finally { preservePreviewClick = false; }
                        previewNotice("gui.excavation3d.to_custom.switched");
                    }
                    case UNCHANGED -> { }
                }
                return;
            }
            Cell target = anchorMode || button == 1 ? (hit.selected() ? hit.cell() : null)
                    : PreviewPicking.addTarget(hit, customPreview().mask()).orElse(null);
            if (target == null) return;
            CustomMaskDraft custom = draft.custom();
            Cell cell = CustomCanvas.toDraft(target, custom.width(), custom.height(), draft.transform());
            if (anchorMode && button == 0) {
                anchorMode = false;
                customEdited(custom.setAnchor(cell.x(), cell.y(), cell.z()));
                refresh();
            } else if (!anchorMode) customEdited(custom.set(cell.x(), cell.y(), cell.z(), button == 0));
        });
    }

    private void previewNotice(String key) {
        previewNotice = Component.translatable(key);
        previewNoticeUntil = Util.getMillis() + 2000;
    }

    // ---------------------------------------------------------------- rendering

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.flush();
        com.mojang.blaze3d.systems.RenderSystem.clear(org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT,
                net.minecraft.client.Minecraft.ON_OSX);
        graphics.fill(0, 0, width, height, 0xFF171A20);
        // Widgets under an open menu get no hover.
        boolean overMenu = menu != null && menu.contains(mouseX, mouseY);
        int mx = overMenu ? -1 : mouseX;
        int my = overMenu ? -1 : mouseY;
        super.render(graphics, mx, my, partialTick);
        graphics.drawString(font, title, leftX, 6, 0xFFFFFF);

        graphics.enableScissor(leftX, contentTop, leftX + leftW, contentBottom);
        for (RowLabel label : labels) {
            int y = label.baseY() - scroll;
            if (y + 20 > contentTop && y < contentBottom) {
                for (FormattedCharSequence line : font.split(label.text(), leftW)) {
                    graphics.drawString(font, line, leftX, y + 6, TEXT);
                    y += font.lineHeight;
                }
            }
        }
        renderSizeRows(graphics);
        renderGrid(graphics, mx, my);
        graphics.disableScissor();
        if (maxScroll() > 0) {
            int track = contentBottom - contentTop;
            int thumb = Math.max(10, track * track / (track + maxScroll()));
            int thumbY = contentTop + (track - thumb) * scroll / maxScroll();
            graphics.fill(leftX + leftW + 2, contentTop, leftX + leftW + 3, contentBottom, 0x183F4B59);
            graphics.fill(leftX + leftW + 2, thumbY, leftX + leftW + 3, thumbY + thumb, 0x886B7888);
        }

        renderPreview(graphics, mx, my, partialTick);
        renderSummary(graphics);
        if (menu != null) {
            graphics.pose().pushPose();
            graphics.pose().translate(0.0F, 0.0F, 300.0F);
            menu.render(graphics, font, mouseX, mouseY);
            if (presetName != null) {
                presetName.render(graphics, mouseX, mouseY, partialTick);
                presetSave.render(graphics, mouseX, mouseY, partialTick);
                if (overwriteName != null) {
                    Component confirm = Component.translatable("gui.excavation3d.favorite.overwrite", overwriteName);
                    float scale = Math.min(1.0F, (float) (menu.width() - 8) / Math.max(1, font.width(confirm)));
                    graphics.pose().pushPose();
                    graphics.pose().translate(menu.x() + 4, menu.y() + 26, 0);
                    graphics.pose().scale(scale, scale, 1);
                    graphics.drawString(font, confirm, 0, 0, WARN);
                    graphics.pose().popPose();
                }
            }
            graphics.pose().popPose();
        }
    }

    private void renderSizeRows(GuiGraphics graphics) {
        for (SizeRow row : sizeRows) {
            int y = row.y() - scroll;
            Component error = rowProblem(row.field());
            graphics.pose().pushPose();
            graphics.pose().translate(row.valueX(), y + 4, 0);
            graphics.pose().scale(1.4F, 1.4F, 1);
            graphics.drawCenteredString(font, draft.text(row.field()), 0, 0, error == null ? TEXT : ERROR);
            graphics.pose().popPose();
            if (error != null) {
                graphics.drawString(font, firstLine(error, leftW), leftX, y + ROW, ERROR);
            }
        }
    }

    private Component rowProblem(String field) {
        if (evaluation == null) return null;
        NumericInput.Problem problem = evaluation.fieldErrors().get(field);
        if (problem != null) return ShapeTexts.problem(field, problem, 1, limits().maxAxis());
        if (evaluation.result() instanceof ShapeGenerator.Invalid invalid) {
            return invalid.issues().stream().filter(issue -> issue.field().equals(field)
                    || ((draft.type() == ShapeType.STAIR || draft.type() == ShapeType.CORRIDOR)
                            && (issue.field().equals("height") && (field.equals(EditorDraft.STEPS)
                                    || field.equals(EditorDraft.STEP_HEIGHT) || field.equals(EditorDraft.CLEARANCE))
                                || issue.field().equals("depth") && (field.equals(EditorDraft.STEPS)
                                    || field.equals(EditorDraft.STEP_DEPTH))))
                    || (field.equals(EditorDraft.fields(draft.type()).getFirst())
                            && !EditorDraft.fields(draft.type()).contains(issue.field())))
                    .findFirst().map(ShapeTexts::issue).orElse(null);
        }
        if (draft.type() == ShapeType.CUSTOM && evaluation.resizePending()) {
            int actual = switch (field) {
                case EditorDraft.WIDTH -> draft.custom().width();
                case EditorDraft.HEIGHT -> draft.custom().height();
                default -> draft.custom().depth();
            };
            if (!Integer.toString(actual).equals(draft.text(field))) return currentProblem();
        }
        return null;
    }

    private void renderGrid(GuiGraphics graphics, int mouseX, int mouseY) {
        if (gridBaseY < 0) {
            return;
        }
        CustomMaskDraft custom = draft.custom();
        int gx = gridX();
        int gy = gridBaseY - scroll;
        graphics.drawCenteredString(font, Component.translatable("gui.excavation3d.layer.grid", layer + 1),
                leftX + leftW / 2, gy - font.lineHeight - 4, MUTED);
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

    private void renderPreview(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(previewX0, previewY0, previewX1, previewY1, 0xFF0E1116);
        graphics.renderOutline(previewX0, previewY0, previewX1 - previewX0, previewY1 - previewY0, 0xFF3A3F47);
        GeneratedShape current = evaluation == null ? null : evaluation.shape().orElse(null);
        GeneratedShape shown = current != null ? current : lastValid;
        boolean custom = draft.type() == ShapeType.CUSTOM;
        if (custom) shown = customPreview();
        if (shown != null) {
            // Only the scene between the caption and hint strips, so the shape and its labels never cover them.
            preview.render(graphics, font, previewX0 + 1, sceneY0, previewX1 - 1, sceneY1, shown, camera,
                    false, custom, custom ? layer : 0, mouseX, mouseY, anchorMode,
                    custom || (evaluation != null && evaluation.isValid()));
        }
        graphics.enableScissor(previewX0 + 1, previewY0 + 1, previewX1 - 1, previewY1 - 1);
        if (!custom && current == null && lastValid != null) {
            drawWrapped(graphics, Component.translatable("gui.excavation3d.last_valid"), captionY, captionWidth,
                    WARN);
        } else {
            drawWrapped(graphics, Component.translatable("gui.excavation3d.preview.selection"), captionY,
                    captionWidth, MUTED);
        }
        if (shown != null) {
            Component summary = Component.translatable("gui.excavation3d.preview.summary",
                    shown.width(), shown.height(), shown.depth(), shown.count());
            float scale = Math.min(1.0F, (float) (previewX1 - previewX0 - 8) / Math.max(1, font.width(summary)));
            graphics.pose().pushPose();
            graphics.pose().translate(previewX0 + 4, sceneY1 + 4, 0);
            graphics.pose().scale(scale, scale, 1);
            graphics.drawString(font, summary, 0, 0, current == null ? MUTED : TEXT);
            graphics.pose().popPose();
        }
        int helpY = hintY;
        for (Component hint : controlHints()) {
            for (FormattedCharSequence line : font.split(hint, Math.max(20, previewX1 - previewX0 - 8))) {
                graphics.drawString(font, line, previewX0 + 4, helpY, MUTED);
                helpY += font.lineHeight + 2;
            }
        }
        graphics.disableScissor();
        for (AbstractWidget widget : previewWidgets) {
            widget.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    private void renderSummary(GuiGraphics graphics) {
        graphics.drawString(font, Component.translatable("gui.excavation3d.escape_hint"), leftX, actionY + 6, MUTED);
        Component problem = applyProblem();
        Component message = problem != null ? problem : status;
        int color = currentProblem() != null ? ERROR : statusColor;
        if (previewNotice != null && Util.getMillis() < previewNoticeUntil) {
            message = previewNotice;
            color = TEXT;
        }
        List<FormattedCharSequence> lines = font.split(message, width - 2 * MARGIN);
        for (int i = 0; i < Math.min(2, lines.size()); i++) {
            graphics.drawString(font, lines.get(i), leftX, statusY + i * font.lineHeight, color);
        }
        if (!applyButton.active) {
            graphics.fill(applyButton.getX(), actionY, applyButton.getX() + applyButton.getWidth(), actionY + 20,
                    0xFF20252D);
            graphics.renderOutline(applyButton.getX(), actionY, applyButton.getWidth(), 20, 0xFF48515E);
            graphics.drawCenteredString(font, applyButton.getMessage(),
                    applyButton.getX() + applyButton.getWidth() / 2, actionY + 6, 0xFF737E8D);
        }
    }

    private FormattedCharSequence firstLine(Component text, int maxWidth) {
        List<FormattedCharSequence> lines = font.split(text, maxWidth);
        return lines.isEmpty() ? FormattedCharSequence.EMPTY : lines.get(0);
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
            return Component.translatable("gui.excavation3d.resize.pending", pendingResize.lostCells());
        }
        if (evaluation.resizePending()) {
            Optional<CustomMaskDraft.ResizeCheck> request = draft.requestedCustomResize(limits());
            if (request.isPresent() && !request.get().anchorInside()) {
                return Component.translatable("gui.excavation3d.resize.anchor_outside");
            }
            return Component.translatable("gui.excavation3d.resize.size_mismatch");
        }
        if (evaluation.result() instanceof ShapeGenerator.Invalid invalid) {
            return ShapeTexts.issue(invalid.issues().get(0));
        }
        return null;
    }
}

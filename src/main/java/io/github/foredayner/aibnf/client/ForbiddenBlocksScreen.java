package io.github.foredayner.aibnf.client;

import io.github.foredayner.aibnf.config.ForbiddenBlockRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class ForbiddenBlocksScreen extends Screen {

    private static final int PANEL_WIDTH = 360;
    private static final int PANEL_HEIGHT = 320;
    private static final int VISIBLE_ROWS = 6;
    private static final int ROW_HEIGHT = 24;

    private static final int LABEL_COLOR = 0xFFCCCCCC;
    private static final int ROW_COLOR = 0xFF9AA4B2;
    private static final int EMPTY_COLOR = 0xFF888888;
    private static final int SECTION_COLOR = 0xFF88AAFF;

    private final Screen parent;
    private final Consumer<List<String>> onDone;
    private final List<String> blocks;

    private int panelX;
    private int panelY;
    private int scrollOffset;
    private Component statusMessage = Component.empty();

    public ForbiddenBlocksScreen(Screen parent, List<String> initialBlocks, Consumer<List<String>> onDone) {
        super(Component.literal("Forbidden Blocks"));
        this.parent = parent;
        this.onDone = onDone;
        this.blocks = new ArrayList<>(ForbiddenBlockRegistry.normalizeList(initialBlocks));
    }

    @Override
    protected void init() {
        super.init();
        layoutPanel();
        rebuildList();
    }

    private void layoutPanel() {
        this.panelX = (this.width - PANEL_WIDTH) / 2;
        this.panelY = (this.height - PANEL_HEIGHT) / 2;
    }

    private void rebuildList() {
        this.clearWidgets();

        int x = this.panelX + 16;
        int fieldWidth = PANEL_WIDTH - 32;
        int listTop = this.panelY + 48;
        int listHeight = VISIBLE_ROWS * ROW_HEIGHT;

        clampScrollOffset();
        for (int row = 0; row < VISIBLE_ROWS; row++) {
            int index = this.scrollOffset + row;
            if (index >= this.blocks.size()) {
                break;
            }

            int rowY = listTop + row * ROW_HEIGHT;
            int removeX = this.panelX + PANEL_WIDTH - 42;
            int finalIndex = index;
            this.addRenderableWidget(Button.builder(Component.literal("X"), button -> removeBlock(finalIndex))
                .bounds(removeX, rowY + 2, 20, 20)
                .build());
        }

        int scrollX = this.panelX + PANEL_WIDTH - 18;
        this.addRenderableWidget(Button.builder(Component.literal("^"), button -> scroll(-1))
            .bounds(scrollX, listTop, 12, 16)
            .build());
        this.addRenderableWidget(Button.builder(Component.literal("v"), button -> scroll(1))
            .bounds(scrollX, listTop + listHeight - 16, 12, 16)
            .build());

        this.addRenderableWidget(Button.builder(Component.literal("Add from list..."), button -> openBlockPicker())
            .bounds(x, this.panelY + 210, fieldWidth, 20)
            .build());

        this.addRenderableWidget(Button.builder(Component.literal("Defaults"), button -> resetDefaults())
            .bounds(x, this.panelY + 238, 90, 20)
            .build());

        int buttonWidth = (fieldWidth - 8) / 2;
        this.addRenderableWidget(Button.builder(Component.literal("Done"), button -> finish())
            .bounds(x + fieldWidth - buttonWidth, this.panelY + PANEL_HEIGHT - 28, buttonWidth, 20)
            .build());

        this.addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> cancel())
            .bounds(x + fieldWidth - buttonWidth * 2 - 8, this.panelY + PANEL_HEIGHT - 28, buttonWidth, 20)
            .build());
    }

    private void openBlockPicker() {
        if (this.minecraft == null) {
            return;
        }

        this.minecraft.setScreen(new BlockPickerScreen(this, this::addBlockById));
    }

    private void addBlockById(String blockId) {
        String normalized = ForbiddenBlockRegistry.normalizeBlockId(blockId);
        if (normalized == null) {
            this.statusMessage = Component.literal("Invalid block");
            return;
        }

        if (this.blocks.contains(normalized)) {
            this.statusMessage = Component.literal("Already forbidden");
            return;
        }

        this.blocks.add(normalized);
        this.blocks.sort(String::compareTo);
        this.statusMessage = Component.literal("Added " + BlockIconHelper.shortName(normalized));
        rebuildList();
    }

    private void removeBlock(int index) {
        if (index < 0 || index >= this.blocks.size()) {
            return;
        }

        String removed = this.blocks.remove(index);
        this.statusMessage = Component.literal("Removed " + BlockIconHelper.shortName(removed));
        rebuildList();
    }

    private void resetDefaults() {
        this.blocks.clear();
        this.blocks.addAll(ForbiddenBlockRegistry.defaultBlocks());
        this.statusMessage = Component.literal("Reset to defaults");
        this.scrollOffset = 0;
        rebuildList();
    }

    private void scroll(int delta) {
        this.scrollOffset += delta;
        rebuildList();
    }

    private void clampScrollOffset() {
        int maxOffset = Math.max(0, this.blocks.size() - VISIBLE_ROWS);
        this.scrollOffset = net.minecraft.util.Mth.clamp(this.scrollOffset, 0, maxOffset);
    }

    private void finish() {
        this.onDone.accept(ForbiddenBlockRegistry.normalizeList(this.blocks));
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    private void cancel() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    @Override
    public void render(GuiGraphics drawContext, int mouseX, int mouseY, float delta) {
        layoutPanel();

        drawContext.fill(0, 0, this.width, this.height, 0x80000000);
        drawContext.fill(this.panelX, this.panelY, this.panelX + PANEL_WIDTH, this.panelY + PANEL_HEIGHT, 0xF0202027);
        drawContext.fill(this.panelX, this.panelY, this.panelX + PANEL_WIDTH, this.panelY + 28, 0xFF2E3744);
        drawContext.drawCenteredString(this.font, this.title, this.panelX + PANEL_WIDTH / 2, this.panelY + 9, 0xFFFFFFFF);

        drawContext.drawString(this.font, Component.literal("Blocked blocks (" + this.blocks.size() + ")"), this.panelX + 16, this.panelY + 32, SECTION_COLOR, false);
        drawContext.drawString(this.font, Component.literal("Pick icons from the block list"), this.panelX + 16, this.panelY + 196, LABEL_COLOR, false);

        int listTop = this.panelY + 48;
        int listBottom = listTop + VISIBLE_ROWS * ROW_HEIGHT;
        drawContext.fill(this.panelX + 16, listTop, this.panelX + PANEL_WIDTH - 16, listBottom, 0xCC000000);

        if (this.blocks.isEmpty()) {
            drawContext.drawString(this.font, Component.literal("No forbidden blocks"), this.panelX + 24, listTop + 8, EMPTY_COLOR, false);
        } else {
            for (int row = 0; row < VISIBLE_ROWS; row++) {
                int index = this.scrollOffset + row;
                if (index >= this.blocks.size()) {
                    break;
                }

                String blockId = this.blocks.get(index);
                int rowY = listTop + row * ROW_HEIGHT;
                BlockIconHelper.drawBlockIcon(drawContext, blockId, this.panelX + 22, rowY + 4);
                drawContext.drawString(
                    this.font,
                    Component.literal(BlockIconHelper.shortName(blockId)),
                    this.panelX + 44,
                    rowY + 8,
                    ROW_COLOR,
                    false
                );
            }
        }

        if (!this.statusMessage.getString().isEmpty()) {
            drawContext.drawString(this.font, this.statusMessage, this.panelX + 16, this.panelY + 264, 0xFFAAAAAA, false);
        }

        super.render(drawContext, mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

}

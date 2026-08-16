package cn.xiaoshuaixia.sparseverticalexpansion.client;

import cn.xiaoshuaixia.sparseverticalexpansion.network.CreateRegionLayerPayload;
import cn.xiaoshuaixia.sparseverticalexpansion.network.DeleteRegionLayerPayload;
import cn.xiaoshuaixia.sparseverticalexpansion.network.OpenRegionEditorPayload;
import cn.xiaoshuaixia.sparseverticalexpansion.world.ExtendedYRange;
import cn.xiaoshuaixia.sparseverticalexpansion.world.SimulationRules;
import cn.xiaoshuaixia.sparseverticalexpansion.world.VerticalLayer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

public final class VerticalRegionScreen extends Screen {
    private final OpenRegionEditorPayload request;
    private EditBox minYBox;
    private EditBox maxYBox;
    private Checkbox rendering;
    private Checkbox collision;
    private Checkbox entityInteraction;
    private Button create;
    private VerticalLayer selectedLayer;
    private int minY;
    private int maxY;
    private boolean adjusted;

    public VerticalRegionScreen(OpenRegionEditorPayload request) {
        super(Component.translatable("screen.sve.region.title"));
        this.request = request;
        minY = request.suggestedMinY();
        maxY = request.suggestedMaxY();
    }

    @Override
    protected void init() {
        int formX = width / 2 - 70;
        minYBox = addRenderableWidget(new EditBox(font, formX, 50, 140, 20, Component.translatable("screen.sve.region.min_y")));
        maxYBox = addRenderableWidget(new EditBox(font, formX, 88, 140, 20, Component.translatable("screen.sve.region.max_y")));
        minYBox.setValue(Integer.toString(minY));
        maxYBox.setValue(Integer.toString(maxY));
        minYBox.setFilter(VerticalRegionScreen::integerText);
        maxYBox.setFilter(VerticalRegionScreen::integerText);
        minYBox.setResponder(ignored -> inputChanged());
        maxYBox.setResponder(ignored -> inputChanged());

        int rulesX = width / 2 - 70;
        rendering = addRenderableWidget(Checkbox.builder(Component.translatable("screen.sve.rule.rendering"), font)
                .pos(rulesX, 125).selected(true).onValueChange((box, value) -> validate()).build());
        collision = addRenderableWidget(Checkbox.builder(Component.translatable("screen.sve.rule.collision"), font)
                .pos(rulesX, 149).selected(true).onValueChange((box, value) -> validate()).build());
        entityInteraction = addRenderableWidget(Checkbox.builder(Component.translatable("screen.sve.rule.entity_interaction"), font)
                .pos(rulesX, 173).selected(true).onValueChange((box, value) -> validate()).build());
        entityInteraction.active = false;
        create = addRenderableWidget(Button.builder(Component.translatable("screen.sve.region.create"), button -> confirm())
                .bounds(width / 2 - 105, height - 32, 100, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose())
                .bounds(width / 2 + 5, height - 32, 100, 20).build());
        validate();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        VerticalLayer clickedLayer = layerAt(mouseX, mouseY);
        if (clickedLayer != null && (button == 0 || button == 1)) {
            selectLayer(clickedLayer);
            if (button == 1) {
                confirmDelete(clickedLayer);
            }
            return true;
        }
        boolean minEditing = minYBox.isFocused();
        boolean maxEditing = maxYBox.isFocused();
        boolean handled = super.mouseClicked(mouseX, mouseY, button);
        if (minEditing && !minYBox.isFocused() || maxEditing && !maxYBox.isFocused()) {
            alignInputs();
        }
        return handled;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean minEditing = minYBox.isFocused();
        boolean maxEditing = maxYBox.isFocused();
        boolean handled = super.keyPressed(keyCode, scanCode, modifiers);
        if ((keyCode == 258 || keyCode == 257)
                && (minEditing && !minYBox.isFocused() || maxEditing && !maxYBox.isFocused())) {
            alignInputs();
        }
        return handled;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 16, 0xFFFFFF);
        graphics.drawString(font, Component.translatable("screen.sve.region.chunk", request.chunkX(), request.chunkZ()), 20, 30, 0xA0A0A0);
        graphics.drawString(font, Component.translatable("screen.sve.region.min_y"), width / 2 - 70, 39, 0xFFFFFF);
        graphics.drawString(font, Component.translatable("screen.sve.region.max_y"), width / 2 - 70, 77, 0xFFFFFF);
        graphics.drawString(font, Component.translatable("screen.sve.region.rules"), width / 2 - 70, 113, 0xFFFFFF);
        renderVerticalBar(graphics);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(null);
    }

    private void alignInputs() {
        try {
            int enteredMin = Integer.parseInt(minYBox.getValue());
            int enteredMax = Integer.parseInt(maxYBox.getValue());
            ExtendedYRange range = ExtendedYRange.aligned(enteredMin, enteredMax);
            adjusted |= range.minY() != enteredMin || range.maxY() != enteredMax;
            minY = range.minY();
            maxY = range.maxY();
            minYBox.setValue(Integer.toString(minY));
            maxYBox.setValue(Integer.toString(maxY));
        } catch (IllegalArgumentException ignored) {
        }
        validate();
    }

    private void validate() {
        try {
            ExtendedYRange range = ExtendedYRange.aligned(
                    Integer.parseInt(minYBox.getValue()), Integer.parseInt(maxYBox.getValue()));
            create.active = range.maxY() <= request.maximumY();
            minY = range.minY();
            maxY = range.maxY();
        } catch (IllegalArgumentException exception) {
            create.active = false;
        }
    }

    private void inputChanged() {
        selectedLayer = null;
        validate();
    }

    private void confirm() {
        alignInputs();
        if (!create.active) {
            return;
        }
        List<Component> enabled = new ArrayList<>(3);
        if (rendering.selected()) enabled.add(Component.translatable("screen.sve.rule.rendering"));
        if (collision.selected()) enabled.add(Component.translatable("screen.sve.rule.collision"));
        if (entityInteraction.selected()) enabled.add(Component.translatable("screen.sve.rule.entity_interaction"));
        String rules = enabled.isEmpty()
                ? Component.translatable("screen.sve.rule.none").getString()
                : String.join("、", enabled.stream().map(Component::getString).toList());
        Component message = Component.translatable(
                "screen.sve.region.confirm_message",
                minY,
                maxY,
                adjusted ? Component.translatable("screen.sve.region.aligned").getString() : "",
                rules);
        minecraft.setScreen(new ConfirmScreen(confirmed -> {
            if (confirmed) {
                PacketDistributor.sendToServer(new CreateRegionLayerPayload(
                        request.chunkX(), request.chunkZ(), minY, maxY, rulesMask(), request.revision()));
                minecraft.setScreen(null);
            } else {
                minecraft.setScreen(this);
            }
        }, Component.translatable("screen.sve.region.confirm_title"), message));
    }

    private int rulesMask() {
        return (rendering.selected() ? SimulationRules.RENDERING : 0)
                | (collision.selected() ? SimulationRules.COLLISION : 0)
                | (entityInteraction.selected() ? SimulationRules.ENTITY_INTERACTION : 0);
    }

    private void confirmDelete(VerticalLayer layer) {
        minecraft.setScreen(new ConfirmScreen(confirmed -> {
            if (confirmed) {
                PacketDistributor.sendToServer(new DeleteRegionLayerPayload(
                        request.chunkX(), request.chunkZ(),
                        layer.range().minY(), layer.range().maxY(), request.revision()));
                minecraft.setScreen(null);
            } else {
                minecraft.setScreen(this);
            }
        }, Component.translatable("screen.sve.region.delete_title"), Component.translatable(
                "screen.sve.region.delete_message", layer.range().minY(), layer.range().maxY())));
    }

    private void renderVerticalBar(GuiGraphics graphics) {
        int left = barLeft();
        int top = barTop();
        int bottom = barBottom();
        graphics.fill(left, top, left + 18, bottom, 0xFF202020);
        double low = scale(displayMinY());
        double high = scale(displayMaxY());
        for (VerticalLayer layer : request.layers()) {
            int layerTop = mapped(layer.range().maxY(), low, high, top, bottom);
            int layerBottom = Math.max(layerTop + 1, mapped(layer.range().minY(), low, high, top, bottom));
            graphics.fill(left + 2, layerTop, left + 16, layerBottom, ruleColor(layer.rules().mask()));
            if (layer.equals(selectedLayer)) {
                graphics.fill(left, layerTop - 1, left + 18, layerTop, 0xFFFFFFFF);
                graphics.fill(left, layerBottom, left + 18, layerBottom + 1, 0xFFFFFFFF);
                graphics.fill(left, layerTop, left + 1, layerBottom, 0xFFFFFFFF);
                graphics.fill(left + 17, layerTop, left + 18, layerBottom, 0xFFFFFFFF);
            }
        }
        int regionTop = mapped(maxY, low, high, top, bottom);
        int regionBottom = mapped(minY, low, high, top, bottom);
        if (selectedLayer == null) {
            graphics.fill(left + 3, regionTop, left + 15, Math.max(regionTop + 1, regionBottom), ruleColor(rulesMask()));
        }
        int vanillaTop = mapped(ExtendedYRange.VANILLA_MAX_Y, low, high, top, bottom);
        int vanillaBottom = mapped(ExtendedYRange.VANILLA_MIN_Y, low, high, top, bottom);
        graphics.fill(left + 2, vanillaTop, left + 16, Math.max(vanillaTop + 1, vanillaBottom), 0xFF777777);
        graphics.drawString(font, Integer.toString(maxY), left + 24, Math.max(top, regionTop - 4), 0xFFFFFF);
        graphics.drawString(font, Integer.toString(minY), left + 24, Math.min(bottom - 8, regionBottom - 4), 0xFFFFFF);
    }

    private VerticalLayer layerAt(double mouseX, double mouseY) {
        if (mouseX < barLeft() || mouseX >= barLeft() + 18 || mouseY < barTop() || mouseY >= barBottom()) {
            return null;
        }
        double low = scale(displayMinY());
        double high = scale(displayMaxY());
        for (VerticalLayer layer : request.layers()) {
            int top = mapped(layer.range().maxY(), low, high, barTop(), barBottom());
            int bottom = Math.max(top + 1, mapped(layer.range().minY(), low, high, barTop(), barBottom()));
            if (mouseY >= top && mouseY < bottom) {
                return layer;
            }
        }
        return null;
    }

    private void selectLayer(VerticalLayer layer) {
        minY = layer.range().minY();
        maxY = layer.range().maxY();
        adjusted = false;
        minYBox.setValue(Integer.toString(minY));
        maxYBox.setValue(Integer.toString(maxY));
        selectedLayer = layer;
    }

    private int displayMinY() {
        return request.layers().stream()
                .mapToInt(layer -> layer.range().minY())
                .reduce(Math.min(minY, ExtendedYRange.VANILLA_MIN_Y), Math::min);
    }

    private int displayMaxY() {
        return request.layers().stream()
                .mapToInt(layer -> layer.range().maxY())
                .reduce(Math.max(maxY, ExtendedYRange.VANILLA_MAX_Y), Math::max);
    }

    private static int barLeft() {
        return 24;
    }

    private static int barTop() {
        return 48;
    }

    private int barBottom() {
        return height - 48;
    }

    private static int mapped(int y, double low, double high, int top, int bottom) {
        return Mth.clamp(bottom - (int) Math.round((scale(y) - low) / (high - low) * (bottom - top)), top, bottom);
    }

    private static double scale(int y) {
        return Math.log(y + Math.sqrt((double) y * y + 102400.0));
    }

    private static int ruleColor(int mask) {
        return 0xFF000000 | (0x5F3759DF ^ Integer.rotateLeft(mask * 0x45D9F3B, 7)) & 0x00FFFFFF;
    }

    private static boolean integerText(String text) {
        return text.isEmpty() || text.equals("-") || text.matches("-?\\d{0,8}");
    }
}

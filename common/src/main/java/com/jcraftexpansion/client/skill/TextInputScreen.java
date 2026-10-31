package com.jcraftexpansion.client.skill;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/** Tela simples com uma caixa de texto, usada por skills que pedem um nome (ex.: o disfarce do White Snake). */
public class TextInputScreen extends Screen {
    private final Component hint;
    private final Consumer<String> onSubmit;
    private EditBox input;

    public TextInputScreen(Component title, Component hint, Consumer<String> onSubmit) {
        super(title);
        this.hint = hint;
        this.onSubmit = onSubmit;
    }

    @Override
    protected void init() {
        int w = 220;
        int x = this.width / 2 - w / 2;
        int y = this.height / 2 - 10;

        input = new EditBox(this.font, x, y, w, 20, this.title);
        input.setMaxLength(48);
        this.addRenderableWidget(input);
        this.setInitialFocus(input);

        this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> submit())
                .bounds(x, y + 28, w / 2 - 2, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> this.onClose())
                .bounds(x + w / 2 + 2, y + 28, w / 2 - 2, 20).build());
    }

    private void submit() {
        String text = input.getValue();
        this.onClose();
        onSubmit.accept(text);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 || keyCode == 335) { // Enter / Enter do teclado numérico
            submit();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, this.height / 2 - 42, 0xFFFFFF);
        graphics.drawCenteredString(this.font, this.hint, this.width / 2, this.height / 2 - 28, 0xAAAAAA);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

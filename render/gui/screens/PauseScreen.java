package com.mineshaft.render.gui.screens;

import com.mineshaft.render.SimpleFont;
import com.mineshaft.render.gui.GameState;
import com.mineshaft.render.gui.MenuManager;
import com.mineshaft.render.gui.Screen;

import static org.lwjgl.opengl.GL11.*;

public class PauseScreen extends Screen {

    public PauseScreen(long window, SimpleFont font, MenuManager menuManager) {
        super(window, font, menuManager);
        this.title = "Game Paused";
    }

    @Override
    public void onShow() {
        // No init time needed anymore
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button) {
        // ✅ FIX: Remove delay so buttons work immediately
        super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void init() {
        int buttonWidth = 200;
        int buttonHeight = 40;
        int centerX = (screenWidth - buttonWidth) / 2;
        int centerY = screenHeight / 2 - 40; // Center screen
        int spacing = 24;

        addButton(centerX, centerY, buttonWidth, buttonHeight, "Back to Game", () -> {
            menuManager.setGameState(GameState.PLAYING);
        });

        addButton(centerX, centerY + buttonHeight + spacing, buttonWidth, buttonHeight, "Save and Quit to Title",
                () -> {
                    menuManager.setGameState(GameState.MAIN_MENU);
                });
    }

    @Override
    public void render(double mouseX, double mouseY) {
        // Darkened background
        glDisable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        glColor4f(0.0f, 0.0f, 0.0f, 0.6f);
        glBegin(GL_QUADS);
        glVertex2f(0, 0);
        glVertex2f(screenWidth, 0);
        glVertex2f(screenWidth, screenHeight);
        glVertex2f(0, screenHeight);
        glEnd();

        glEnable(GL_TEXTURE_2D);

        renderTitle();
        super.render(mouseX, mouseY);
    }
}

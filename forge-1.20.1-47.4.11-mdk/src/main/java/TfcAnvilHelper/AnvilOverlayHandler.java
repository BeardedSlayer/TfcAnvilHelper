package TfcAnvilHelper;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;

@Mod.EventBusSubscriber(modid = "tfcanvilhelper", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class AnvilOverlayHandler {
    private static boolean overlayOpen = false;
    private static int buttonX = Integer.MIN_VALUE;
    private static int buttonY = Integer.MIN_VALUE;
    private static final int BUTTON_W = 16;
    private static final int BUTTON_H = 18;

    private static int calcX = -1, calcY = -1;
    private static final int CALC_WIDTH = 160;
    private static final int CALC_HEIGHT = 130;

    private static boolean positionsInitialized = false;
    private static final String PROP_FILE = "tfcanvilhelper_gui.properties";

    private static int dragTarget = 0; // 0 none, 1 main button, 2 calc window
    private static boolean isDragging = false;
    private static int dragStartX = 0, dragStartY = 0;
    private static int dragOffsetX = 0, dragOffsetY = 0;
    private static final int DRAG_THRESHOLD = 4;

    private static String targetNumber = "";
    private static String selectedAction1 = "";
    private static String selectedAction2 = "";
    private static String selectedAction3 = "";
    private static int activeMenu = -1; // -1 none, 0..2
    private static String resultText = ""; // показываем в отдельной плашке снизу
    private static final String[] ACTIONS_LIST = {"0", "+2", "+7", "+13", "+16", "-3", "-6", "-9", "-15"};
    private static int activeInputField = -1; // -1 none, 0 target input

    private static long lastCharTime = 0L;

    // ===== Result toast UI constants =====
    private static final int TOAST_PADDING_X = 8;
    private static final int TOAST_PADDING_Y = 6;
    private static final int TOAST_BOTTOM_MARGIN = 12;

    private static final int TOAST_CLOSE_W = 12;
    private static final int TOAST_CLOSE_H = 12;
    private static final int TOAST_CLOSE_GAP = 6;

    private static final int TOAST_MAX_WIDTH_MARGIN = 20; // max toast width = screenW - margin*2

    private static class Layout {
        int inputX, inputY, inputW, inputH;
        int bsX, bsY, bsW, bsH;

        int btnY, btnW, btnH;
        int[] btnX;

        int calcBtnX, calcBtnY, calcBtnW, calcBtnH;

        int menuW, itemH;
    }

    private static Layout layout() {
        Layout l = new Layout();

        l.inputX = calcX + 5; l.inputY = calcY + 18; l.inputW = 64; l.inputH = 14;
        l.bsX = l.inputX + l.inputW + 4; l.bsY = l.inputY; l.bsW = 12; l.bsH = l.inputH;

        l.btnY = calcY + 50;
        l.btnW = 44; l.btnH = 14;
        int gap = 4;
        int startX = calcX + 5;
        l.btnX = new int[]{ startX, startX + l.btnW + gap, startX + (l.btnW + gap) * 2 };

        l.calcBtnX = calcX + 18;
        l.calcBtnW = CALC_WIDTH - 36;
        l.calcBtnH = 14;
        l.calcBtnY = calcY + CALC_HEIGHT - (l.calcBtnH + 6);

        // opaque dropdown
        l.menuW = 80;
        l.itemH = 14;

        return l;
    }

    private static void loadPositions() {
        if (positionsInitialized) return;
        positionsInitialized = true;
        try {
            Path p = Minecraft.getInstance().gameDirectory.toPath().resolve(PROP_FILE);
            if (Files.exists(p)) {
                Properties props = new Properties();
                try (InputStream in = Files.newInputStream(p)) { props.load(in); }
                buttonX = Integer.parseInt(props.getProperty("buttonX", String.valueOf(buttonX)));
                buttonY = Integer.parseInt(props.getProperty("buttonY", String.valueOf(buttonY)));
                calcX = Integer.parseInt(props.getProperty("calcX", String.valueOf(calcX)));
                calcY = Integer.parseInt(props.getProperty("calcY", String.valueOf(calcY)));
            }
        } catch (Exception ignored) {}
    }

    private static void savePositions() {
        try {
            Path p = Minecraft.getInstance().gameDirectory.toPath().resolve(PROP_FILE);
            Properties props = new Properties();
            props.setProperty("buttonX", String.valueOf(buttonX));
            props.setProperty("buttonY", String.valueOf(buttonY));
            props.setProperty("calcX", String.valueOf(calcX));
            props.setProperty("calcY", String.valueOf(calcY));
            try (OutputStream out = Files.newOutputStream(p, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                props.store(out, "TfcAnvilHelper");
            }
        } catch (Exception ignored) {}
    }

    private static boolean isOnMainButton(int mx, int my) {
        return mx >= buttonX && mx < buttonX + BUTTON_W && my >= buttonY && my < buttonY + BUTTON_H;
    }

    private static void clampButtonPosition() {
        int screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int screenHeight = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        buttonX = Math.min(Math.max(buttonX, 0), screenWidth - BUTTON_W);
        buttonY = Math.min(Math.max(buttonY, 0), screenHeight - BUTTON_H);
    }

    private static void clampCalcPosition() {
        int screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int screenHeight = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        calcX = Math.min(Math.max(calcX, 0), screenWidth - CALC_WIDTH);
        calcY = Math.min(Math.max(calcY, 0), screenHeight - CALC_HEIGHT);
    }

    // ===== Result toast helpers =====
    private static class ToastRect {
        int x0, y0, w, h;
        int closeX0, closeY0, closeX1, closeY1;
        String displayText;
    }

    private static String ellipsizeToWidth(String s, int maxWidthPx) {
        if (s == null) return "";
        Minecraft mc = Minecraft.getInstance();
        if (mc.font.width(s) <= maxWidthPx) return s;

        String dots = "...";
        int dotsW = mc.font.width(dots);
        if (dotsW >= maxWidthPx) return dots;

        int lo = 0, hi = s.length();
        while (lo < hi) {
            int mid = (lo + hi + 1) / 2;
            String cand = s.substring(0, mid) + dots;
            if (mc.font.width(cand) <= maxWidthPx) lo = mid;
            else hi = mid - 1;
        }
        return s.substring(0, lo) + dots;
    }

    private static ToastRect computeToast() {
        if (resultText == null || resultText.isEmpty()) return null;

        Minecraft mc = Minecraft.getInstance();
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();

        int maxToastW = Math.max(60, screenW - TOAST_MAX_WIDTH_MARGIN * 2);

        // место под текст: общие отступы + место под крестик
        int maxTextW = maxToastW - (TOAST_PADDING_X * 2) - (TOAST_CLOSE_GAP + TOAST_CLOSE_W);
        if (maxTextW < 20) maxTextW = 20;

        String display = ellipsizeToWidth(resultText, maxTextW);

        int textW = mc.font.width(display);
        int boxW = textW + TOAST_PADDING_X * 2 + TOAST_CLOSE_GAP + TOAST_CLOSE_W;
        boxW = Math.min(boxW, maxToastW);

        int boxH = mc.font.lineHeight + TOAST_PADDING_Y * 2;

        int x0 = (screenW - boxW) / 2;
        int y0 = screenH - boxH - TOAST_BOTTOM_MARGIN;

        ToastRect tr = new ToastRect();
        tr.x0 = x0; tr.y0 = y0; tr.w = boxW; tr.h = boxH;
        tr.displayText = display;

        int cx0 = x0 + boxW - TOAST_PADDING_X - TOAST_CLOSE_W;
        int cy0 = y0 + (boxH - TOAST_CLOSE_H) / 2;
        tr.closeX0 = cx0; tr.closeY0 = cy0;
        tr.closeX1 = cx0 + TOAST_CLOSE_W; tr.closeY1 = cy0 + TOAST_CLOSE_H;

        return tr;
    }

    private static void drawResultToast(GuiGraphics g) {
        ToastRect tr = computeToast();
        if (tr == null) return;

        // opaque background + border
        g.fill(tr.x0, tr.y0, tr.x0 + tr.w, tr.y0 + tr.h, 0xFF111111);
        g.fill(tr.x0, tr.y0, tr.x0 + tr.w, tr.y0 + 1, 0xFF444444);
        g.fill(tr.x0, tr.y0 + tr.h - 1, tr.x0 + tr.w, tr.y0 + tr.h, 0xFF444444);
        g.fill(tr.x0, tr.y0, tr.x0 + 1, tr.y0 + tr.h, 0xFF444444);
        g.fill(tr.x0 + tr.w - 1, tr.y0, tr.x0 + tr.w, tr.y0 + tr.h, 0xFF444444);

        Minecraft mc = Minecraft.getInstance();
        g.drawString(mc.font, tr.displayText, tr.x0 + TOAST_PADDING_X, tr.y0 + TOAST_PADDING_Y, 0xFFFFFF);

        // close button (X)
        g.fill(tr.closeX0, tr.closeY0, tr.closeX1, tr.closeY1, 0xFF333333);
        g.drawCenteredString(mc.font, "X", (tr.closeX0 + tr.closeX1) / 2, tr.closeY0 + 2, 0xFFFFFF);
    }

    private static boolean isClickOnToastClose(int mx, int my) {
        ToastRect tr = computeToast();
        if (tr == null) return false;
        return mx >= tr.closeX0 && mx < tr.closeX1 && my >= tr.closeY0 && my < tr.closeY1;
    }

    private static boolean isClickOnToast(int mx, int my) {
        ToastRect tr = computeToast();
        if (tr == null) return false;
        return mx >= tr.x0 && mx < tr.x0 + tr.w && my >= tr.y0 && my < tr.y0 + tr.h;
    }

    @SubscribeEvent
    public static void onScreenRender(ScreenEvent.Render.Post event) {
        Screen screen = event.getScreen();
        if (!(screen instanceof AbstractContainerScreen<?>)) return;

        String cls = screen.getClass().getName();
        if (!cls.contains("AnvilScreen") && !cls.contains("InventoryScreen")) return;

        if (!positionsInitialized) {
            int screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
            if (buttonX == Integer.MIN_VALUE) buttonX = (screenWidth / 2) - (BUTTON_W / 2);
            if (buttonY == Integer.MIN_VALUE) buttonY = 10;
            if (calcX == -1) calcX = (screenWidth / 2) - (CALC_WIDTH / 2);
            if (calcY == -1) calcY = 30;
            loadPositions();
        }

        // drag handling
        if (dragTarget > 0) {
            Minecraft mc = Minecraft.getInstance();
            long win = mc.getWindow().getWindow();
            double guiScale = mc.getWindow().getGuiScale();
            double mx = mc.mouseHandler.xpos() / guiScale;
            double my = mc.mouseHandler.ypos() / guiScale;
            int imx = (int) Math.round(mx), imy = (int) Math.round(my);

            if (GLFW.glfwGetMouseButton(win, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_RELEASE) {
                if (isDragging) savePositions();
                dragTarget = 0;
                isDragging = false;
            } else {
                if (!isDragging) {
                    if (Math.abs(imx - dragStartX) > DRAG_THRESHOLD || Math.abs(imy - dragStartY) > DRAG_THRESHOLD) {
                        isDragging = true;
                    }
                }
                if (isDragging) {
                    if (dragTarget == 1) {
                        buttonX = imx - dragOffsetX;
                        buttonY = imy - dragOffsetY;
                        clampButtonPosition();
                    } else if (dragTarget == 2) {
                        calcX = imx - dragOffsetX;
                        calcY = imy - dragOffsetY;
                        clampCalcPosition();
                    }
                }
            }
        }

        // keyboard polling fallback
        if (overlayOpen && activeInputField == 0) {
            try {
                Minecraft mc = Minecraft.getInstance();
                long win = mc.getWindow().getWindow();
                long now = System.currentTimeMillis();
                if (now - lastCharTime > 80) {
                    for (int i = 0; i <= 9; i++) {
                        int key = GLFW.GLFW_KEY_0 + i;
                        if (GLFW.glfwGetKey(win, key) == GLFW.GLFW_PRESS) {
                            targetNumber = targetNumber + (char) ('0' + i);
                            lastCharTime = now;
                            break;
                        }
                    }
                    for (int i = 0; i <= 9; i++) {
                        int key = GLFW.GLFW_KEY_KP_0 + i;
                        if (GLFW.glfwGetKey(win, key) == GLFW.GLFW_PRESS) {
                            targetNumber = targetNumber + (char) ('0' + i);
                            lastCharTime = now;
                            break;
                        }
                    }
                    if (GLFW.glfwGetKey(win, GLFW.GLFW_KEY_BACKSPACE) == GLFW.GLFW_PRESS) {
                        if (!targetNumber.isEmpty()) targetNumber = targetNumber.substring(0, targetNumber.length() - 1);
                        lastCharTime = now;
                    }
                    if (GLFW.glfwGetKey(win, GLFW.GLFW_KEY_ENTER) == GLFW.GLFW_PRESS ||
                            GLFW.glfwGetKey(win, GLFW.GLFW_KEY_KP_ENTER) == GLFW.GLFW_PRESS) {
                        performCalculation();
                        lastCharTime = now;
                    }
                    if (GLFW.glfwGetKey(win, GLFW.GLFW_KEY_ESCAPE) == GLFW.GLFW_PRESS) {
                        overlayOpen = false;
                        lastCharTime = now;
                    }
                }
            } catch (Throwable ignored) {}
        }

        Layout l = layout();
        GuiGraphics g = event.getGuiGraphics();

        // main toggle button
        int buttonColor = overlayOpen ? 0xFF00FF00 : 0xFF666666;
        g.fill(buttonX, buttonY, buttonX + BUTTON_W, buttonY + BUTTON_H, buttonColor);
        g.drawCenteredString(Minecraft.getInstance().font, "⧉", buttonX + 8, buttonY + 5, 0xFFFFFF);

        // result toast always visible if has text
        drawResultToast(g);

        if (!overlayOpen) return;

        // background
        g.fill(calcX - 5, calcY - 5, calcX + CALC_WIDTH, calcY + CALC_HEIGHT, 0xBB111111);

        // input
        g.drawString(Minecraft.getInstance().font, "Целевое число:", calcX + 5, calcY + 5, 0xFFFFFF);
        int inputBg = (activeInputField == 0) ? 0xFF333333 : 0xFF000000;
        g.fill(l.inputX, l.inputY, l.inputX + l.inputW, l.inputY + l.inputH, inputBg);

        String disp = targetNumber.isEmpty() ? "" : targetNumber;
        if (activeInputField == 0) disp = disp + "|";
        g.drawString(Minecraft.getInstance().font, disp, l.inputX + 2, l.inputY + 2, 0xFFFFFF);

        // backspace
        g.fill(l.bsX, l.bsY, l.bsX + l.bsW, l.bsY + l.bsH, 0xFF444444);
        g.drawString(Minecraft.getInstance().font, "←", l.bsX + 2, l.bsY + 2, 0xFFFFFF);

        // actions
        g.drawString(Minecraft.getInstance().font, "Действия:", calcX + 5, calcY + 36, 0xFFFFFF);
        for (int i = 0; i < 3; i++) {
            int bx = l.btnX[i];
            g.fill(bx, l.btnY, bx + l.btnW, l.btnY + l.btnH, 0xFF444444);
            g.drawString(Minecraft.getInstance().font, (i + 1) + ".", bx + 3, l.btnY + 2, 0xFFFFFF);

            String at = (i == 0) ? (selectedAction1.isEmpty() ? "---" : selectedAction1)
                    : (i == 1) ? (selectedAction2.isEmpty() ? "---" : selectedAction2)
                    : (selectedAction3.isEmpty() ? "---" : selectedAction3);
            g.drawString(Minecraft.getInstance().font, at, bx + 16, l.btnY + 2, 0xFFFFFF);
        }

        // calculate button
        g.fill(l.calcBtnX, l.calcBtnY, l.calcBtnX + l.calcBtnW, l.calcBtnY + l.calcBtnH, 0xFF00AA00);
        g.drawCenteredString(Minecraft.getInstance().font, "Рассчитать", l.calcBtnX + l.calcBtnW / 2, l.calcBtnY + 2, 0xFFFFFF);

        // dropdown (opaque + border)
        if (activeMenu >= 0 && activeMenu < 3) {
            g.pose().pushPose();
            g.pose().translate(0, 0, 500); // поднять слой сильно вперёд

            int menuX = l.btnX[activeMenu];
            int menuY = l.btnY + l.btnH + 4;
            int menuH = ACTIONS_LIST.length * l.itemH;

            g.fill(menuX, menuY, menuX + l.menuW, menuY + menuH, 0xFF202020);
            // рамка...
            for (int i = 0; i < ACTIONS_LIST.length; i++) {
                g.drawString(Minecraft.getInstance().font, ACTIONS_LIST[i],
                        menuX + 4, menuY + 2 + i * l.itemH, 0xFFFFFF);
            }

            g.pose().popPose();
        }

    }

    @SubscribeEvent
    public static void onMouseButtonPressed(ScreenEvent.MouseButtonPressed.Pre event) {
        Screen screen = event.getScreen();
        if (!(screen instanceof AbstractContainerScreen<?>)) return;

        String cls = screen.getClass().getName();
        if (!cls.contains("AnvilScreen") && !cls.contains("InventoryScreen")) return;

        if (event.getButton() != 0) return;

        int mx = (int) Math.round(event.getMouseX());
        int my = (int) Math.round(event.getMouseY());

        // click close on toast
        if (isClickOnToastClose(mx, my)) {
            resultText = "";
            event.setCanceled(true);
            return;
        }
        // block clicks under the toast
        if (isClickOnToast(mx, my)) {
            event.setCanceled(true);
            return;
        }

        if (isOnMainButton(mx, my)) {
            dragTarget = 1;
            dragStartX = mx; dragStartY = my;
            dragOffsetX = mx - buttonX; dragOffsetY = my - buttonY;
            isDragging = false;
            event.setCanceled(true);
            return;
        }

        if (!overlayOpen) return;

        Layout l = layout();

        // click in dropdown: don't start drag
        if (activeMenu >= 0 && activeMenu < 3) {
            int menuX = l.btnX[activeMenu];
            int menuY = l.btnY + l.btnH + 4;
            if (mx >= menuX && mx < menuX + l.menuW && my >= menuY && my < menuY + (ACTIONS_LIST.length * l.itemH)) {
                event.setCanceled(true);
                return;
            }
        }

        // input focus
        if (mx >= l.inputX && mx < l.inputX + l.inputW && my >= l.inputY && my < l.inputY + l.inputH) {
            activeInputField = 0;
            lastCharTime = 0;
            event.setCanceled(true);
            return;
        }

        // action buttons: no drag on press
        for (int i = 0; i < 3; i++) {
            int bx = l.btnX[i];
            if (mx >= bx && mx < bx + l.btnW && my >= l.btnY && my < l.btnY + l.btnH) {
                event.setCanceled(true);
                return;
            }
        }

        // calc window drag start
        if (mx >= calcX - 5 && mx < calcX + CALC_WIDTH && my >= calcY - 5 && my < calcY + CALC_HEIGHT) {
            dragTarget = 2;
            dragStartX = mx; dragStartY = my;
            dragOffsetX = mx - calcX; dragOffsetY = my - calcY;
            isDragging = false;
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onMouseButtonReleased(ScreenEvent.MouseButtonReleased.Pre event) {
        Screen screen = event.getScreen();
        if (!(screen instanceof AbstractContainerScreen<?>)) return;

        String cls = screen.getClass().getName();
        if (!cls.contains("AnvilScreen") && !cls.contains("InventoryScreen")) return;

        if (event.getButton() != 0) return;

        int mx = (int) Math.round(event.getMouseX());
        int my = (int) Math.round(event.getMouseY());

        if (dragTarget == 1) {
            if (isDragging) savePositions();
            else {
                if (isOnMainButton(mx, my)) {
                    overlayOpen = !overlayOpen;
                    activeMenu = -1;
                    activeInputField = -1;
                }
            }
            dragTarget = 0;
            isDragging = false;
            event.setCanceled(true);
            return;
        }

        if (dragTarget == 2) {
            if (isDragging) savePositions();
            dragTarget = 0;
            isDragging = false;
            event.setCanceled(true);
        }

        if (!overlayOpen) return;

        Layout l = layout();

        // backspace
        if (mx >= l.bsX && mx < l.bsX + l.bsW && my >= l.bsY && my < l.bsY + l.bsH) {
            if (!targetNumber.isEmpty()) targetNumber = targetNumber.substring(0, targetNumber.length() - 1);
            event.setCanceled(true);
            return;
        }

        // action buttons
        for (int i = 0; i < 3; i++) {
            int bx = l.btnX[i];
            if (mx >= bx && mx < bx + l.btnW && my >= l.btnY && my < l.btnY + l.btnH) {
                activeMenu = (activeMenu == i) ? -1 : i;
                activeInputField = -1;
                event.setCanceled(true);
                return;
            }
        }

        // dropdown select
        if (activeMenu >= 0 && activeMenu < 3) {
            int menuX = l.btnX[activeMenu];
            int menuY = l.btnY + l.btnH + 4;
            if (mx >= menuX && mx < menuX + l.menuW && my >= menuY && my < menuY + (ACTIONS_LIST.length * l.itemH)) {
                int idx = (my - menuY) / l.itemH;
                if (idx >= 0 && idx < ACTIONS_LIST.length) {
                    String sel = ACTIONS_LIST[idx];
                    if (activeMenu == 0) selectedAction1 = sel;
                    else if (activeMenu == 1) selectedAction2 = sel;
                    else selectedAction3 = sel;
                }
                activeMenu = -1;
                event.setCanceled(true);
                return;
            }
            activeMenu = -1;
        }

        // calculate
        if (mx >= l.calcBtnX && mx < l.calcBtnX + l.calcBtnW && my >= l.calcBtnY && my < l.calcBtnY + l.calcBtnH) {
            performCalculation();
            event.setCanceled(true);
        }
    }

    private static void performCalculation() {
        if (targetNumber.isEmpty()) { resultText = "Введите целевое число!"; return; }
        int target;
        try { target = Integer.parseInt(targetNumber); } catch (Exception e) { resultText = "Неверное число"; return; }

        List<String> lastThree = new ArrayList<>();
        if (!selectedAction1.isEmpty()) lastThree.add(selectedAction1);
        if (!selectedAction2.isEmpty()) lastThree.add(selectedAction2);
        if (!selectedAction3.isEmpty()) lastThree.add(selectedAction3);

        List<String> solution = AnvilCalculator.calculate(target, lastThree);
        if (solution.isEmpty()) { resultText = "Решение не найдено"; return; }

        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String action : solution) counts.put(action, counts.getOrDefault(action, 0) + 1);

        StringBuilder res = new StringBuilder();
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            if (res.length() > 0) res.append(", ");
            res.append(e.getValue()).append("x").append(e.getKey());
        }
        resultText = res.toString();
    }
}

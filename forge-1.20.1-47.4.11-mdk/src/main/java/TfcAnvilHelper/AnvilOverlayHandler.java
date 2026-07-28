package TfcAnvilHelper;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
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

    // ==========================================
    // НАСТРОЙКИ И ТЕХНИЧЕСКИЕ ПЕРЕМЕННЫЕ
    // ==========================================
    private static final int ANIM_DURATION = 350;
    private static final int STEP_GAP = 10;

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

    // Логика перетаскивания (Drag & Drop)
    private static int dragTarget = 0;
    private static boolean isDragging = false;
    private static int dragStartX = 0, dragStartY = 0;
    private static int dragOffsetX = 0, dragOffsetY = 0;
    private static final int DRAG_THRESHOLD = 4;

    // Состояния полей ввода
    private static String targetNumber = "";
    private static String selectedAction1 = "";
    private static String selectedAction2 = "";
    private static String selectedAction3 = "";
    private static int activeMenu = -1;
    private static String resultText = "";
    private static final String[] ACTIONS_LIST = {"-", "+2", "+7", "+13", "+16", "-3", "-6", "-9", "-15"};
    private static int activeInputField = -1;

    private static long lastCharTime = 0L;

    // Переменные для быстрой анимации смены текста (200 миллисекунд)
    private static long stepTransitionStart = 0L;
    private static String oldToastDisplay = "";
    private static final int TOAST_ANIM_MS = 200;

    // Настройки отступов плашки подсказок
    private static final int TOAST_PADDING_X = 8;
    private static final int TOAST_PADDING_Y = 6;
    private static final int TOAST_BOTTOM_MARGIN = 40;
    private static final int TOAST_CLOSE_W = 12;
    private static final int TOAST_CLOSE_H = 12;
    private static final int TOAST_CLOSE_GAP = 6;
    private static final int TOAST_MAX_WIDTH_MARGIN = 20;

    // ==========================================
    // ПРЕМИАЛЬНАЯ ЦВЕТОВАЯ ПАЛИТРА
    // ==========================================
    private static final int COLOR_BG_MAIN = 0xD5141416;
    private static final int COLOR_BG_WIDGET = 0xFF222224;
    private static final int COLOR_BG_WIDGET_HOVER = 0xFF323236;
    private static final int COLOR_BORDER = 0xFF444448;
    private static final int COLOR_BORDER_HIGH = 0xFF63636B;

    private static final int COLOR_BTN_GREEN = 0xFF2E6F40;
    private static final int COLOR_BTN_GREEN_HOVER = 0xFF3B8B50;
    private static final int COLOR_BTN_GREEN_BORDER = 0xFF1B4D2A;

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
        l.menuW = 44;
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

    private static class ToastRect {
        int x0, y0, w, h;
        int closeX0, closeY0, closeX1, closeY1;
        String displayText;
    }

    // ==========================================
    // КООРДИНАТНАЯ КАРТА КНОПОК НАКОВАЛЬНИ TFC
    // ==========================================
    // Возвращает [относительный X, относительный Y, Ширина, Высота] кнопки молотка внутри текстуры TFC
    private static int[] getActionRect(String action) {
        switch (action) {
            case "-3":  return new int[]{53, 50, 16, 16};
            case "-6":  return new int[]{71, 50, 16, 16};
            case "+2":  return new int[]{89, 50, 16, 16};
            case "+7":  return new int[]{107, 50, 16, 16};
            case "-9":  return new int[]{53, 68, 16, 16};
            case "-15": return new int[]{71, 68, 16, 16};
            case "+13": return new int[]{89, 68, 16, 16};
            case "+16": return new int[]{107, 68, 16, 16};
            default:    return null;
        }
    }

    // ==========================================
    // МЕТОД ОПРЕДЕЛЕНИЯ КЛИКА ПО КНОПКАМ TFC
    // ==========================================
    private static String getActionAt(int mx, int my, AbstractContainerScreen screen) {
        int relX = mx - screen.getGuiLeft();
        int relY = my - screen.getGuiTop();

        // Проверяем верхний ряд кнопок молотков
        if (relY >= 50 && relY <= 66) {
            if (relX >= 53 && relX <= 69) return "-3";
            if (relX >= 71 && relX <= 87) return "-6";
            if (relX >= 89 && relX <= 104) return "+2";
            if (relX >= 107 && relX <= 123) return "+7";
        }

        // Проверяем нижний ряд кнопок молотков
        if (relY >= 68 && relY <= 84) {
            if (relX >= 53 && relX <= 69) return "-9";
            if (relX >= 71 && relX <= 87) return "-15";
            if (relX >= 89 && relX <= 105) return "+13";
            if (relX >= 107 && relX <= 123) return "+16";
        }

        return null; // Если кликнули мимо кнопок TFC
    }

    // ==========================================
    // РЕНДЕР АНИМИРОВАННОЙ РАМКИ-ЗЕБРЫ
    // ==========================================
    private static void drawZebraBorder(GuiGraphics g, int x, int y, int w, int h) {
        // Скорость движения полосок
        long time = System.currentTimeMillis() / 60;
        int period = 6; // Ширина шага зебры (3px белых, 3px черных)

        // Верхняя грань (рисуем на 1px выше кнопки)
        for (int i = -1; i <= w; i++) {
            int index = i + (int) time;
            int color = (Math.floorMod(index, period) < 3) ? 0xFFFFFFFF : 0xFF000000;
            g.fill(x + i, y - 1, x + i + 1, y, color);
        }

        // Нижня грань (рисуем на 1px ниже кнопки)
        for (int i = -1; i <= w; i++) {
            int index = (w - i) + (int) time;
            int color = (Math.floorMod(index, period) < 3) ? 0xFFFFFFFF : 0xFF000000;
            g.fill(x + i, y + h, x + i + 1, y + h + 1, color);
        }

        // Левая грань (рисуем на 1px левее кнопки)
        for (int j = 0; j < h; j++) {
            int index = (h - j) + (int) time;
            int color = (Math.floorMod(index, period) < 3) ? 0xFFFFFFFF : 0xFF000000;
            g.fill(x - 1, y + j, x, y + j + 1, color);
        }

        // Правая грань (рисуем на 1px правее кнопки)
        for (int j = 0; j < h; j++) {
            int index = j + (int) time;
            int color = (Math.floorMod(index, period) < 3) ? 0xFFFFFFFF : 0xFF000000;
            g.fill(x + w, y + j, x + w + 1, y + j + 1, color);
        }
    }

    // Сборка строки текстового уведомления
    private static String getToastDisplayStr(int stepIdx) {
        if (!plannedActions.isEmpty()) {
            if (stepIdx < plannedActions.size()) {
                String current = plannedActions.get(stepIdx);
                if (stepIdx + 1 < plannedActions.size()) {
                    return Component.translatable("msg.tfcanvilhelper.current_step", current, plannedActions.get(stepIdx + 1)).getString();
                } else {
                    return Component.translatable("msg.tfcanvilhelper.last_step", current).getString();
                }
            } else {
                return Component.translatable("msg.tfcanvilhelper.done").getString();
            }
        }
        return (resultText != null) ? resultText : "";
    }

    // Триггер запуска анимации переключения шагов текста
    private static void triggerStepAdvance() {
        if (plannedActions.isEmpty()) return;
        oldToastDisplay = getToastDisplayStr(currentStepIndex);
        currentStepIndex++;
        stepTransitionStart = System.currentTimeMillis();
    }

    private static ToastRect computeToast() {
        if ((resultText == null || resultText.isEmpty()) && plannedActions.isEmpty()) return null;

        Minecraft mc = Minecraft.getInstance();
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();

        String display = getToastDisplayStr(currentStepIndex);
        int textW = mc.font.width(display);

        // Чтобы рамка не дергалась во время сдвига, сохраняем максимальную ширину между старым и новым текстом
        long elapsed = System.currentTimeMillis() - stepTransitionStart;
        if (elapsed < TOAST_ANIM_MS && !oldToastDisplay.isEmpty()) {
            int oldW = mc.font.width(oldToastDisplay);
            textW = Math.max(textW, oldW);
        }

        int boxW = textW + 40;
        int boxH = 21;
        int x0 = (screenW - boxW) / 2;
        int y0 = screenH - boxH - 40;

        ToastRect tr = new ToastRect();
        tr.x0 = x0; tr.y0 = y0; tr.w = boxW; tr.h = boxH;
        tr.displayText = display;
        tr.closeX0 = x0 + boxW - 20;
        tr.closeY0 = y0 + 4;
        tr.closeX1 = tr.closeX0 + 12;
        tr.closeY1 = tr.closeY0 + 12;
        return tr;
    }

    private static void drawResultToast(GuiGraphics g) {
        ToastRect tr = computeToast();
        if (tr == null) return;

        g.fill(tr.x0, tr.y0, tr.x0 + tr.w, tr.y0 + tr.h, COLOR_BG_MAIN);
        g.fill(tr.x0, tr.y0, tr.x0 + tr.w, tr.y0 + 1, COLOR_BORDER);
        g.fill(tr.x0, tr.y0 + tr.h - 1, tr.x0 + tr.w, tr.y0 + tr.h, COLOR_BORDER);
        g.fill(tr.x0, tr.y0, tr.x0 + 1, tr.y0 + tr.h, COLOR_BORDER);
        g.fill(tr.x0 + tr.w - 1, tr.y0, tr.x0 + tr.w, tr.y0 + tr.h, COLOR_BORDER);

        Minecraft mc = Minecraft.getInstance();
        long elapsed = System.currentTimeMillis() - stepTransitionStart;

        if (elapsed < TOAST_ANIM_MS && !oldToastDisplay.isEmpty()) {
            float progress = (float) elapsed / TOAST_ANIM_MS;
            float easeOut = 1.0f - (float) Math.pow(1.0f - progress, 3); // Быстрый сдвиг Cubic Ease-Out

            // Рендер улетающего старого текста
            int oldAlpha = (int) ((1.0f - progress) * 255);
            int oldColor = (oldAlpha << 24) | 0xFFFFFF;
            int oldOffsetX = (int) (-easeOut * 25);
            g.pose().pushPose();
            g.pose().translate(oldOffsetX, 0, 0);
            g.drawString(mc.font, oldToastDisplay, tr.x0 + TOAST_PADDING_X, tr.y0 + TOAST_PADDING_Y, oldColor, false);
            g.pose().popPose();

            // Рендер налетающего нового текста
            int newAlpha = (int) (progress * 255);
            int newColor = (newAlpha << 24) | 0xFFFFFF;
            int newOffsetX = (int) ((1.0f - easeOut) * 25);
            g.pose().pushPose();
            g.pose().translate(newOffsetX, 0, 0);
            g.drawString(mc.font, tr.displayText, tr.x0 + TOAST_PADDING_X, tr.y0 + TOAST_PADDING_Y, newColor, false);
            g.pose().popPose();
        } else {
            g.drawString(mc.font, tr.displayText, tr.x0 + TOAST_PADDING_X, tr.y0 + TOAST_PADDING_Y, 0xFFFFFF, false);
        }

        g.fill(tr.closeX0, tr.closeY0, tr.closeX1, tr.closeY1, COLOR_BG_WIDGET);
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

    // ==========================================
    // СЕРДЦЕ РЕНДЕРА ИНТЕРФЕЙСА МОДА
    // ==========================================
    @SubscribeEvent
    public static void onScreenRender(ScreenEvent.Render.Post event) {
        Screen screen = event.getScreen();
        if (!(screen instanceof AbstractContainerScreen<?> containerScreen)) return;

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

        int mx = (int) Math.round(event.getMouseX());
        int my = (int) Math.round(event.getMouseY());

        // Логика перетаскивания элементов мышкой
        if (dragTarget > 0) {
            Minecraft mc = Minecraft.getInstance();
            long win = mc.getWindow().getWindow();
            double guiScale = mc.getWindow().getGuiScale();
            int imx = (int) Math.round(mc.mouseHandler.xpos() / guiScale);
            int imy = (int) Math.round(mc.mouseHandler.ypos() / guiScale);

            if (GLFW.glfwGetMouseButton(win, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_RELEASE) {
                if (isDragging) savePositions();
                dragTarget = 0; isDragging = false;
            } else {
                if (!isDragging && (Math.abs(imx - dragStartX) > DRAG_THRESHOLD || Math.abs(imy - dragStartY) > DRAG_THRESHOLD)) {
                    isDragging = true;
                }
                if (isDragging) {
                    if (dragTarget == 1) { buttonX = imx - dragOffsetX; buttonY = imy - dragOffsetY; clampButtonPosition(); }
                    else if (dragTarget == 2) { calcX = imx - dragOffsetX; calcY = imy - dragOffsetY; clampCalcPosition(); }
                }
            }
        }

        // Поллинг клавиатуры
        if (overlayOpen && activeInputField == 0) {
            try {
                Minecraft mc = Minecraft.getInstance();
                long win = mc.getWindow().getWindow();
                long now = System.currentTimeMillis();
                if (now - lastCharTime > 80) {
                    for (int i = 0; i <= 9; i++) {
                        if (GLFW.glfwGetKey(win, GLFW.GLFW_KEY_0 + i) == GLFW.GLFW_PRESS || GLFW.glfwGetKey(win, GLFW.GLFW_KEY_KP_0 + i) == GLFW.GLFW_PRESS) {
                            targetNumber += (char) ('0' + i); lastCharTime = now; break;
                        }
                    }
                    if (GLFW.glfwGetKey(win, GLFW.GLFW_KEY_BACKSPACE) == GLFW.GLFW_PRESS && !targetNumber.isEmpty()) {
                        targetNumber = targetNumber.substring(0, targetNumber.length() - 1); lastCharTime = now;
                    }
                    if (GLFW.glfwGetKey(win, GLFW.GLFW_KEY_ENTER) == GLFW.GLFW_PRESS || GLFW.glfwGetKey(win, GLFW.GLFW_KEY_KP_ENTER) == GLFW.GLFW_PRESS) {
                        performCalculation(); lastCharTime = now;
                    }
                    if (GLFW.glfwGetKey(win, GLFW.GLFW_KEY_ESCAPE) == GLFW.GLFW_PRESS) { overlayOpen = false; lastCharTime = now; }
                }
            } catch (Throwable ignored) {}
        }

        Layout l = layout();
        GuiGraphics g = event.getGuiGraphics();

        // ------------------------------------------
        // ДИНАМИЧЕСКИЙ ПОДДСВЕТ КНОПКИ МОЛОТКА НА НАКОВАЛЬНЕ TFC
        // ------------------------------------------
        if (cls.contains("AnvilScreen") && !plannedActions.isEmpty() && currentStepIndex < plannedActions.size()) {
            String expectedAction = plannedActions.get(currentStepIndex);
            int[] rect = getActionRect(expectedAction);
            if (rect != null) {
                // Переводим относительные координаты текстуры TFC в абсолютные оконные координаты игры
                int absX = containerScreen.getGuiLeft() + rect[0];
                int absY = containerScreen.getGuiTop() + rect[1];
                drawZebraBorder(g, absX, absY, rect[2], rect[3]);
            }
        }

        // Отрисовка маленькой круглой кнопки оверлея
        boolean mainBtnHovered = mx >= buttonX && mx < buttonX + BUTTON_W && my >= buttonY && my < buttonY + BUTTON_H;
        int mainBtnBg = overlayOpen ? COLOR_BTN_GREEN : (mainBtnHovered ? COLOR_BG_WIDGET_HOVER : COLOR_BG_WIDGET);
        int mainBtnBrd = overlayOpen ? COLOR_BTN_GREEN_BORDER : COLOR_BORDER;

        g.fill(buttonX, buttonY, buttonX + BUTTON_W, buttonY + BUTTON_H, mainBtnBg);
        g.fill(buttonX, buttonY, buttonX + BUTTON_W, buttonY + 1, mainBtnBrd);
        g.fill(buttonX, buttonY + BUTTON_H - 1, buttonX + BUTTON_W, buttonY + BUTTON_H, mainBtnBrd);
        g.fill(buttonX, buttonY, buttonX + 1, buttonY + BUTTON_H, mainBtnBrd);
        g.fill(buttonX + BUTTON_W - 1, buttonY, buttonX + BUTTON_W, buttonY + BUTTON_H, mainBtnBrd);
        g.drawCenteredString(Minecraft.getInstance().font, "⧉", buttonX + 8, buttonY + 5, 0xFFFFFF);

        drawResultToast(g);

        if (!overlayOpen) return;

        // Фон главного окна калькулятора
        int winX0 = calcX - 5, winY0 = calcY - 5, winX1 = calcX + CALC_WIDTH, winY1 = calcY + CALC_HEIGHT;
        g.fill(winX0, winY0, winX1, winY1, COLOR_BG_MAIN);
        g.fill(winX0, winY0, winX1, winY0 + 1, COLOR_BORDER);
        g.fill(winX0, winY1 - 1, winX1, winY1, COLOR_BORDER);
        g.fill(winX0, winY0, winX0 + 1, winY1, COLOR_BORDER);
        g.fill(winX1 - 1, winY0, winX1, winY1, COLOR_BORDER);

        // Поле ввода "Целевое число"
        g.drawString(Minecraft.getInstance().font, Component.translatable("gui.tfcanvilhelper.target_number"), calcX + 5, calcY + 5, 0xFFFFFF);
        boolean inputHovered = mx >= l.inputX && mx < l.inputX + l.inputW && my >= l.inputY && my < l.inputY + l.inputH;
        int inputBg = (activeInputField == 0) ? 0xFF141416 : 0xFF1C1C1E;
        int inputBrd = (activeInputField == 0) ? COLOR_BORDER_HIGH : (inputHovered ? COLOR_BORDER_HIGH : COLOR_BORDER);

        g.fill(l.inputX, l.inputY, l.inputX + l.inputW, l.inputY + l.inputH, inputBg);
        g.fill(l.inputX, l.inputY, l.inputX + l.inputW, l.inputY + 1, inputBrd);
        g.fill(l.inputX, l.inputY + l.inputH - 1, l.inputX + l.inputW, l.inputY + l.inputH, inputBrd);
        g.fill(l.inputX, l.inputY, l.inputX + 1, l.inputY + l.inputH, inputBrd);
        g.fill(l.inputX + l.inputW - 1, l.inputY, l.inputX + l.inputW, l.inputY + l.inputH, inputBrd);

        String disp = targetNumber.isEmpty() ? "" : targetNumber;
        if (activeInputField == 0) disp += "|";
        g.drawString(Minecraft.getInstance().font, disp, l.inputX + 4, l.inputY + 3, 0xFFFFFF);

        // Кнопка Backspace "←"
        boolean bsHovered = mx >= l.bsX && mx < l.bsX + l.bsW && my >= l.bsY && my < l.bsY + l.bsH;
        g.fill(l.bsX, l.bsY, l.bsX + l.bsW, l.bsY + l.bsH, bsHovered ? COLOR_BG_WIDGET_HOVER : COLOR_BG_WIDGET);
        g.fill(l.bsX, l.bsY, l.bsX + l.bsW, l.bsY + 1, COLOR_BORDER);
        g.fill(l.bsX, l.bsY + l.bsH - 1, l.bsX + l.bsW, l.bsY + l.bsH, COLOR_BORDER);
        g.fill(l.bsX, l.bsY, l.bsX + 1, l.bsY + l.bsH, COLOR_BORDER);
        g.fill(l.bsX + l.bsW - 1, l.bsY, l.bsX + l.bsW, l.bsY + l.bsH, COLOR_BORDER);
        g.drawString(Minecraft.getInstance().font, "←", l.bsX + 2, l.bsY + 3, 0xFFFFFF);

        // 3 Слота закрепленных действий
        g.drawString(Minecraft.getInstance().font, Component.translatable("gui.tfcanvilhelper.actions"), calcX + 5, calcY + 36, 0xFFFFFF);
        for (int i = 0; i < 3; i++) {
            int bx = l.btnX[i];
            boolean actHovered = mx >= bx && mx < bx + l.btnW && my >= l.btnY && my < l.btnY + l.btnH;
            int actBrd = (activeMenu == i) ? COLOR_BORDER_HIGH : COLOR_BORDER;

            g.fill(bx, l.btnY, bx + l.btnW, l.btnY + l.btnH, actHovered ? COLOR_BG_WIDGET_HOVER : COLOR_BG_WIDGET);
            g.fill(bx, l.btnY, bx + l.btnW, l.btnY + 1, actBrd);
            g.fill(bx, l.btnY + l.btnH - 1, bx + l.btnW, l.btnY + l.btnH, actBrd);
            g.fill(bx, l.btnY, bx + 1, l.btnY + l.btnH, actBrd);
            g.fill(bx + l.btnW - 1, l.btnY, bx + l.btnW, l.btnY + l.btnH, actBrd);

            g.drawString(Minecraft.getInstance().font, (i + 1) + ".", bx + 3, l.btnY + 3, 0x808080);

            String at = (i == 0) ? (selectedAction1.isEmpty() ? "-" : selectedAction1)
                    : (i == 1) ? (selectedAction2.isEmpty() ? "-" : selectedAction2)
                      : (selectedAction3.isEmpty() ? "-" : selectedAction3);
            int textOffset = at.equals("-") ? 20 : 16;
            g.drawString(Minecraft.getInstance().font, at, bx + textOffset, l.btnY + 3, 0xFFFFFF);
        }

        // Кнопка "Рассчитать"
        boolean calcHovered = mx >= l.calcBtnX && mx < l.calcBtnX + l.calcBtnW && my >= l.calcBtnY && my < l.calcBtnY + l.calcBtnH;
        int calcBg = calcHovered ? COLOR_BTN_GREEN_HOVER : COLOR_BTN_GREEN;
        g.fill(l.calcBtnX, l.calcBtnY, l.calcBtnX + l.calcBtnW, l.calcBtnY + l.calcBtnH, calcBg);
        g.fill(l.calcBtnX, l.calcBtnY, l.calcBtnX + l.calcBtnW, l.calcBtnY + 1, COLOR_BTN_GREEN_BORDER);
        g.fill(l.calcBtnX, l.calcBtnY + l.calcBtnH - 1, l.calcBtnX + l.calcBtnW, l.calcBtnY + l.calcBtnH, COLOR_BTN_GREEN_BORDER);
        g.fill(l.calcBtnX, l.calcBtnY, l.calcBtnX + 1, l.calcBtnY + l.calcBtnH, COLOR_BTN_GREEN_BORDER);
        g.fill(l.calcBtnX + l.calcBtnW - 1, l.calcBtnY, l.calcBtnX + l.calcBtnW, l.calcBtnY + l.calcBtnH, COLOR_BTN_GREEN_BORDER);
        g.drawCenteredString(Minecraft.getInstance().font, Component.translatable("gui.tfcanvilhelper.calculate"), l.calcBtnX + l.calcBtnW / 2, l.calcBtnY + 3, 0xFFFFFF);

        // Открытый выпадающий список вариантов
        if (activeMenu >= 0 && activeMenu < 3) {
            g.pose().pushPose();
            g.pose().translate(0, 0, 500);

            int menuX = l.btnX[activeMenu];
            int menuY = l.btnY + l.btnH + 2;
            int menuH = ACTIONS_LIST.length * l.itemH;

            g.fill(menuX, menuY, menuX + l.menuW, menuY + menuH, 0xFF1C1C1E);
            g.fill(menuX, menuY, menuX + l.menuW, menuY + 1, COLOR_BORDER);
            g.fill(menuX, menuY + menuH - 1, menuX + l.menuW, menuY + menuH, COLOR_BORDER);
            g.fill(menuX, menuY, menuX + 1, menuY + menuH, COLOR_BORDER);
            g.fill(menuX + l.menuW - 1, menuY, menuX + l.menuW, menuY + menuH, COLOR_BORDER);

            for (int i = 0; i < ACTIONS_LIST.length; i++) {
                int itemY = menuY + i * l.itemH;
                boolean itemHovered = mx >= menuX && mx < menuX + l.menuW && my >= itemY && my < itemY + l.itemH;
                if (itemHovered) g.fill(menuX + 1, itemY, menuX + l.menuW - 1, itemY + l.itemH, COLOR_BG_WIDGET_HOVER);

                String itemText = ACTIONS_LIST[i];
                int textW = Minecraft.getInstance().font.width(itemText);
                g.drawString(Minecraft.getInstance().font, itemText, menuX + (l.menuW - textW) / 2, itemY + 3, 0xFFFFFFFF, false);
            }
            g.pose().popPose();
        }
    }

    // ==========================================
    // КЛИКИ: НАЖАТИЕ МЫШИ
    // ==========================================
    @SubscribeEvent
    public static void onMouseButtonPressed(ScreenEvent.MouseButtonPressed.Pre event) {
        Screen screen = event.getScreen();
        if (!(screen instanceof AbstractContainerScreen<?>)) return;

        int mx = (int) Math.round(event.getMouseX());
        int my = (int) Math.round(event.getMouseY());

        ToastRect trForSkip = computeToast();
        if (trForSkip != null) {
            int skipX = trForSkip.closeX0 - 18;
            if (mx >= skipX && mx < skipX + 12 && my >= trForSkip.closeY0 && my < trForSkip.closeY1) {
                if (!plannedActions.isEmpty() && currentStepIndex < plannedActions.size()) { triggerStepAdvance(); }
                event.setCanceled(true); return;
            }
        }

        String cls = screen.getClass().getName();
        if (!cls.contains("AnvilScreen") && !cls.contains("InventoryScreen")) return;
        if (event.getButton() != 0) return;

        if (isClickOnToastClose(mx, my)) {
            resultText = ""; plannedActions.clear(); currentStepIndex = 0; event.setCanceled(true); return;
        }

        if (isClickOnToast(mx, my) && !isClickOnToastClose(mx, my)) {
            if (!plannedActions.isEmpty() && currentStepIndex < plannedActions.size()) { triggerStepAdvance(); }
            event.setCanceled(true); return;
        }

        if (isClickOnToast(mx, my)) { event.setCanceled(true); return; }

        if (isOnMainButton(mx, my)) {
            dragTarget = 1; dragStartX = mx; dragStartY = my; dragOffsetX = mx - buttonX; dragOffsetY = my - buttonY;
            isDragging = false; event.setCanceled(true); return;
        }

        if (!overlayOpen) return;
        Layout l = layout();

        if (activeMenu >= 0 && activeMenu < 3) {
            int menuX = l.btnX[activeMenu]; int menuY = l.btnY + l.btnH + 2;
            if (mx >= menuX && mx < menuX + l.menuW && my >= menuY && my < menuY + (ACTIONS_LIST.length * l.itemH)) {
                event.setCanceled(true); return;
            }
        }

        if (mx >= l.inputX && mx < l.inputX + l.inputW && my >= l.inputY && my < l.inputY + l.inputH) {
            activeInputField = 0; lastCharTime = 0; event.setCanceled(true); return;
        }

        for (int i = 0; i < 3; i++) {
            if (mx >= l.btnX[i] && mx < l.btnX[i] + l.btnW && my >= l.btnY && my < l.btnY + l.btnH) { event.setCanceled(true); return; }
        }

        if (mx >= calcX - 5 && mx < calcX + CALC_WIDTH && my >= calcY - 5 && my < calcY + CALC_HEIGHT) {
            dragTarget = 2; dragStartX = mx; dragStartY = my; dragOffsetX = mx - calcX; dragOffsetY = my - calcY;
            isDragging = false; event.setCanceled(true);
        }
    }

    // ==========================================
    // ЛОГИКА: Обработка кликов мыши (Отпускание кнопки)
    // ==========================================
    @SubscribeEvent
    public static void onMouseButtonReleased(ScreenEvent.MouseButtonReleased.Pre event) {
        Screen screen = event.getScreen();
        if (!(screen instanceof AbstractContainerScreen containerScreen)) return;

        String cls = screen.getClass().getName();
        if (!cls.contains("AnvilScreen") && !cls.contains("InventoryScreen")) return;
        if (event.getButton() != 0) return;

        int mx = (int) Math.round(event.getMouseX());
        int my = (int) Math.round(event.getMouseY());

        // Обработка отпускания маленькой кнопки оверлея
        if (dragTarget == 1) {
            if (isDragging) savePositions();
            else if (isOnMainButton(mx, my)) { overlayOpen = !overlayOpen; activeMenu = -1; activeInputField = -1; }
            dragTarget = 0; isDragging = false; event.setCanceled(true); return;
        }

        // Обработка перетаскивания самого окна калькулятора
        if (dragTarget == 2) {
            if (isDragging) {
                savePositions();
                dragTarget = 0; isDragging = false; event.setCanceled(true);
                return; // Выходим ТОЛЬКО если окно реально тащили мышкой
            }
            // Если это был обычный клик без перемещения - сбрасываем флаги
            dragTarget = 0; isDragging = false;
        }

        if (!overlayOpen) return;
        Layout l = layout();

        // Проверка клика по выпадающему списку
        if (activeMenu >= 0 && activeMenu < 3) {
            int menuX = l.btnX[activeMenu];
            int menuY = l.btnY + l.btnH + 2;

            if (mx >= menuX && mx < menuX + l.menuW && my >= menuY && my < menuY + (ACTIONS_LIST.length * l.itemH)) {
                int idx = (my - menuY) / l.itemH;
                if (idx >= 0 && idx < ACTIONS_LIST.length) {
                    String sel = ACTIONS_LIST[idx];
                    if (sel.equals("-")) sel = "";

                    if (activeMenu == 0) selectedAction1 = sel;
                    else if (activeMenu == 1) selectedAction2 = sel;
                    else selectedAction3 = sel;
                }
                activeMenu = -1;
                event.setCanceled(true);
                return;
            }

            activeMenu = -1;
            event.setCanceled(true);
            return;
        }

        // Перехват кликов по оригинальным кнопкам TFC наковальни
        String clickedAction = getActionAt(mx, my, containerScreen);
        if (clickedAction != null && !plannedActions.isEmpty() && currentStepIndex < plannedActions.size()) {
            String expectedAction = plannedActions.get(currentStepIndex);
            if (clickedAction.equals(expectedAction)) {
                triggerStepAdvance();
                System.out.println("[TfcAnvilHelper] Correct step! Next index: " + currentStepIndex);
            }
        }

        // Кнопка Backspace "←"
        if (mx >= l.bsX && mx < l.bsX + l.bsW && my >= l.bsY && my < l.bsY + l.bsH) {
            if (!targetNumber.isEmpty()) targetNumber = targetNumber.substring(0, targetNumber.length() - 1);
            event.setCanceled(true); return;
        }

        // Кнопки открытия подменю действий
        for (int i = 0; i < 3; i++) {
            int bx = l.btnX[i];
            if (mx >= bx && mx < bx + l.btnW && my >= l.btnY && my < l.btnY + l.btnH) {
                activeMenu = (activeMenu == i) ? -1 : i; activeInputField = -1; event.setCanceled(true); return;
            }
        }

        // Кнопка "Рассчитать"
        if (mx >= l.calcBtnX && mx < l.calcBtnX + l.calcBtnW && my >= l.calcBtnY && my < l.calcBtnY + l.calcBtnH) {
            performCalculation(); event.setCanceled(true);
        }
    }

    private static final List<String> plannedActions = new ArrayList<>();
    private static int currentStepIndex = 0;

    private static void performCalculation() {
        if (targetNumber.isEmpty()) {
            resultText = Component.translatable("msg.tfcanvilhelper.enter_target").getString();
            return;
        }
        int target;
        try {
            target = Integer.parseInt(targetNumber);
        } catch (Exception e) {
            resultText = Component.translatable("msg.tfcanvilhelper.invalid_number").getString();
            return;
        }

        List<String> lastThree = new ArrayList<>();
        if (!selectedAction1.isEmpty()) lastThree.add(selectedAction1);
        if (!selectedAction2.isEmpty()) lastThree.add(selectedAction2);
        if (!selectedAction3.isEmpty()) lastThree.add(selectedAction3);

        List<String> solution = AnvilCalculator.calculate(target, lastThree);
        if (solution.isEmpty()) {
            resultText = Component.translatable("msg.tfcanvilhelper.no_solution").getString();
            return;
        }

        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String action : solution) counts.put(action, counts.getOrDefault(action, 0) + 1);

        StringBuilder res = new StringBuilder();
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            if (res.length() > 0) res.append(", ");
            res.append(e.getValue()).append("x").append(e.getKey());
        }
        resultText = res.toString();

        plannedActions.clear();
        plannedActions.addAll(solution);
        currentStepIndex = 0;
        stepTransitionStart = 0L;
        oldToastDisplay = "";
        System.out.println("[TfcAnvilHelper] plannedActions = " + plannedActions);
    }
}
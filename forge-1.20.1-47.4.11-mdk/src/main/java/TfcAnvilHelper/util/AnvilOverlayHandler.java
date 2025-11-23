package TfcAnvilHelper.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.client.event.ScreenEvent;

import java.util.*;

@Mod.EventBusSubscriber(value = Dist.CLIENT)
public class AnvilOverlayHandler {
    private static boolean overlayOpen = false;
    private static boolean resultOpen = false;
    private static EditBox targetBox;
    private static Button[] stepButtons = new Button[3];
    private static Button calcButton;
    private static List<Button> actionMenu = new ArrayList<>();
    private static String[] lastSteps = new String[] { "", "", "" };
    private static List<Integer> resultActions = new ArrayList<>();
    private static Button closeResultButton;
    private static int menuSlot = -1;

    // Для сортировки
    private static final Comparator<Map.Entry<String, Integer>> actionOrder =
            Comparator.comparingInt((Map.Entry<String, Integer> e) -> Math.abs(e.getValue()))
                    .reversed()
                    .thenComparing(e -> e.getValue());

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        Screen screen = event.getScreen();
        if (!(screen instanceof AbstractContainerScreen<?> container)) return;
        if (!screen.getClass().getName().endsWith("AnvilScreen")) return;

        Minecraft mc = Minecraft.getInstance();

        int left = container.getGuiLeft();
        int top = container.getGuiTop();

        // Кнопка: впритык к окну
        Button toggleButton = Button.builder(
                net.minecraft.network.chat.Component.literal("⧉"),
                btn -> {
                    overlayOpen = !overlayOpen;
                    resultOpen = false;
                    resultActions.clear();
                    screen.init(mc, screen.width, screen.height);
                }
        ).bounds(left - 20, top + 10, 16, 18).build();
        event.addListener(toggleButton);

        if (!overlayOpen) return;

        int calcX = left - 58;
        int calcY = top + 10;

        targetBox = new EditBox(mc.font, calcX, calcY, 44, 16,
                net.minecraft.network.chat.Component.literal(""));
        event.addListener(targetBox);

        for (int i = 0; i < 3; i++) {
            int yBtn = calcY + 22 + i * 20;
            int finalI = i;
            stepButtons[i] = Button.builder(
                    net.minecraft.network.chat.Component.literal("[выбрать]"),
                    btn -> toggleActionMenu(event, calcX - 70, yBtn, finalI) // меню выпадает левее, не налазиет
            ).bounds(calcX, yBtn, 44, 16).build();
            event.addListener(stepButtons[i]);
        }

        calcButton = Button.builder(
                net.minecraft.network.chat.Component.literal("OK"),
                btn -> {
                    try {
                        int target = Integer.parseInt(targetBox.getValue());
                        List<String> stepList = Arrays.asList(lastSteps);
                        List<String> actions = AnvilCalculator.calculate(target, stepList);
                        resultActions.clear();
                        for (String action : actions) {
                            Integer val = AnvilCalculator.ACTIONS.getOrDefault(action, 0);
                            resultActions.add(val);
                        }
                        resultOpen = true;
                        screen.init(mc, screen.width, screen.height);
                    } catch (Exception e) {
                        resultActions.clear();
                        resultOpen = true;
                        screen.init(mc, screen.width, screen.height);
                    }
                }
        ).bounds(calcX, calcY + 90, 44, 16).build();
        event.addListener(calcButton);

        // Кнопка "закрыть" результат — справа снизу
        if (resultOpen) {
            int boxW = 20, boxH = 14, padding = 2;
            int resultCount = resultActions.size();
            int windowW = Math.max(240, resultCount * (boxW + padding) + 26); // увеличили ширину
            int resX = left;
            int resY = top + container.getYSize() + 12;
            closeResultButton = Button.builder(
                    net.minecraft.network.chat.Component.literal("✖"),
                    btn -> {
                        resultOpen = false;
                        screen.init(mc, screen.width, screen.height);
                    }
            ).bounds(resX + windowW - 22, resY + boxH + 26, 14, 12).build();
            event.addListener(closeResultButton);
        }
    }

    private static void toggleActionMenu(ScreenEvent.Init.Post event, int x, int y, int slotIdx) {
        if (menuSlot == slotIdx && !actionMenu.isEmpty()) {
            closeActionMenu(event);
            menuSlot = -1;
        } else {
            menuSlot = slotIdx;
            closeActionMenu(event);
            List<Map.Entry<String, Integer>> sortedActions = new ArrayList<>(AnvilCalculator.ACTIONS.entrySet());
            sortedActions.sort(actionOrder);

            int i = 0;
            for (Map.Entry<String, Integer> entry : sortedActions) {
                Button actionBtn = Button.builder(
                        net.minecraft.network.chat.Component.literal(entry.getKey() + " (" + entry.getValue() + ")"),
                        btn -> {
                            stepButtons[slotIdx].setMessage(net.minecraft.network.chat.Component.literal(entry.getKey()));
                            lastSteps[slotIdx] = entry.getKey();
                            closeActionMenu(event);
                            menuSlot = -1;
                        }
                ).bounds(x, y + i * 16, 80, 15).build(); // шире для полного названия
                event.addListener(actionBtn);
                actionMenu.add(actionBtn);
                i++;
            }
        }
    }

    private static void closeActionMenu(ScreenEvent.Init.Post event) {
        for (Button btn : actionMenu) event.removeListener(btn);
        actionMenu.clear();
    }

    @SubscribeEvent
    public static void onScreenRender(ScreenEvent.Render.Post event) {
        Screen screen = event.getScreen();
        if (!(screen instanceof AbstractContainerScreen<?> container)) return;
        if (!screen.getClass().getName().endsWith("AnvilScreen")) return;
        if (!overlayOpen) return;

        int left = container.getGuiLeft();
        int top = container.getGuiTop();

        int calcX = left - 58;
        int calcY = top + 10;
        GuiGraphics guiGraphics = event.getGuiGraphics();

        guiGraphics.fill(calcX - 5, calcY - 5, calcX + 56, calcY + 112, 0xBB111111);

        // Нижняя полоска результата
        if (resultOpen) {
            int boxW = 20, boxH = 14, padding = 2;
            int resultCount = resultActions.size();
            int windowW = Math.max(240, resultCount * (boxW + padding) + 26); // новая ширина!
            int resX = left;
            int resY = top + container.getYSize() + 12;

            guiGraphics.fill(resX, resY, resX + windowW, resY + boxH + 26, 0xCC444444);
            guiGraphics.drawString(Minecraft.getInstance().font, "Решение:", resX + 8, resY + 7, 0xFFFFFF);

            for (int i = 0; i < resultCount; i++) {
                int val = resultActions.get(i);
                int boxX = resX + 14 + i * (boxW + padding);
                int boxY = resY + 22;
                guiGraphics.fill(boxX, boxY, boxX + boxW, boxY + boxH, 0xFF888888);
                guiGraphics.drawString(Minecraft.getInstance().font,
                        String.valueOf(val), boxX + 3, boxY + 2, 0xFFFFFF);
            }
        }
    }
}

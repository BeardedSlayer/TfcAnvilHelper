package TfcAnvilHelper;

import java.util.*;

public class AnvilCalculator {
    // Карта действий и их значений TFC
    public static final Map<String, Integer> ACTIONS = Map.of(
            "0", 0,
            "+2", 2,
            "+7", 7,
            "+13", 13,
            "+16", 16,
            "-3", -3,
            "-6", -6,
            "-9", -9,
            "-15", -15
    );

    /**
     * Поиск быстрой последовательности действий для достижения целевого числа
     * @param target - итоговое число
     * @param lastThree - последние 3 действия (названия)
     * @return список всех шагов для полного решения (основные + финальные)
     */
    public static List<String> calculate(int target, List<String> lastThree) {
        int lastSum = 0;
        for (String s : lastThree) lastSum += ACTIONS.getOrDefault(s, 0);
        int needed = target - lastSum;

        // Все положительные действия, отсортированы по убыванию (16, 13, 7, 2)
        List<Map.Entry<String, Integer>> positiveActions = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : ACTIONS.entrySet()) {
            if (entry.getValue() > 0) positiveActions.add(entry);
        }
        positiveActions.sort((a, b) -> b.getValue() - a.getValue()); // максимальные в начале

        // DP: для каждой суммы храним минимальный вариант пути
        Map<Integer, List<String>> paths = new HashMap<>();
        paths.put(0, new ArrayList<>());

        Set<Integer> currentSums = new HashSet<>();
        currentSums.add(0);

        int maxIter = 10;
        int iter = 0;

        while (iter < maxIter && !paths.containsKey(needed)) {
            iter++;
            Set<Integer> newSums = new HashSet<>();

            for (int curSum : currentSums) {
                for (Map.Entry<String, Integer> act : positiveActions) {
                    int newSum = curSum + act.getValue();
                    if (newSum <= needed + 5) { // небольшой запас для корректности
                        List<String> newPath = new ArrayList<>(paths.get(curSum));
                        newPath.add(act.getKey());
                        if (!paths.containsKey(newSum) || paths.get(newSum).size() > newPath.size()) {
                            paths.put(newSum, newPath);
                            newSums.add(newSum);
                        }
                    }
                }
            }

            if (newSums.isEmpty()) break;
            currentSums = newSums;
        }

        if (paths.containsKey(needed)) {
            List<String> actions = new ArrayList<>(paths.get(needed));
            actions.addAll(lastThree); // добавляем финальные шаги
            return actions;
        } else {
            return Collections.emptyList();
        }
    }
}

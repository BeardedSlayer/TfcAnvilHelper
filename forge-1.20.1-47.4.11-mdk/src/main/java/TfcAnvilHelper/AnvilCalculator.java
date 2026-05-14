package TfcAnvilHelper;

import java.util.*;

public class AnvilCalculator {
    public static final Map<String, Integer> ACTIONS = Map.of(
            "0", 0,
            "+2", 2, "+7", 7, "+13", 13, "+16", 16,
            "-3", -3, "-6", -6, "-9", -9, "-15", -15
    );

    public static List<String> calculate(int target, List<String> lastThree) {
        int lastSum = 0;
        for (String s : lastThree) {
            lastSum += ACTIONS.getOrDefault(s, 0);
        }
        int needed = target - lastSum;

        if (needed == 0) return new ArrayList<>(lastThree);

        Queue<Integer> queue = new LinkedList<>();
        Map<Integer, Integer> parent = new HashMap<>();
        Map<Integer, String> actionTaken = new HashMap<>();

        queue.add(0);
        parent.put(0, null);

        boolean found = false;
        while (!queue.isEmpty()) {
            int current = queue.poll();
            if (current == needed) {
                found = true;
                break;
            }

            for (Map.Entry<String, Integer> entry : ACTIONS.entrySet()) {
                int next = current + entry.getValue();
                if (next >= -20 && next <= 160 && !parent.containsKey(next)) {
                    parent.put(next, current);
                    actionTaken.put(next, entry.getKey());
                    queue.add(next);
                }
            }
        }

        if (found) {
            List<String> result = new ArrayList<>();
            Integer step = needed;
            while (step != null && parent.get(step) != null) {
                result.add(actionTaken.get(step));
                step = parent.get(step);
            }
            Collections.reverse(result);
            result.addAll(lastThree);
            return result;
        }
        return Collections.emptyList();
    }
}
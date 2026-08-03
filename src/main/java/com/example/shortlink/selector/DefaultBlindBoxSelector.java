package com.example.shortlink.selector;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.stereotype.Component;

@Component
public class DefaultBlindBoxSelector implements BlindBoxSelector {

    /**
     * 只选择候选下标，不扣减次数，也不修改候选列表。
     */
    @Override
    public String select(List<String> candidates) {
        Objects.requireNonNull(candidates, "candidates must not be null");
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("candidates must not be empty");
        }

        // 每个下标被选中的概率相同；选择器不扣减次数，也不修改调用方持有的列表。
        int selectedIndex = ThreadLocalRandom.current().nextInt(candidates.size());
        return candidates.get(selectedIndex);
    }
}

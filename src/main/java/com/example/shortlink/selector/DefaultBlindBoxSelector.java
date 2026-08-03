package com.example.shortlink.selector;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.stereotype.Component;

@Component
public class DefaultBlindBoxSelector implements BlindBoxSelector {

    @Override
    public String select(List<String> candidates) {
        Objects.requireNonNull(candidates, "candidates must not be null");
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("candidates must not be empty");
        }

        // Every index has the same probability; the selector does not consume
        // times or mutate the caller-owned list.
        int selectedIndex = ThreadLocalRandom.current().nextInt(candidates.size());
        return candidates.get(selectedIndex);
    }
}

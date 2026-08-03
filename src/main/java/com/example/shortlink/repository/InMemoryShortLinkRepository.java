package com.example.shortlink.repository;

import com.example.shortlink.domain.ShortLink;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryShortLinkRepository implements ShortLinkRepository {

    private final ConcurrentHashMap<String, ShortLink> linkStore = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> normalUrlIndex = new ConcurrentHashMap<>();

    @Override
    public Optional<ShortLink> findByShortCode(String shortCode) {
        return Optional.ofNullable(linkStore.get(Objects.requireNonNull(shortCode, "shortCode must not be null")));
    }

    @Override
    public boolean saveIfAbsent(String shortCode, ShortLink shortLink) {
        Objects.requireNonNull(shortCode, "shortCode must not be null");
        Objects.requireNonNull(shortLink, "shortLink must not be null");

        // putIfAbsent combines the existence check and insertion atomically;
        // containsKey followed by put would allow concurrent overwrites.
        return linkStore.putIfAbsent(shortCode, shortLink) == null;
    }

    @Override
    public Collection<ShortLink> findAll() {
        return List.copyOf(new ArrayList<>(linkStore.values()));
    }

    @Override
    public Optional<String> findNormalCodeByBusinessKey(String businessKey) {
        return Optional.ofNullable(normalUrlIndex.get(
                Objects.requireNonNull(businessKey, "businessKey must not be null")));
    }

    @Override
    public String computeNormalCodeIfAbsent(
            String businessKey,
            Function<String, String> mappingFunction) {
        Objects.requireNonNull(businessKey, "businessKey must not be null");
        Objects.requireNonNull(mappingFunction, "mappingFunction must not be null");

        // ConcurrentHashMap guarantees one atomic mapping decision per key;
        // the mapping function must remain side-effect free for this index.
        return normalUrlIndex.computeIfAbsent(businessKey, mappingFunction);
    }
}

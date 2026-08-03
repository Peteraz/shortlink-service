package com.example.shortlink.repository;

import com.example.shortlink.domain.ShortLink;

import java.util.Collection;
import java.util.Optional;
import java.util.function.Function;

public interface ShortLinkRepository {

    Optional<ShortLink> findByShortCode(String shortCode);

    boolean saveIfAbsent(String shortCode, ShortLink shortLink);

    Collection<ShortLink> findAll();

    Optional<String> findNormalCodeByBusinessKey(String businessKey);

    String computeNormalCodeIfAbsent(String businessKey, Function<String, String> mappingFunction);
}

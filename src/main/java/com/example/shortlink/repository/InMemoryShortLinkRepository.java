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

    /**
     * 按短码保存短链领域对象的内存索引。
     */
    private final ConcurrentHashMap<String, ShortLink> linkStore = new ConcurrentHashMap<>();
    /**
     * 按普通短链业务键保存短码的幂等索引。
     */
    private final ConcurrentHashMap<String, String> normalUrlIndex = new ConcurrentHashMap<>();

    @Override
    public Optional<ShortLink> findByShortCode(String shortCode) {
        return Optional.ofNullable(linkStore.get(Objects.requireNonNull(shortCode, "shortCode must not be null")));
    }

    /**
     * 以 putIfAbsent 原子保留全局短码，碰撞由 Service 负责重试。
     */
    @Override
    public boolean saveIfAbsent(String shortCode, ShortLink shortLink) {
        Objects.requireNonNull(shortCode, "shortCode must not be null");
        Objects.requireNonNull(shortLink, "shortLink must not be null");

        // putIfAbsent 将存在性检查和写入合并为原子操作；
        // containsKey 后再 put 会在并发场景下允许覆盖写入。
        return linkStore.putIfAbsent(shortCode, shortLink) == null;
    }

    /**
     * 返回快照集合，避免调用方直接持有内部 Map 的 Collection 视图。
     */
    @Override
    public Collection<ShortLink> findAll() {
        return List.copyOf(new ArrayList<>(linkStore.values()));
    }

    @Override
    public Optional<String> findNormalCodeByBusinessKey(String businessKey) {
        return Optional.ofNullable(normalUrlIndex.get(Objects.requireNonNull(businessKey, "businessKey must not be null")));
    }

    /**
     * 获取业务键对应的短码。
     * 如果不存在，则创建短链并保存短码与业务键的映射。
     */
    @Override
    public String computeNormalCodeIfAbsent(String businessKey, Function<String, String> mappingFunction) {
        Objects.requireNonNull(businessKey, "businessKey must not be null");
        Objects.requireNonNull(mappingFunction, "mappingFunction must not be null");

        // 同一个 businessKey 并发创建时，只允许一个线程执行创建逻辑。
        return normalUrlIndex.computeIfAbsent(businessKey, mappingFunction);
    }
}

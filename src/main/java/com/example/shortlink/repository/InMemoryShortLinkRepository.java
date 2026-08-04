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
     * 短码到短链对象的索引：key 是短码，value 是对应的 ShortLink 对象。
     */
    private final ConcurrentHashMap<String, ShortLink> linkStore = new ConcurrentHashMap<>();
    /**
     * 普通短链业务键到短码的索引：key 是“规范化 URL + 渠道”，value 是已生成的短码。
     * 用于让相同的普通短链创建请求复用已有结果。
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

        // putIfAbsent 会一次完成“未占用才写入”；先检查再写入会留下并发覆盖的空档。
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

        // 相同业务键并发创建时，只有一个线程会创建并保存短链。
        return normalUrlIndex.computeIfAbsent(businessKey, mappingFunction);
    }
}

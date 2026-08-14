package com.example.shortlink.service;

import com.example.shortlink.domain.LinkType;
import com.example.shortlink.domain.ShortLink;
import com.example.shortlink.dto.request.CreateBlindBoxLinkRequest;
import com.example.shortlink.dto.request.CreateNormalLinkRequest;
import com.example.shortlink.dto.request.ShortLinkQuery;
import com.example.shortlink.dto.response.PageResponse;
import com.example.shortlink.dto.response.ResolveResponse;
import com.example.shortlink.dto.response.ShortLinkResponse;
import com.example.shortlink.exception.BlindBoxDuplicateUrlException;
import com.example.shortlink.exception.BlindBoxUrlInsufficientException;
import com.example.shortlink.exception.BusinessException;
import com.example.shortlink.exception.ShortCodeGenerationException;
import com.example.shortlink.exception.ShortLinkNotFoundException;
import com.example.shortlink.generator.ShortCodeGenerator;
import com.example.shortlink.mapper.ShortLinkMapper;
import com.example.shortlink.repository.ShortLinkRepository;
import com.example.shortlink.selector.BlindBoxSelector;
import com.example.shortlink.selector.DefaultBlindBoxSelector;
import com.example.shortlink.validator.ChannelNormalizer;
import com.example.shortlink.validator.ShortCodeValidator;
import com.example.shortlink.validator.UrlValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
public class ShortLinkServiceImpl implements ShortLinkService {

    /**
     * 短码生成发生碰撞时允许的最大重试次数。
     */
    private static final int MAX_GENERATION_ATTEMPTS = 10;
    /**
     * 盲盒候选 URL 的最小数量。
     */
    private static final int MIN_BLIND_BOX_URLS = 2;
    /**
     * 盲盒候选 URL 的最大数量。同步健康检测会遍历候选 URL，
     * 因此限制规模以控制创建成本和最坏探测时延。
     */
    private static final int MAX_BLIND_BOX_URLS = 10;
    /**
     * 盲盒有效次数的最大值。
     */
    private static final int MAX_VALID_TIMES = 1_000_000;

    /**
     * 短链内存仓储。
     */
    private final ShortLinkRepository repository;
    /**
     * 短码生成器。
     */
    private final ShortCodeGenerator shortCodeGenerator;
    /**
     * URL 校验和规范化器。
     */
    private final UrlValidator urlValidator;
    /**
     * 渠道规范化器。
     */
    private final ChannelNormalizer channelNormalizer;
    /**
     * 普通短链业务唯一键生成器。
     */
    private final NormalLinkBusinessKeyFactory businessKeyFactory;
    /**
     * 领域对象到响应对象的转换器。
     */
    private final ShortLinkMapper mapper;
    /**
     * 短码格式校验器。
     */
    private final ShortCodeValidator shortCodeValidator;
    /**
     * 盲盒候选 URL 选择器。
     */
    private final BlindBoxSelector blindBoxSelector;
    /**
     * 统一时间来源，便于测试固定时间。
     */
    private final Clock clock;
    /**
     * 集中管理短链状态规则。
     */
    private final LinkStatusPolicy linkStatusPolicy;

    /**
     * 保留原有构造方式，兼容只使用普通短链的单元测试和调用方。
     */
    public ShortLinkServiceImpl(
            ShortLinkRepository repository,
            ShortCodeGenerator shortCodeGenerator,
            UrlValidator urlValidator,
            ChannelNormalizer channelNormalizer,
            NormalLinkBusinessKeyFactory businessKeyFactory,
            ShortLinkMapper mapper,
            ShortCodeValidator shortCodeValidator,
            Clock clock) {
        this(
                repository,
                shortCodeGenerator,
                urlValidator,
                channelNormalizer,
                businessKeyFactory,
                mapper,
                shortCodeValidator,
                new DefaultBlindBoxSelector(),
                clock);
    }

    @Autowired
    public ShortLinkServiceImpl(
            ShortLinkRepository repository,
            ShortCodeGenerator shortCodeGenerator,
            UrlValidator urlValidator,
            ChannelNormalizer channelNormalizer,
            NormalLinkBusinessKeyFactory businessKeyFactory,
            ShortLinkMapper mapper,
            ShortCodeValidator shortCodeValidator,
            BlindBoxSelector blindBoxSelector,
            Clock clock) {
        this.repository = repository;
        this.shortCodeGenerator = shortCodeGenerator;
        this.urlValidator = urlValidator;
        this.channelNormalizer = channelNormalizer;
        this.businessKeyFactory = businessKeyFactory;
        this.mapper = mapper;
        this.shortCodeValidator = shortCodeValidator;
        this.blindBoxSelector = Objects.requireNonNull(blindBoxSelector, "blindBoxSelector must not be null");
        this.clock = clock;
        this.linkStatusPolicy = new LinkStatusPolicy();
    }

    /**
     * 创建普通短链；相同 URL 和渠道重复提交时返回已有短链。
     */
    @Override
    public ShortLinkResponse createNormalLink(CreateNormalLinkRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        String normalizedUrl = urlValidator.validateAndNormalize(request.getOriginalUrl());
        String normalizedChannel = channelNormalizer.normalize(request.getChannel());
        String businessKey = businessKeyFactory.create(normalizedUrl, normalizedChannel);

        String existingCode = repository.findNormalCodeByBusinessKey(businessKey).orElse(null);
        if (existingCode != null) {
            return getByShortCode(existingCode);
        }

        String shortCode = repository.computeNormalCodeIfAbsent(businessKey, ignored -> createAndStore(normalizedUrl, normalizedChannel));
        return getByShortCode(shortCode);
    }

    /**
     * 盲盒短链不复用普通短链的幂等结果；每次创建都会生成一条新的短链。
     */
    @Override
    public ShortLinkResponse createBlindBoxLink(CreateBlindBoxLinkRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        List<String> normalizedUrls = normalizeBlindBoxUrls(request.getOriginalUrls());
        int validTimes = validateValidTimes(request.getValidTimes());
        String normalizedChannel = channelNormalizer.normalize(request.getChannel());

        String shortCode = createAndStoreBlindBox(normalizedUrls, normalizedChannel, validTimes);
        return getByShortCode(shortCode);
    }

    /**
     * 只读取详情，不执行解析，因此不会增加访问次数或消耗盲盒次数。
     */
    @Override
    public ShortLinkResponse getByShortCode(String shortCode) {
        shortCodeValidator.validate(shortCode);
        return mapper.toResponse(findByShortCode(shortCode));
    }

    /**
     * 根据短链类型执行对应的解析流程。
     * 盲盒必须先成功消耗一次有效次数，才会返回目标 URL。
     */
    @Override
    public ResolveResponse resolve(String shortCode) {
        shortCodeValidator.validate(shortCode);
        ShortLink shortLink = findByShortCode(shortCode);

        if (shortLink.getType() == LinkType.NORMAL) {
            return resolveNormal(shortLink);
        }
        return resolveBlindBox(shortLink);
    }

    private ResolveResponse resolveNormal(ShortLink shortLink) {
        return shortLink.withStateLock(() -> {
            // 状态检查、读取目标和增加次数必须连续完成，期间不能被断链操作打断。
            linkStatusPolicy.ensureResolvable(shortLink);

            String targetUrl = shortLink.getOriginalUrls().getFirst();
            shortLink.incrementResolveCount();
            return mapper.toResolveResponse(shortLink, targetUrl);
        });
    }

    private ResolveResponse resolveBlindBox(ShortLink shortLink) {
        return shortLink.withStateLock(() -> {
            // 先确认状态，再扣减次数；两步必须连续完成。
            linkStatusPolicy.ensureResolvable(shortLink);

            // 只有成功扣减次数的请求，才获得本次解析资格。
            if (!shortLink.tryConsume()) {
                throw linkStatusPolicy.exhausted(shortLink);
            }

            String targetUrl = blindBoxSelector.select(shortLink.getOriginalUrls());
            shortLink.incrementResolveCount();
            return mapper.toResolveResponse(shortLink, targetUrl);
        });
    }

    /**
     * 重复标记已断链的短链时，保留第一次的断链原因并直接返回。
     */
    @Override
    public ShortLinkResponse markBroken(String shortCode, String reason) {
        shortCodeValidator.validate(shortCode);
        String normalizedReason = normalizeBrokenReason(reason);
        ShortLink shortLink = findByShortCode(shortCode);

        return shortLink.withStateLock(() -> {
            // 已断链时不覆盖第一次记录的原因，重复请求返回已有结果。
            if (linkStatusPolicy.isBroken(shortLink)) {
                return mapper.toResponse(shortLink);
            }
            shortLink.markBroken(normalizedReason);
            return mapper.toResponse(shortLink);
        });
    }

    /**
     * 分页查询短链列表
     */
    @Override
    public PageResponse<ShortLinkResponse> query(ShortLinkQuery query) {
        Objects.requireNonNull(query, "query must not be null");
        int page = validatePage(query.getPage());
        int size = validateSize(query.getSize());
        if (query.getShortCode() != null) {
            shortCodeValidator.validate(query.getShortCode());
        }
        String normalizedChannel = query.getChannel() == null ? null : channelNormalizer.normalize(query.getChannel());

        List<ShortLink> matchingLinks = repository.findAll().stream()
                .filter(shortLink -> query.getShortCode() == null
                        || shortLink.getShortCode().equals(query.getShortCode()))
                .filter(shortLink -> normalizedChannel == null
                        || shortLink.getChannel().equals(normalizedChannel))
                .filter(shortLink -> query.getStatus() == null
                        || shortLink.getStatus() == query.getStatus())
                .filter(shortLink -> query.getType() == null
                        || shortLink.getType() == query.getType())
                .sorted(Comparator.comparing(ShortLink::getCreatedAt)
                        .reversed()
                        .thenComparing(ShortLink::getShortCode))
                .toList();

        long totalElements = matchingLinks.size();
        long offset = (long) page * size;
        int fromIndex = Math.toIntExact(Math.min(offset, totalElements));
        int toIndex = Math.toIntExact(Math.min(offset + size, totalElements));
        List<ShortLinkResponse> content = matchingLinks.subList(fromIndex, toIndex).stream().map(mapper::toResponse).toList();
        int totalPages = Math.toIntExact((totalElements + size - 1) / size);

        return new PageResponse<>(content, page, size, totalElements, totalPages);
    }

    /**
     * 创建普通短链时，如果生成的短码已存在则重新生成。
     * 只有仓储确认短码尚未被占用后，才返回该短码。
     */
    private String createAndStore(String normalizedUrl, String normalizedChannel) {
        for (int attempt = 1; attempt <= MAX_GENERATION_ATTEMPTS; attempt++) {
            String candidateCode = shortCodeGenerator.generate();
            ShortLink shortLink = ShortLink.normal(candidateCode, normalizedUrl, normalizedChannel, LocalDateTime.now(clock));

            // Repository 原子保留全局短码；返回 false 表示候选短码已被其他短链占用，
            // 因此使用新的候选短码继续重试。
            if (repository.saveIfAbsent(candidateCode, shortLink)) {
                return candidateCode;
            }
        }
        throw new ShortCodeGenerationException("failed to generate a unique short code after " + MAX_GENERATION_ATTEMPTS + " attempts");
    }

    /**
     * 盲盒每次创建都生成新短码，但仍使用相同的碰撞重试上限。
     */
    private String createAndStoreBlindBox(List<String> normalizedUrls, String normalizedChannel, int validTimes) {
        // 短码冲突时重新生成，最多重试指定次数，避免无限循环。
        for (int attempt = 1; attempt <= MAX_GENERATION_ATTEMPTS; attempt++) {
            String candidateCode = shortCodeGenerator.generate();
            ShortLink shortLink = ShortLink.blindBox(
                    candidateCode,
                    normalizedUrls,
                    normalizedChannel,
                    LocalDateTime.now(clock),
                    validTimes);

            if (repository.saveIfAbsent(candidateCode, shortLink)) {
                return candidateCode;
            }
        }
        throw new ShortCodeGenerationException("failed to generate a unique short code after " + MAX_GENERATION_ATTEMPTS + " attempts");
    }

    /**
     * 先做数量校验，再规范化并用规范化 URL 检测列表内重复项。
     */
    private List<String> normalizeBlindBoxUrls(List<String> originalUrls) {
        if (originalUrls == null || originalUrls.size() < MIN_BLIND_BOX_URLS) {
            throw new BlindBoxUrlInsufficientException("blind-box must contain at least " + MIN_BLIND_BOX_URLS + " URLs");
        }
        if (originalUrls.size() > MAX_BLIND_BOX_URLS) {
            throw new BusinessException("BLIND_BOX_URL_LIMIT_EXCEEDED", "blind-box must contain at most " + MAX_BLIND_BOX_URLS + " URLs");
        }

        Set<String> seenUrls = new HashSet<>();
        List<String> normalizedUrls = new ArrayList<>(originalUrls.size());
        for (String originalUrl : originalUrls) {
            String normalizedUrl = urlValidator.validateAndNormalize(originalUrl);
            // 比较规范化后的 URL；例如仅 host 大小写不同的两个 URL 也视为重复。
            if (!seenUrls.add(normalizedUrl)) {
                throw new BlindBoxDuplicateUrlException("blind-box original URLs must not contain duplicates");
            }
            normalizedUrls.add(normalizedUrl);
        }
        return List.copyOf(normalizedUrls);
    }

    private int validateValidTimes(Integer validTimes) {
        if (validTimes == null || validTimes < 1 || validTimes > MAX_VALID_TIMES) {
            throw new BusinessException("INVALID_VALID_TIMES", "validTimes must be between 1 and " + MAX_VALID_TIMES);
        }
        return validTimes;
    }

    private ShortLink findByShortCode(String shortCode) {
        return repository.findByShortCode(shortCode).orElseThrow(() -> new ShortLinkNotFoundException("short link not found: " + shortCode));
    }

    private int validatePage(int page) {
        if (page < 0) {
            throw new BusinessException("INVALID_PAGE", "page must be at least 0");
        }
        return page;
    }

    private int validateSize(int size) {
        if (size < 1 || size > 100) {
            throw new BusinessException("INVALID_PAGE_SIZE", "size must be between 1 and 100");
        }
        return size;
    }

    private String normalizeBrokenReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessException("INVALID_BROKEN_REASON", "reason must not be blank");
        }
        String normalizedReason = reason.trim();
        if (normalizedReason.length() > 200) {
            throw new BusinessException("INVALID_BROKEN_REASON", "reason must not exceed 200 characters");
        }
        return normalizedReason;
    }
}

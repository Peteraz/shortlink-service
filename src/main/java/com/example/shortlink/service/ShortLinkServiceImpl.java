package com.example.shortlink.service;

import com.example.shortlink.domain.LinkType;
import com.example.shortlink.domain.ShortLink;
import com.example.shortlink.dto.request.CreateBlindBoxLinkRequest;
import com.example.shortlink.dto.request.CreateNormalLinkRequest;
import com.example.shortlink.dto.request.ShortLinkQuery;
import com.example.shortlink.dto.response.PageResponse;
import com.example.shortlink.dto.response.ResolveResult;
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

    private static final int MAX_GENERATION_ATTEMPTS = 10;
    private static final int MIN_BLIND_BOX_URLS = 2;
    private static final int MAX_BLIND_BOX_URLS = 100;
    private static final int MAX_VALID_TIMES = 1_000_000;

    private final ShortLinkRepository repository;
    private final ShortCodeGenerator shortCodeGenerator;
    private final UrlValidator urlValidator;
    private final ChannelNormalizer channelNormalizer;
    private final NormalLinkBusinessKeyFactory businessKeyFactory;
    private final ShortLinkMapper mapper;
    private final ShortCodeValidator shortCodeValidator;
    private final BlindBoxSelector blindBoxSelector;
    private final Clock clock;
    private final LinkStatusPolicy linkStatusPolicy;

    /**
     * Keeps the existing construction contract for unit tests and callers that
     * only use normal links.
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

    @Override
    public ShortLinkResponse createNormalLink(CreateNormalLinkRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        String normalizedUrl = urlValidator.validateAndNormalize(request.originalUrl());
        String normalizedChannel = channelNormalizer.normalize(request.channel());
        String businessKey = businessKeyFactory.create(normalizedUrl, normalizedChannel);

        String existingCode = repository.findNormalCodeByBusinessKey(businessKey).orElse(null);
        if (existingCode != null) {
            return getByShortCode(existingCode);
        }

        String shortCode = repository.computeNormalCodeIfAbsent(
                businessKey,
                ignored -> createAndStore(normalizedUrl, normalizedChannel));
        return getByShortCode(shortCode);
    }

    @Override
    public ShortLinkResponse createBlindBoxLink(CreateBlindBoxLinkRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        List<String> normalizedUrls = normalizeBlindBoxUrls(request.originalUrls());
        int validTimes = validateValidTimes(request.validTimes());
        String normalizedChannel = channelNormalizer.normalize(request.channel());

        String shortCode = createAndStoreBlindBox(normalizedUrls, normalizedChannel, validTimes);
        return getByShortCode(shortCode);
    }

    @Override
    public ShortLinkResponse getByShortCode(String shortCode) {
        shortCodeValidator.validate(shortCode);
        return mapper.toResponse(findByShortCode(shortCode));
    }

    @Override
    public ResolveResult resolve(String shortCode) {
        shortCodeValidator.validate(shortCode);
        ShortLink shortLink = findByShortCode(shortCode);

        if (shortLink.getType() == LinkType.NORMAL) {
            return resolveNormal(shortLink);
        }
        return resolveBlindBox(shortLink);
    }

    private ResolveResult resolveNormal(ShortLink shortLink) {
        linkStatusPolicy.ensureNormalResolvable(shortLink);

        String targetUrl = shortLink.getOriginalUrls().getFirst();
        shortLink.getResolveCount().incrementAndGet();
        return mapper.toResolveResult(shortLink, targetUrl);
    }

    private ResolveResult resolveBlindBox(ShortLink shortLink) {
        linkStatusPolicy.ensureBlindNotBroken(shortLink);

        // The CAS result is the authority. Checking status alone would allow
        // concurrent callers to resolve after the last valid time was spent.
        if (!shortLink.tryConsume()) {
            shortLink.markExhausted();
            throw linkStatusPolicy.exhausted(shortLink);
        }

        // Select only after a successful CAS, so every returned target has
        // consumed exactly one valid time.
        String targetUrl = blindBoxSelector.select(shortLink.getOriginalUrls());
        shortLink.getResolveCount().incrementAndGet();
        return mapper.toResolveResult(shortLink, targetUrl);
    }

    @Override
    public ShortLinkResponse markBroken(String shortCode, String reason) {
        shortCodeValidator.validate(shortCode);
        String normalizedReason = normalizeBrokenReason(reason);
        ShortLink shortLink = findByShortCode(shortCode);

        if (linkStatusPolicy.isBroken(shortLink)) {
            return mapper.toResponse(shortLink);
        }
        linkStatusPolicy.ensureCanMarkBroken(shortLink);
        shortLink.markBroken(normalizedReason);
        return mapper.toResponse(shortLink);
    }

    @Override
    public PageResponse<ShortLinkResponse> query(ShortLinkQuery query) {
        Objects.requireNonNull(query, "query must not be null");
        int page = validatePage(query.page());
        int size = validateSize(query.size());
        if (query.shortCode() != null) {
            shortCodeValidator.validate(query.shortCode());
        }
        String normalizedChannel = query.channel() == null
                ? null
                : channelNormalizer.normalize(query.channel());

        List<ShortLink> matchingLinks = repository.findAll().stream()
                .filter(shortLink -> query.shortCode() == null
                        || shortLink.getShortCode().equals(query.shortCode()))
                .filter(shortLink -> normalizedChannel == null
                        || shortLink.getChannel().equals(normalizedChannel))
                .filter(shortLink -> query.status() == null
                        || shortLink.getStatus() == query.status())
                .filter(shortLink -> query.type() == null
                        || shortLink.getType() == query.type())
                .sorted(Comparator.comparing(ShortLink::getCreatedAt)
                        .reversed()
                        .thenComparing(ShortLink::getShortCode))
                .toList();

        long totalElements = matchingLinks.size();
        long offset = (long) page * size;
        int fromIndex = Math.toIntExact(Math.min(offset, totalElements));
        int toIndex = Math.toIntExact(Math.min(offset + size, totalElements));
        List<ShortLinkResponse> content = matchingLinks.subList(fromIndex, toIndex).stream()
                .map(mapper::toResponse)
                .toList();
        int totalPages = Math.toIntExact((totalElements + size - 1) / size);

        return new PageResponse<>(content, page, size, totalElements, totalPages);
    }

    private String createAndStore(String normalizedUrl, String normalizedChannel) {
        for (int attempt = 1; attempt <= MAX_GENERATION_ATTEMPTS; attempt++) {
            String candidateCode = shortCodeGenerator.generate();
            ShortLink shortLink = ShortLink.normal(
                    candidateCode,
                    normalizedUrl,
                    normalizedChannel,
                    LocalDateTime.now(clock));

            // The repository atomically reserves the global short code. A false result
            // means another link owns the candidate, so retry with a fresh candidate.
            if (repository.saveIfAbsent(candidateCode, shortLink)) {
                return candidateCode;
            }
        }
        throw new ShortCodeGenerationException(
                "failed to generate a unique short code after " + MAX_GENERATION_ATTEMPTS + " attempts");
    }

    private String createAndStoreBlindBox(
            List<String> normalizedUrls,
            String normalizedChannel,
            int validTimes) {
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
        throw new ShortCodeGenerationException(
                "failed to generate a unique short code after " + MAX_GENERATION_ATTEMPTS + " attempts");
    }

    private List<String> normalizeBlindBoxUrls(List<String> originalUrls) {
        if (originalUrls == null || originalUrls.size() < MIN_BLIND_BOX_URLS) {
            throw new BlindBoxUrlInsufficientException(
                    "blind-box must contain at least " + MIN_BLIND_BOX_URLS + " URLs");
        }
        if (originalUrls.size() > MAX_BLIND_BOX_URLS) {
            throw new BusinessException(
                    "BLIND_BOX_URL_LIMIT_EXCEEDED",
                    "blind-box must contain at most " + MAX_BLIND_BOX_URLS + " URLs");
        }

        Set<String> seenUrls = new HashSet<>();
        List<String> normalizedUrls = new ArrayList<>(originalUrls.size());
        for (String originalUrl : originalUrls) {
            String normalizedUrl = urlValidator.validateAndNormalize(originalUrl);
            if (!seenUrls.add(normalizedUrl)) {
                throw new BlindBoxDuplicateUrlException(
                        "blind-box original URLs must not contain duplicates");
            }
            normalizedUrls.add(normalizedUrl);
        }
        return List.copyOf(normalizedUrls);
    }

    private int validateValidTimes(Integer validTimes) {
        if (validTimes == null || validTimes < 1 || validTimes > MAX_VALID_TIMES) {
            throw new BusinessException(
                    "INVALID_VALID_TIMES",
                    "validTimes must be between 1 and " + MAX_VALID_TIMES);
        }
        return validTimes;
    }

    private ShortLink findByShortCode(String shortCode) {
        return repository.findByShortCode(shortCode)
                .orElseThrow(() -> new ShortLinkNotFoundException("short link not found: " + shortCode));
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

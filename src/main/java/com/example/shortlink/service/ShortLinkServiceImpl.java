package com.example.shortlink.service;

import com.example.shortlink.domain.ShortLink;
import com.example.shortlink.dto.request.CreateNormalLinkRequest;
import com.example.shortlink.dto.response.ShortLinkResponse;
import com.example.shortlink.exception.ShortCodeGenerationException;
import com.example.shortlink.exception.ShortLinkNotFoundException;
import com.example.shortlink.generator.ShortCodeGenerator;
import com.example.shortlink.mapper.ShortLinkMapper;
import com.example.shortlink.repository.ShortLinkRepository;
import com.example.shortlink.validator.ChannelNormalizer;
import com.example.shortlink.validator.ShortCodeValidator;
import com.example.shortlink.validator.UrlValidator;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;

@Service
public class ShortLinkServiceImpl implements ShortLinkService {

    private static final int MAX_GENERATION_ATTEMPTS = 10;

    private final ShortLinkRepository repository;
    private final ShortCodeGenerator shortCodeGenerator;
    private final UrlValidator urlValidator;
    private final ChannelNormalizer channelNormalizer;
    private final NormalLinkBusinessKeyFactory businessKeyFactory;
    private final ShortLinkMapper mapper;
    private final ShortCodeValidator shortCodeValidator;
    private final Clock clock;

    public ShortLinkServiceImpl(
            ShortLinkRepository repository,
            ShortCodeGenerator shortCodeGenerator,
            UrlValidator urlValidator,
            ChannelNormalizer channelNormalizer,
            NormalLinkBusinessKeyFactory businessKeyFactory,
            ShortLinkMapper mapper,
            ShortCodeValidator shortCodeValidator,
            Clock clock) {
        this.repository = repository;
        this.shortCodeGenerator = shortCodeGenerator;
        this.urlValidator = urlValidator;
        this.channelNormalizer = channelNormalizer;
        this.businessKeyFactory = businessKeyFactory;
        this.mapper = mapper;
        this.shortCodeValidator = shortCodeValidator;
        this.clock = clock;
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
    public ShortLinkResponse getByShortCode(String shortCode) {
        shortCodeValidator.validate(shortCode);
        ShortLink shortLink = repository.findByShortCode(shortCode)
                .orElseThrow(() -> new ShortLinkNotFoundException("short link not found: " + shortCode));
        return mapper.toResponse(shortLink);
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
}

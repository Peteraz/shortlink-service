package com.example.shortlink.validator;

import com.example.shortlink.domain.ShortLink;
import com.example.shortlink.exception.InvalidChannelException;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class ChannelNormalizer {

    private static final int MAX_CHANNEL_LENGTH = 64;
    private static final Pattern CHANNEL_PATTERN = Pattern.compile("[\\p{L}\\p{N}_-]+");

    public String normalize(String channel) {
        if (channel == null || channel.isBlank()) {
            return ShortLink.DEFAULT_CHANNEL;
        }

        String normalizedChannel = channel.trim();
        if (normalizedChannel.length() > MAX_CHANNEL_LENGTH || !CHANNEL_PATTERN.matcher(normalizedChannel).matches()) {
            throw new InvalidChannelException("channel must be at most 64 characters and contain only letters, numbers, Chinese characters, underscores, or hyphens");
        }
        return normalizedChannel;
    }
}

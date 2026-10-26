package com.team262.moviematch.util;

import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Component;

@Component
public class RoomCodeGenerator {
    private static final char[] charArray = "ABCDEFGHJKMNPQRSTUVWXYZ23456789".toCharArray();
    private static final int LENGTH = 6;

    public String generateCode() {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < LENGTH; i++) {
            int randomIndex = ThreadLocalRandom.current().nextInt(charArray.length);
            builder.append(charArray[randomIndex]);
        }
        return builder.toString();
    }
}

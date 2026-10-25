package com.team262.moviematch.util;

import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class RoomCodeGeneratorTest {

    private final RoomCodeGenerator roomCodeGenerator =
            new RoomCodeGenerator();

    private static final Set<Character> ALLOWED_CHARACTERS =
            "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
                    .chars()
                    .mapToObj(c -> (char) c)
                    .collect(Collectors.toSet());

    @Test
    void shouldGenerateCodeWithExactlySixCharacters() {
        String code = roomCodeGenerator.generateCode();

        assertEquals(6, code.length());
    }

    @Test
    void shouldGenerateCodeUsingOnlyAllowedCharacters() {
        String code = roomCodeGenerator.generateCode();

        assertTrue(
                code.chars()
                        .mapToObj(c -> (char) c)
                        .allMatch(ALLOWED_CHARACTERS::contains)
        );
    }

    @Test
    void shouldNeverGenerateExcludedCharacters() {
        Set<Character> excludedCharacters =
                Set.of('O', '0', 'I', 'l', '1');
        IntStream.range(0, 1000).forEach(i -> {
            String code = roomCodeGenerator.generateCode();
            assertTrue(
                    code.chars()
                            .mapToObj(c -> (char) c)
                            .noneMatch(excludedCharacters::contains),
                    "Generated code contains an excluded character: " + code
            );
        });
    }
}

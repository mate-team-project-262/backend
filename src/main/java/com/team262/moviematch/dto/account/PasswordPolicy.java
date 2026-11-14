package com.team262.moviematch.dto.account;

/** The one definition of "a valid password", used by registration and by PUT /me/password. */
public final class PasswordPolicy {

    private PasswordPolicy() {
    }

    public static final int MIN_LENGTH = 8;
    public static final int MAX_LENGTH = 72;

    /** At least one letter and one digit. (?s) lets "." match line breaks too. */
    public static final String LETTER_AND_DIGIT = "(?s)(?=.*[A-Za-z])(?=.*[0-9]).*";

    public static final String LETTER_AND_DIGIT_MESSAGE = "must contain at least one letter and one digit";
}
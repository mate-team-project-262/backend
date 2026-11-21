package com.team262.moviematch.service.account;

import com.team262.moviematch.dto.error.FieldErrorResponse;
import com.team262.moviematch.exception.ex.RequestValidationException;
import com.team262.moviematch.model.User;
import com.team262.moviematch.model.UserAvatar;
import com.team262.moviematch.repository.UserAvatarRepository;
import com.team262.moviematch.repository.UserRepository;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
@RequiredArgsConstructor
public class AccountAvatarService {
    private static final long MAX_BYTES = 2L * 1024 * 1024;
    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
    private final UserAvatarRepository avatars;
    private final UserRepository users;

    @Transactional
    public User upload(User account, MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > MAX_BYTES) {
            throw invalidFile("Choose an image up to 2 MB");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException exception) {
            throw invalidFile("Could not read the image");
        }
        String contentType = detectType(bytes);
        if (contentType == null || !contentType.equals(file.getContentType())) {
            throw invalidFile("Use a JPG, PNG or WebP image");
        }
        User user = users.findById(account.getId()).orElseThrow();
        UserAvatar avatar = avatars.findById(user.getId()).orElse(null);
        if (avatar == null) {
            avatars.save(new UserAvatar(user.getId(), contentType, bytes));
        } else {
            avatar.replace(contentType, bytes);
        }
        long now = System.currentTimeMillis();
        user.changeAvatarVersion(user.getAvatarVersion() == null ? now
                : Math.max(now, user.getAvatarVersion() + 1));
        return user;
    }

    @Transactional
    public void delete(User user) {
        User managed = users.findById(user.getId()).orElseThrow();
        avatars.deleteById(managed.getId());
        managed.changeAvatarVersion(null);
    }

    @Transactional(readOnly = true)
    public UserAvatar get(User user) {
        return avatars.findById(user.getId()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Avatar not found"));
    }

    private static RequestValidationException invalidFile(String message) {
        return new RequestValidationException(List.of(new FieldErrorResponse("file", message)));
    }

    private static String detectType(byte[] bytes) {
        if (startsWith(bytes, PNG)) {
            return "image/png";
        }
        if (bytes.length >= 3 && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8
                && (bytes[2] & 0xff) == 0xff) {
            return "image/jpeg";
        }
        if (bytes.length >= 12 && ascii(bytes, 0, "RIFF") && ascii(bytes, 8, "WEBP")) {
            return "image/webp";
        }
        return null;
    }

    private static boolean startsWith(byte[] bytes, byte[] prefix) {
        if (bytes.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (bytes[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean ascii(byte[] bytes, int offset, String value) {
        for (int i = 0; i < value.length(); i++) {
            if (bytes[offset + i] != value.charAt(i)) {
                return false;
            }
        }
        return true;
    }
}

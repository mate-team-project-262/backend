package com.team262.moviematch.security;

import com.team262.moviematch.exception.ErrorCode;
import com.team262.moviematch.exception.ex.UnauthorizedException;
import com.team262.moviematch.model.Participant;
import com.team262.moviematch.service.ParticipantTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@Component
@RequiredArgsConstructor
public class CurrentParticipantArgumentResolver implements HandlerMethodArgumentResolver {

    public static final String HEADER = "X-Participant-Token";
    private final ParticipantTokenService participantTokenService;

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentParticipant.class)
                && Participant.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest,
                                  WebDataBinderFactory binderFactory) {
        String token = webRequest.getHeader(HEADER);
        if (token == null || token.trim().isEmpty()) {
            throw new UnauthorizedException(ErrorCode.INVALID_TOKEN, "Missing participant token");
        }

        return participantTokenService.requireParticipant(token.trim());
    }
}

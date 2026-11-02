package com.team262.moviematch.service;

import java.util.List;

/** Internal service. Callers must supply the ID of an authenticated participant. */
public interface RoomDeckService {
    List<Long> prepare(String code, long participantId);

    List<Long> getTitleIds(String code, long participantId);
}

package com.team262.moviematch.repository;

import com.team262.moviematch.model.RoomTitle;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomTitleRepository extends JpaRepository<RoomTitle, Long> {
    @EntityGraph(attributePaths = "title")
    List<RoomTitle> findAllByRoomIdOrderByPositionAsc(Long roomId);
}

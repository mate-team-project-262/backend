package com.team262.moviematch.repository;

import com.team262.moviematch.model.RoomTitle;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomTitleRepository extends JpaRepository<RoomTitle, Long> {
    Optional<RoomTitle> findByIdAndRoomId(Long id, Long roomId);

    long countByRoomId(Long roomId);

    @EntityGraph(attributePaths = "title")
    List<RoomTitle> findAllByRoomIdOrderByPositionAsc(Long roomId);
}

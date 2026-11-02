package com.team262.moviematch.repository;

import com.team262.moviematch.model.RoomTitle;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoomTitleRepository extends JpaRepository<RoomTitle, Long> {
    @EntityGraph(attributePaths = "title")
    List<RoomTitle> findAllByRoomIdOrderByPositionAsc(Long roomId);

    @Query("select rt.position as position, rt.title.id as titleId from RoomTitle rt "
            + "where rt.room.id = :roomId order by rt.position")
    List<Slot> findSlotsByRoomId(@Param("roomId") Long roomId);

    interface Slot {
        int getPosition();
        Long getTitleId();
    }
}

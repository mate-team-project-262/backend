package com.team262.moviematch.repository;

import com.team262.moviematch.model.Room;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoomRepository extends JpaRepository<Room, Long> {
    @Query("select room from Room room where room.code = upper(:code)")
    Optional<Room> findByCode(@Param("code") String code);
}

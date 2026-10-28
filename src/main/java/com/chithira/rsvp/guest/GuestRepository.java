package com.chithira.rsvp.guest;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GuestRepository extends JpaRepository<Guest, Long> {

    List<Guest> findByEventIdOrderById(Long eventId);

    @EntityGraph(attributePaths = "event")
    Optional<Guest> findByToken(String token);

    Optional<Guest> findByIdAndEventId(Long id, Long eventId);

    boolean existsByEventIdAndPhone(Long eventId, String phone);
}

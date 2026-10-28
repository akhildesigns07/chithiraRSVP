package com.chithira.rsvp.event;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventRepository extends JpaRepository<Event, Long> {

    Optional<Event> findByHostTokenHash(String hostTokenHash);

    Optional<Event> findByOgSlug(String ogSlug);

    List<Event> findAllByOrderByStartsAtDesc();
}

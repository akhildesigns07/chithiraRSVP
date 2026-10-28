package com.chithira.rsvp.host;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EventMessageRepository extends JpaRepository<EventMessage, Long> {
}

package com.chithira.rsvp.event;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface LetterPageRepository extends JpaRepository<LetterPage, Long> {

    @Query("""
            select new com.chithira.rsvp.event.LetterPageInfo(p.pageNo, p.contentType, p.filename)
            from LetterPage p where p.eventId = :eventId order by p.pageNo""")
    List<LetterPageInfo> findInfoByEventId(Long eventId);

    Optional<LetterPage> findByEventIdAndPageNo(Long eventId, int pageNo);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from LetterPage p where p.eventId = :eventId")
    void deleteByEventId(Long eventId);
}

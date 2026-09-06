package com.whatsmine.repository;

import com.whatsmine.model.SegmentContact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface SegmentContactRepository extends JpaRepository<SegmentContact, SegmentContact.PivotId> {
    List<SegmentContact> findBySegmentId(Long segmentId);

    List<SegmentContact> findByContactId(Long contactId);

    @Transactional
    void deleteBySegmentId(Long segmentId);

    @Transactional
    void deleteBySegmentIdAndContactId(Long segmentId, Long contactId);

    long countBySegmentId(Long segmentId);
}

package com.whatsmine.repository;

import com.whatsmine.model.SegmentContact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SegmentContactRepository extends JpaRepository<SegmentContact, SegmentContact.PivotId> {
    List<SegmentContact> findBySegmentId(Long segmentId);
}

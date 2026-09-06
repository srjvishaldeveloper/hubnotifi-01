package com.whatsmine.repository;

import com.whatsmine.model.ContactTagPivot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ContactTagPivotRepository extends JpaRepository<ContactTagPivot, ContactTagPivot.PivotId> {
    List<ContactTagPivot> findByTagId(Long tagId);
    List<ContactTagPivot> findByContactId(Long contactId);
}

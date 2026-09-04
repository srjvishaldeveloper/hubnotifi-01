package com.whatsmine.repository;

import com.whatsmine.model.WhatsappPhoneNumber;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WhatsappPhoneNumberRepository extends JpaRepository<WhatsappPhoneNumber, Long> {

    Optional<WhatsappPhoneNumber> findByPhoneNumberId(String phoneNumberId);

    List<WhatsappPhoneNumber> findByWabaIdFk(Long wabaIdFk);
}

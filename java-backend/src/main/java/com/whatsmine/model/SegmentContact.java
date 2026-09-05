package com.whatsmine.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.util.Objects;

/** Maps the existing segment_contact table (segment_id, contact_id composite PK) — materialized membership for static (and periodically re-materialized dynamic) segments. Table already exists from the PHP schema; no migration needed. */
@Entity
@Table(name = "segment_contact")
@IdClass(SegmentContact.PivotId.class)
public class SegmentContact {

    @Id
    @Column(name = "segment_id")
    private Long segmentId;

    @Id
    @Column(name = "contact_id")
    private Long contactId;

    public Long getSegmentId() { return segmentId; }
    public void setSegmentId(Long segmentId) { this.segmentId = segmentId; }
    public Long getContactId() { return contactId; }
    public void setContactId(Long contactId) { this.contactId = contactId; }

    public static class PivotId implements Serializable {
        private Long segmentId;
        private Long contactId;

        public PivotId() {}
        public PivotId(Long segmentId, Long contactId) {
            this.segmentId = segmentId;
            this.contactId = contactId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PivotId)) return false;
            PivotId that = (PivotId) o;
            return Objects.equals(segmentId, that.segmentId) && Objects.equals(contactId, that.contactId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(segmentId, contactId);
        }
    }
}

package com.whatsmine.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.util.Objects;

/** Maps the existing contact_tag_pivot table (contact_id, tag_id composite PK) — no migration needed, table already exists from the PHP schema. */
@Entity
@Table(name = "contact_tag_pivot")
@IdClass(ContactTagPivot.PivotId.class)
public class ContactTagPivot {

    @Id
    @Column(name = "contact_id")
    private Long contactId;

    @Id
    @Column(name = "tag_id")
    private Long tagId;

    public Long getContactId() { return contactId; }
    public void setContactId(Long contactId) { this.contactId = contactId; }
    public Long getTagId() { return tagId; }
    public void setTagId(Long tagId) { this.tagId = tagId; }

    public static class PivotId implements Serializable {
        private Long contactId;
        private Long tagId;

        public PivotId() {}
        public PivotId(Long contactId, Long tagId) {
            this.contactId = contactId;
            this.tagId = tagId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PivotId)) return false;
            PivotId that = (PivotId) o;
            return Objects.equals(contactId, that.contactId) && Objects.equals(tagId, that.tagId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(contactId, tagId);
        }
    }
}

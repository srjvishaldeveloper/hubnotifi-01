package com.whatsmine.service.segments;

import com.whatsmine.model.Contact;
import com.whatsmine.model.Segment;
import com.whatsmine.model.SegmentContact;
import com.whatsmine.repository.SegmentContactRepository;
import com.whatsmine.repository.SegmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Java port of PHP SegmentController's call into SegmentResolver::materialise()
 * on every save of a dynamic segment: re-run its rules, replace the
 * segment_contact pivot with the fresh member list, and update contact_count.
 * Static segments never have their membership computed here — that's the
 * "manage contacts" attach/detach flow — this only recomputes their count.
 */
@Service
public class SegmentMaterializerService {

    private final SegmentRepository segmentRepository;
    private final SegmentContactRepository segmentContactRepository;
    private final SegmentRuleEvaluator segmentRuleEvaluator;

    public SegmentMaterializerService(SegmentRepository segmentRepository, SegmentContactRepository segmentContactRepository,
                                       SegmentRuleEvaluator segmentRuleEvaluator) {
        this.segmentRepository = segmentRepository;
        this.segmentContactRepository = segmentContactRepository;
        this.segmentRuleEvaluator = segmentRuleEvaluator;
    }

    @Transactional
    public Segment materialise(Segment segment) {
        if ("dynamic".equalsIgnoreCase(segment.getType())) {
            List<Contact> matches = segmentRuleEvaluator.evaluate(segment.getWorkspaceId(), segment.getRulesJson());
            segmentContactRepository.deleteBySegmentId(segment.getId());
            for (Contact contact : matches) {
                SegmentContact sc = new SegmentContact();
                sc.setSegmentId(segment.getId());
                sc.setContactId(contact.getId());
                segmentContactRepository.save(sc);
            }
            segment.setContactCount(matches.size());
        } else {
            segment.setContactCount((int) segmentContactRepository.countBySegmentId(segment.getId()));
        }
        return segmentRepository.save(segment);
    }
}

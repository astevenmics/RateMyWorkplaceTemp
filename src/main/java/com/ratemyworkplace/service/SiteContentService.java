package com.ratemyworkplace.service;

import com.ratemyworkplace.domain.SiteFeedback;
import com.ratemyworkplace.domain.SiteUpdate;
import com.ratemyworkplace.domain.User;
import com.ratemyworkplace.dto.Requests;
import com.ratemyworkplace.repository.SiteFeedbackRepository;
import com.ratemyworkplace.repository.SiteUpdateRepository;
import com.ratemyworkplace.web.ApiException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Footer "feedback about the site" inbox and admin-authored "What's new" updates. */
@Service
public class SiteContentService {

    private final SiteFeedbackRepository siteFeedbackRepository;
    private final SiteUpdateRepository siteUpdateRepository;

    public SiteContentService(SiteFeedbackRepository siteFeedbackRepository,
                              SiteUpdateRepository siteUpdateRepository) {
        this.siteFeedbackRepository = siteFeedbackRepository;
        this.siteUpdateRepository = siteUpdateRepository;
    }

    // ---- site feedback ----
    @Transactional
    public SiteFeedback submitSiteFeedback(User author, Requests.SiteFeedbackRequest req) {
        SiteFeedback feedback = new SiteFeedback();
        feedback.setAuthor(author);
        feedback.setContactEmail(req.contactEmail());
        feedback.setCategory(req.category());
        feedback.setMessage(req.message());
        return siteFeedbackRepository.save(feedback);
    }

    public Page<SiteFeedback> listSiteFeedback(boolean resolved, Pageable pageable) {
        return siteFeedbackRepository.findByResolved(resolved, pageable);
    }

    @Transactional
    public SiteFeedback resolveSiteFeedback(Long id, boolean resolved) {
        SiteFeedback feedback = siteFeedbackRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Feedback not found"));
        feedback.setResolved(resolved);
        return siteFeedbackRepository.save(feedback);
    }

    // ---- site updates / changelog ----
    public Page<SiteUpdate> listUpdates(Pageable pageable) {
        return siteUpdateRepository.findByPublishedTrueOrderByCreatedAtDesc(pageable);
    }

    public List<SiteUpdate> latestUpdates() {
        return siteUpdateRepository.findTop5ByPublishedTrueOrderByCreatedAtDesc();
    }

    @Transactional
    public SiteUpdate createUpdate(User author, Requests.SiteUpdateRequest req) {
        SiteUpdate update = new SiteUpdate();
        update.setTitle(req.title());
        update.setBody(req.body());
        update.setTag(req.tag());
        update.setAuthor(author);
        update.setPublished(true);
        return siteUpdateRepository.save(update);
    }

    @Transactional
    public void deleteUpdate(Long id) {
        if (!siteUpdateRepository.existsById(id)) {
            throw ApiException.notFound("Update not found");
        }
        siteUpdateRepository.deleteById(id);
    }
}
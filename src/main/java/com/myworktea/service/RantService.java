package com.myworktea.service;

import com.myworktea.domain.Rant;
import com.myworktea.dto.Requests;
import com.myworktea.repository.RantRepository;
import com.myworktea.web.ApiException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Anonymous, non-company-specific work rants — no login required to read or post. */
@Service
public class RantService {

    private final RantRepository rantRepository;

    public RantService(RantRepository rantRepository) {
        this.rantRepository = rantRepository;
    }

    @Transactional
    public Rant submit(Requests.RantRequest req) {
        String nickname = req.nickname() == null ? null : req.nickname().trim();
        Rant rant = new Rant();
        rant.setNickname(nickname == null || nickname.isEmpty() ? null : nickname);
        rant.setBody(req.body().trim());
        return rantRepository.save(rant);
    }

    public List<Rant> random(int limit) {
        return rantRepository.findRandom(Math.max(1, limit));
    }

    public Page<Rant> recent(Pageable pageable) {
        return rantRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    @Transactional
    public void delete(Long id) {
        if (!rantRepository.existsById(id)) {
            throw ApiException.notFound("Rant not found");
        }
        rantRepository.deleteById(id);
    }
}

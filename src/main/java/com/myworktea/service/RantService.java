package com.myworktea.service;

import com.myworktea.domain.Rant;
import com.myworktea.domain.RantVote;
import com.myworktea.domain.VoteType;
import com.myworktea.dto.DtoMapper;
import com.myworktea.dto.Requests;
import com.myworktea.dto.Responses;
import com.myworktea.repository.RantRepository;
import com.myworktea.repository.RantVoteRepository;
import com.myworktea.web.ApiException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Anonymous, non-company-specific work rants — no login required to read, post or vote. */
@Service
public class RantService {

    private final RantRepository rantRepository;
    private final RantVoteRepository rantVoteRepository;

    public RantService(RantRepository rantRepository, RantVoteRepository rantVoteRepository) {
        this.rantRepository = rantRepository;
        this.rantVoteRepository = rantVoteRepository;
    }

    @Transactional
    public Responses.RantDto submit(Requests.RantRequest req) {
        String nickname = req.nickname() == null ? null : req.nickname().trim();
        Rant rant = new Rant();
        rant.setNickname(nickname == null || nickname.isEmpty() ? null : nickname);
        rant.setBody(req.body().trim());
        return DtoMapper.rant(rantRepository.save(rant));
    }

    /** A random sample for the homepage teaser. {@code voterId} may be null (no vote highlighting). */
    public List<Responses.RantDto> random(int limit, String voterId) {
        List<Rant> rants = rantRepository.findRandom(Math.max(1, limit));
        Map<Long, VoteType> myVotes = myVotes(rants, voterId);
        return rants.stream().map(r -> DtoMapper.rant(r, myVotes.get(r.getId()))).toList();
    }

    /** Newest-first, most-upvoted or most-downvoted depending on {@code pageable}'s sort. */
    public Page<Responses.RantDto> list(Pageable pageable, String voterId) {
        Page<Rant> page = rantRepository.findAll(pageable);
        Map<Long, VoteType> myVotes = myVotes(page.getContent(), voterId);
        return page.map(r -> DtoMapper.rant(r, myVotes.get(r.getId())));
    }

    private Map<Long, VoteType> myVotes(List<Rant> rants, String voterId) {
        if (voterId == null || rants.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = rants.stream().map(Rant::getId).toList();
        return rantVoteRepository.findByVoterIdAndRantIdIn(voterId, ids).stream()
                .collect(Collectors.toMap(v -> v.getRant().getId(), RantVote::getVoteType));
    }

    /**
     * Casts a vote, switches an existing one, or retracts it (clicking the same direction again).
     * One vote per {@code voterId} per rant, enforced by a unique constraint on {@link RantVote}.
     */
    @Transactional
    public Responses.RantDto vote(Long rantId, String voterId, VoteType type) {
        Rant rant = rantRepository.findById(rantId)
                .orElseThrow(() -> ApiException.notFound("Rant not found"));
        RantVote existing = rantVoteRepository.findByRantIdAndVoterId(rantId, voterId).orElse(null);

        VoteType myVote;
        if (existing == null) {
            RantVote vote = new RantVote();
            vote.setRant(rant);
            vote.setVoterId(voterId);
            vote.setVoteType(type);
            rantVoteRepository.save(vote);
            adjust(rant, type, 1);
            myVote = type;
        } else if (existing.getVoteType() == type) {
            rantVoteRepository.delete(existing);
            adjust(rant, type, -1);
            myVote = null;
        } else {
            adjust(rant, existing.getVoteType(), -1);
            adjust(rant, type, 1);
            existing.setVoteType(type);
            rantVoteRepository.save(existing);
            myVote = type;
        }
        return DtoMapper.rant(rantRepository.save(rant), myVote);
    }

    private void adjust(Rant rant, VoteType type, int delta) {
        Function<Rant, Integer> getter = type == VoteType.UP ? Rant::getUpvotes : Rant::getDownvotes;
        int next = Math.max(0, getter.apply(rant) + delta);
        if (type == VoteType.UP) {
            rant.setUpvotes(next);
        } else {
            rant.setDownvotes(next);
        }
    }

    @Transactional
    public void delete(Long id) {
        if (!rantRepository.existsById(id)) {
            throw ApiException.notFound("Rant not found");
        }
        rantVoteRepository.deleteByRantId(id);
        rantRepository.deleteById(id);
    }
}

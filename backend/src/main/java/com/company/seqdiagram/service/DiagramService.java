package com.company.seqdiagram.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.company.seqdiagram.domain.Diagram;
import com.company.seqdiagram.dto.DiagramRequest;
import com.company.seqdiagram.dto.DiagramSummary;
import com.company.seqdiagram.exception.NotFoundException;
import com.company.seqdiagram.repository.DiagramRepository;

/**
 * Every operation is scoped to the calling member, so one member can neither
 * see nor touch another member's diagrams.
 */
@Service
public class DiagramService {

    private final DiagramRepository repository;

    public DiagramService(DiagramRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<DiagramSummary> list(long memberId) {
        return repository.findAllSummaries(memberId).stream()
                .map(DiagramSummary::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public Diagram get(long id, long memberId) {
        return repository.findById(id, memberId)
                .orElseThrow(() -> new NotFoundException("다이어그램 " + id + " 을(를) 찾을 수 없습니다"));
    }

    @Transactional
    public Diagram create(long memberId, DiagramRequest request) {
        long id = repository.insert(
                memberId,
                request.title().strip(),
                trimToNull(request.description()),
                request.mermaidCode(),
                trimToNull(request.lastPrompt()));
        return get(id, memberId);
    }

    /** Overwrites the row; the previous revision is deliberately not kept. */
    @Transactional
    public Diagram update(long id, long memberId, DiagramRequest request) {
        int updated = repository.update(
                id,
                memberId,
                request.title().strip(),
                trimToNull(request.description()),
                request.mermaidCode(),
                trimToNull(request.lastPrompt()));

        if (updated == 0) {
            throw new NotFoundException("다이어그램 " + id + " 을(를) 찾을 수 없습니다");
        }
        return get(id, memberId);
    }

    @Transactional
    public void delete(long id, long memberId) {
        if (repository.deleteById(id, memberId) == 0) {
            throw new NotFoundException("다이어그램 " + id + " 을(를) 찾을 수 없습니다");
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

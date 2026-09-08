package com.company.seqdiagram.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.company.seqdiagram.domain.Diagram;
import com.company.seqdiagram.dto.DiagramRequest;
import com.company.seqdiagram.dto.DiagramSummary;
import com.company.seqdiagram.exception.NotFoundException;
import com.company.seqdiagram.repository.DiagramRepository;

@Service
public class DiagramService {

    private final DiagramRepository repository;

    public DiagramService(DiagramRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<DiagramSummary> list() {
        return repository.findAllSummaries().stream()
                .map(DiagramSummary::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public Diagram get(long id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Diagram " + id + " does not exist"));
    }

    @Transactional
    public Diagram create(DiagramRequest request) {
        long id = repository.insert(
                request.title().strip(),
                trimToNull(request.description()),
                request.mermaidCode(),
                trimToNull(request.lastPrompt()));
        return get(id);
    }

    /** Overwrites the row; the previous revision is deliberately not kept. */
    @Transactional
    public Diagram update(long id, DiagramRequest request) {
        int updated = repository.update(
                id,
                request.title().strip(),
                trimToNull(request.description()),
                request.mermaidCode(),
                trimToNull(request.lastPrompt()));

        if (updated == 0) {
            throw new NotFoundException("Diagram " + id + " does not exist");
        }
        return get(id);
    }

    @Transactional
    public void delete(long id) {
        if (repository.deleteById(id) == 0) {
            throw new NotFoundException("Diagram " + id + " does not exist");
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

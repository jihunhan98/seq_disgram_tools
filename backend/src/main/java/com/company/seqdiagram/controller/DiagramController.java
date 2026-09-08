package com.company.seqdiagram.controller;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.company.seqdiagram.domain.Diagram;
import com.company.seqdiagram.dto.DiagramRequest;
import com.company.seqdiagram.dto.DiagramSummary;
import com.company.seqdiagram.service.DiagramService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/diagrams")
public class DiagramController {

    private final DiagramService service;

    public DiagramController(DiagramService service) {
        this.service = service;
    }

    @GetMapping
    public List<DiagramSummary> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public Diagram get(@PathVariable long id) {
        return service.get(id);
    }

    @PostMapping
    public ResponseEntity<Diagram> create(@Valid @RequestBody DiagramRequest request) {
        Diagram created = service.create(request);
        return ResponseEntity.created(URI.create("/api/diagrams/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    public Diagram update(@PathVariable long id, @Valid @RequestBody DiagramRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}

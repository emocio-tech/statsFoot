package com.emocio.analysefoot.repository;

import com.emocio.analysefoot.model.Competition;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompetitionRepository extends JpaRepository<Competition, Integer> {

    boolean existsByName(String name);
}

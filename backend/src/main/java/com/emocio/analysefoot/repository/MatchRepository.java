package com.emocio.analysefoot.repository;

import com.emocio.analysefoot.model.Match;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchRepository extends JpaRepository<Match, Integer> {
}

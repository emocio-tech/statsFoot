package com.emocio.analysefoot.repository;

import com.emocio.analysefoot.model.MatchLineup;
import com.emocio.analysefoot.model.MatchLineupId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchLineupRepository extends JpaRepository<MatchLineup, MatchLineupId> {
}

package com.emocio.analysefoot.repository;

import com.emocio.analysefoot.model.PenaltyKick;
import com.emocio.analysefoot.model.PenaltyKickId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PenaltyKickRepository extends JpaRepository<PenaltyKick, PenaltyKickId> {
}

package com.emocio.analysefoot.repository;

import com.emocio.analysefoot.model.Goal;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GoalRepository extends JpaRepository<Goal, Integer> {
}

package com.emocio.analysefoot.repository;

import com.emocio.analysefoot.model.Referee;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RefereeRepository extends JpaRepository<Referee, Integer> {

    Optional<Referee> findByName(String name);
}

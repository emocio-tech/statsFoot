package com.emocio.analysefoot.repository;

import com.emocio.analysefoot.model.Player;
import com.emocio.analysefoot.model.Team;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PlayerRepository extends JpaRepository<Player, Integer> {

    Optional<Player> findByNameAndTeam(String name, Team team);
}

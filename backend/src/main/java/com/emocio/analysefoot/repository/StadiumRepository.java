package com.emocio.analysefoot.repository;

import com.emocio.analysefoot.model.Stadium;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StadiumRepository extends JpaRepository<Stadium, Integer> {

    Optional<Stadium> findByNameAndCity(String name, String city);
}

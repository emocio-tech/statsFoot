package com.emocio.analysefoot.repository;

import com.emocio.analysefoot.model.Booking;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingRepository extends JpaRepository<Booking, Integer> {
}

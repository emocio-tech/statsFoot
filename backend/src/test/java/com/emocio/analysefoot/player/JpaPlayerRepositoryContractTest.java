package com.emocio.analysefoot.player;

import org.springframework.beans.factory.annotation.Autowired;
import com.emocio.analysefoot.FullDataTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

import com.emocio.analysefoot.player.repository.JpaPlayerRepository;
import com.emocio.analysefoot.player.repository.PlayerRepository;

/** Runs the repository contract on the real JPA adapter, on the embedded PostgreSQL filled at start-up. */
@FullDataTest
@AutoConfigureMockMvc
class JpaPlayerRepositoryContractTest extends PlayerRepositoryContractTest {

	@Autowired
	private JpaPlayerRepository repository;

	@Override
	protected PlayerRepository repository() {
		return repository;
	}

}

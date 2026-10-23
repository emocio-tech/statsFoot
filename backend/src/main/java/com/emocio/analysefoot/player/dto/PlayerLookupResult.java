package com.emocio.analysefoot.player.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Lookup outcome: the profile of the identified player, or else the list of closest names. Exactly one is set. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PlayerLookupResult(PlayerProfile profile, PlayerSearchResult candidates) {

	public static PlayerLookupResult ofProfile(PlayerProfile profile) {
		return new PlayerLookupResult(profile, null);
	}

	public static PlayerLookupResult ofCandidates(PlayerSearchResult candidates) {
		return new PlayerLookupResult(null, candidates);
	}
}

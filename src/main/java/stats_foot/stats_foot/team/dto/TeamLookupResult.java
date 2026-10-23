package stats_foot.stats_foot.team.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Lookup outcome: the profile of the identified team, or else the list of closest names. Exactly one is set. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TeamLookupResult(TeamProfile profile, TeamSearchResult candidates) {

	public static TeamLookupResult ofProfile(TeamProfile profile) {
		return new TeamLookupResult(profile, null);
	}

	public static TeamLookupResult ofCandidates(TeamSearchResult candidates) {
		return new TeamLookupResult(null, candidates);
	}
}

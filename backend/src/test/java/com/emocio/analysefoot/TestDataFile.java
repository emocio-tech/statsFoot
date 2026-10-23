package com.emocio.analysefoot;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;

/** The real, versioned {@code data/donneesStats.json}, parsed independently of the application code. */
public final class TestDataFile {

	public static final Path PATH = Path.of("..", "data", "donneesStats.json");

	/** A player of the file with the name and FIFA code of its team. */
	public record FilePlayer(int id, String name, String teamName, String teamCode, JsonNode node) {

		public JsonNode tournament() {
			return node.get("tournament");
		}

	}

	private static JsonNode root;
	private static List<FilePlayer> players;

	private TestDataFile() {
	}

	public static synchronized JsonNode root() {
		if (root == null) {
			try {
				root = JsonMapper.builder().build().readTree(PATH.toFile());
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		}
		return root;
	}

	public static synchronized List<FilePlayer> players() {
		if (players == null) {
			Map<Integer, JsonNode> teams = new HashMap<>();
			for (JsonNode team : root().get("teams")) {
				teams.put(team.get("id").asInt(), team);
			}
			List<FilePlayer> list = new ArrayList<>();
			for (JsonNode node : root().get("players")) {
				JsonNode team = teams.get(node.get("teamId").asInt());
				list.add(new FilePlayer(node.get("id").asInt(), node.get("name").asText(),
						team.get("name").asText(), team.get("fifaCode").asText(), node));
			}
			players = List.copyOf(list);
		}
		return players;
	}

	public static FilePlayer player(int id) {
		return players().stream().filter(p -> p.id() == id).findFirst().orElseThrow();
	}

	/** Integer value of a field, or {@code null} when the field is JSON null. */
	public static Integer intOrNull(JsonNode node, String field) {
		JsonNode value = node.get(field);
		return value == null || value.isNull() ? null : value.asInt();
	}

	/** Decimal value of a field, or {@code null} when the field is JSON null. */
	public static Double doubleOrNull(JsonNode node, String field) {
		JsonNode value = node.get(field);
		return value == null || value.isNull() ? null : value.asDouble();
	}

}

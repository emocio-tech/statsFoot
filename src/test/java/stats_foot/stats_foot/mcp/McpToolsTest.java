package stats_foot.stats_foot.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import stats_foot.stats_foot.player.dto.PlayerComparison;
import stats_foot.stats_foot.player.exception.AmbiguousPlayerNameException;
import stats_foot.stats_foot.player.exception.BlankComparisonNameException;
import stats_foot.stats_foot.player.exception.ComparisonPlayerNotFoundException;
import stats_foot.stats_foot.player.exception.SamePlayerComparisonException;
import stats_foot.stats_foot.player.repository.PlayerRepository;
import stats_foot.stats_foot.player.service.PlayerComparisonService;
import stats_foot.stats_foot.player.service.PlayerService;

class McpToolsTest {

	private final PlayerComparisonService comparisonService = mock(PlayerComparisonService.class);
	private final McpTools tools = new McpTools(comparisonService);

	private static List<Method> toolMethods() {
		return Arrays.stream(McpTools.class.getDeclaredMethods())
				.filter(m -> m.isAnnotationPresent(Tool.class))
				.toList();
	}

	@Test
	void comparePlayers_existingPlayers_delegatesToService() {
		PlayerComparison comparison = mock(PlayerComparison.class);
		when(comparisonService.compareByName("mbappe", "haaland")).thenReturn(comparison);

		assertSame(comparison, tools.comparePlayers("mbappe", "haaland"));
		verify(comparisonService).compareByName("mbappe", "haaland");
	}

	@Test
	void comparePlayers_partialAccentlessNames_passesNamesUnchanged() {
		PlayerComparison comparison = mock(PlayerComparison.class);
		when(comparisonService.compareByName("raul rangel", "cesar montes")).thenReturn(comparison);

		assertSame(comparison, tools.comparePlayers("raul rangel", "cesar montes"));
		verify(comparisonService).compareByName("raul rangel", "cesar montes");
	}

	@Test
	void class_isInMcpPackageAndNotAComponent() {
		assertEquals("stats_foot.stats_foot.mcp", McpTools.class.getPackageName());
		assertFalse(McpTools.class.isAnnotationPresent(Component.class));
		assertFalse(McpTools.class.isAnnotationPresent(Service.class));
		assertEquals(1, toolMethods().size());
	}

	@Test
	void comparePlayers_toolAnnotation_hasNameAndDescriptions() {
		List<Method> methods = toolMethods();
		assertEquals(1, methods.size());
		Method method = methods.get(0);
		Tool tool = method.getAnnotation(Tool.class);

		assertEquals("comparePlayers", tool.name());
		assertFalse(tool.description().isBlank());

		Parameter[] parameters = method.getParameters();
		assertEquals(2, parameters.length);
		for (Parameter parameter : parameters) {
			ToolParam param = parameter.getAnnotation(ToolParam.class);
			assertNotNull(param);
			assertFalse(param.description().isBlank());
			assertTrue(param.required());
		}
	}

	@Test
	void mcpClasses_onlyDependOnComparisonService() {
		Constructor<?>[] constructors = McpTools.class.getDeclaredConstructors();
		assertEquals(1, constructors.length);
		assertEquals(List.of(PlayerComparisonService.class), Arrays.asList(constructors[0].getParameterTypes()));

		for (Field field : McpTools.class.getDeclaredFields()) {
			if (Modifier.isStatic(field.getModifiers())) {
				continue;
			}
			assertFalse(PlayerService.class.isAssignableFrom(field.getType()));
			assertFalse(PlayerRepository.class.isAssignableFrom(field.getType()));
		}
	}

	@Test
	void comparePlayers_unknownPlayerA_namesPlayerAndSuggestsFullerName() {
		when(comparisonService.compareByName("zzzz unknown", "mbappe"))
				.thenThrow(new ComparisonPlayerNotFoundException("Player \"zzzz unknown\" not found."));

		ComparisonPlayerNotFoundException e = assertThrows(ComparisonPlayerNotFoundException.class,
				() -> tools.comparePlayers("zzzz unknown", "mbappe"));

		String message = e.getMessage();
		assertTrue(message.contains("zzzz unknown"));
		assertTrue(message.contains("fuller") || message.contains("corrected"));
		assertFalse(message.toLowerCase().contains("searchplayers"));
		assertFalse(message.contains("mbappe"));
	}

	@Test
	void comparePlayers_unknownPlayerB_namesPlayerBNotPlayerA() {
		when(comparisonService.compareByName("mbappe", "zzzz unknown"))
				.thenThrow(new ComparisonPlayerNotFoundException("Player \"zzzz unknown\" not found."));

		ComparisonPlayerNotFoundException e = assertThrows(ComparisonPlayerNotFoundException.class,
				() -> tools.comparePlayers("mbappe", "zzzz unknown"));

		String message = e.getMessage();
		assertTrue(message.contains("zzzz unknown"));
		assertFalse(message.contains("mbappe"));
		assertFalse(message.toLowerCase().contains("searchplayers"));
	}

	@Test
	void comparePlayers_bothUnknown_reportsPlayerAOnly() {
		when(comparisonService.compareByName("xxx", "yyy"))
				.thenThrow(new ComparisonPlayerNotFoundException("Player \"xxx\" not found."));

		ComparisonPlayerNotFoundException e = assertThrows(ComparisonPlayerNotFoundException.class,
				() -> tools.comparePlayers("xxx", "yyy"));

		assertTrue(e.getMessage().contains("xxx"));
		assertFalse(e.getMessage().contains("yyy"));
	}

	@Test
	void comparePlayers_blankName_keepsServiceException() {
		BlankComparisonNameException original = new BlankComparisonNameException("Player A name must not be blank.");
		when(comparisonService.compareByName(" ", "mbappe")).thenThrow(original);

		BlankComparisonNameException e = assertThrows(BlankComparisonNameException.class,
				() -> tools.comparePlayers(" ", "mbappe"));

		assertSame(original, e);
		assertEquals("Player A name must not be blank.", e.getMessage());
	}

	@Test
	void comparePlayers_ambiguousName_keepsServiceException() {
		AmbiguousPlayerNameException original = new AmbiguousPlayerNameException(
				"Several players match the name \"silva\": Silva (T1), Silva Santos (T3). Please be more precise.");
		when(comparisonService.compareByName("silva", "mbappe")).thenThrow(original);

		AmbiguousPlayerNameException e = assertThrows(AmbiguousPlayerNameException.class,
				() -> tools.comparePlayers("silva", "mbappe"));

		assertSame(original, e);
		assertEquals(original.getMessage(), e.getMessage());
	}

	@Test
	void comparePlayers_samePlayer_keepsServiceException() {
		SamePlayerComparisonException original = new SamePlayerComparisonException();
		when(comparisonService.compareByName("mbappe", "mbappe")).thenThrow(original);

		SamePlayerComparisonException e = assertThrows(SamePlayerComparisonException.class,
				() -> tools.comparePlayers("mbappe", "mbappe"));

		assertSame(original, e);
	}

	@Test
	void comparePlayers_emptyName_neverReportsNotFound() {
		when(comparisonService.compareByName("", "mbappe"))
				.thenThrow(new BlankComparisonNameException("Player A name must not be blank."));

		BlankComparisonNameException e = assertThrows(BlankComparisonNameException.class,
				() -> tools.comparePlayers("", "mbappe"));

		assertFalse(e.getMessage().contains("not found"));
	}
}

package stats_foot.stats_foot.player;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import stats_foot.stats_foot.player.util.NameNormalizer;

class NameNormalizerTest {

    @Test
    void upperCaseIsLowered() {
        assertEquals("suzuki", NameNormalizer.normalize("SUZUKI"));
    }

    @Test
    void accentOnLowerCaseLetterIsStripped() {
        assertEquals("sami", NameNormalizer.normalize("sámi"));
    }

    @Test
    void accentAndCaseAreNormalized() {
        assertEquals("ramirez", NameNormalizer.normalize("Ramírez"));
    }

    @Test
    void surroundingAndRepeatedSpacesAreCollapsed() {
        assertEquals("sami suzuki", NameNormalizer.normalize("  sami   suzuki  "));
    }

    @Test
    void nonBreakingSpaceIsTreatedAsSpace() {
        assertEquals("sami suzuki", NameNormalizer.normalize("sami suzuki"));
    }

    @Test
    void tabIsTreatedAsSpace() {
        assertEquals("sami suzuki", NameNormalizer.normalize("sami\tsuzuki"));
    }

    @Test
    void emptyGivesEmpty() {
        assertEquals("", NameNormalizer.normalize(""));
    }

    @Test
    void blankGivesEmpty() {
        assertEquals("", NameNormalizer.normalize("   "));
    }

    @Test
    void nullGivesEmpty() {
        assertEquals("", NameNormalizer.normalize(null));
    }

    @Test
    void normalizationIsIdempotent() {
        String once = NameNormalizer.normalize("  Sámi  SUZUKI\t");
        assertEquals(once, NameNormalizer.normalize(once));
    }
}

package io.cognis.vertical.livestock.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GeofenceTest {

    private static final Geofence FARM = new Geofence("test-farm", -26.0, 27.0, -25.0, 28.0);

    @Test
    void containsReturnsTrueForPointInsideBounds() {
        assertThat(FARM.contains(-25.5, 27.5)).isTrue();
    }

    @Test
    void containsReturnsFalseForPointAboveMaxLat() {
        assertThat(FARM.contains(-24.0, 27.5)).isFalse();
    }

    @Test
    void containsReturnsFalseForPointBelowMinLat() {
        assertThat(FARM.contains(-27.0, 27.5)).isFalse();
    }

    @Test
    void containsReturnsFalseForPointLeftOfMinLng() {
        assertThat(FARM.contains(-25.5, 26.0)).isFalse();
    }

    @Test
    void containsReturnsFalseForPointRightOfMaxLng() {
        assertThat(FARM.contains(-25.5, 29.0)).isFalse();
    }

    @Test
    void containsReturnsTrueOnMinLatBoundary() {
        assertThat(FARM.contains(-26.0, 27.5)).isTrue();
    }

    @Test
    void containsReturnsTrueOnMaxLatBoundary() {
        assertThat(FARM.contains(-25.0, 27.5)).isTrue();
    }

    @Test
    void containsReturnsTrueOnMinLngBoundary() {
        assertThat(FARM.contains(-25.5, 27.0)).isTrue();
    }

    @Test
    void containsReturnsTrueOnMaxLngBoundary() {
        assertThat(FARM.contains(-25.5, 28.0)).isTrue();
    }

    @Test
    void containsReturnsTrueForExactCorner() {
        assertThat(FARM.contains(-26.0, 27.0)).isTrue();
        assertThat(FARM.contains(-25.0, 28.0)).isTrue();
    }

    @Test
    void nameIsPreserved() {
        assertThat(FARM.name()).isEqualTo("test-farm");
    }
}

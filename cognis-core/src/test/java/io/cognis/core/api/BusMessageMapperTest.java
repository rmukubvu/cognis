package io.cognis.core.api;

import static org.assertj.core.api.Assertions.assertThat;

import io.cognis.core.model.ChatMessage;
import org.junit.jupiter.api.Test;

class BusMessageMapperTest {

    private final BusMessageMapper mapper = new BusMessageMapper();

    @Test
    void shouldMapDailyBriefMarker() {
        BusMessageMapper.OutboundFrame frame = mapper.map(ChatMessage.assistant("[workflow:daily_brief]\nToday summary"));

        assertThat(frame.type()).isEqualTo("daily_brief");
        assertThat(frame.content()).isEqualTo("Today summary");
    }

    @Test
    void shouldMapGoalCheckinMarker() {
        BusMessageMapper.OutboundFrame frame = mapper.map(ChatMessage.assistant("[workflow:goal_checkin] Goal update"));

        assertThat(frame.type()).isEqualTo("goal_checkin");
        assertThat(frame.content()).isEqualTo("Goal update");
    }

    @Test
    void shouldMapWorkflowResultMarker() {
        BusMessageMapper.OutboundFrame frame = mapper.map(ChatMessage.assistant("[workflow:workflow_result] Relationship prompt"));

        assertThat(frame.type()).isEqualTo("workflow_result");
        assertThat(frame.content()).isEqualTo("Relationship prompt");
    }

    @Test
    void shouldFallbackToNotificationForUnmarkedMessages() {
        BusMessageMapper.OutboundFrame frame = mapper.map(ChatMessage.assistant("General notification"));

        assertThat(frame.type()).isEqualTo("notification");
        assertThat(frame.content()).isEqualTo("General notification");
    }

    @Test
    void shouldKeepBlankContentForMarkerOnlyMessages() {
        BusMessageMapper.OutboundFrame frame = mapper.map(ChatMessage.assistant("[workflow:daily_brief]"));

        assertThat(frame.type()).isEqualTo("daily_brief");
        assertThat(frame.content()).isEmpty();
    }
}

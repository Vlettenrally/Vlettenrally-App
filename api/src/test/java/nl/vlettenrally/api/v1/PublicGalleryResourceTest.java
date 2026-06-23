package nl.vlettenrally.api.v1;

import nl.vlettenrally.data.domain.Game;
import nl.vlettenrally.data.domain.Picture;
import nl.vlettenrally.data.dto.game.PublicGameResponse;
import nl.vlettenrally.data.dto.picture.PublicPictureResponse;
import nl.vlettenrally.data.enums.PictureStatus;
import nl.vlettenrally.service.GameService;
import nl.vlettenrally.service.PictureService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import jakarta.ws.rs.core.Response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PublicGalleryResource}: response shaping for the public gallery endpoints.
 */
@ExtendWith(MockitoExtension.class)
class PublicGalleryResourceTest {

    @Mock PictureService pictureService;
    @Mock GameService gameService;
    @Mock PublicGalleryCache galleryCache;

    @InjectMocks PublicGalleryResource resource;

    @Test
    void games_returnsMinimalProjectionMostRecentFirst() {
        Game older = game("Spring Rally", Instant.parse("2026-04-01T10:00:00Z"));
        Game newer = game("Summer Rally", Instant.parse("2026-06-01T10:00:00Z"));
        when(gameService.findAll()).thenReturn(List.of(older, newer));

        Response response = resource.games();

        assertEquals(200, response.getStatus());
        List<?> body = assertInstanceOf(List.class, response.getEntity());
        assertEquals(2, body.size());
        assertEquals("Summer Rally", ((PublicGameResponse) body.getFirst()).name());
    }

    @Test
    void approvedForGame_mapsToPublicDtoWithViewUrl() {
        UUID gameId = UUID.randomUUID();
        Picture p = picture(gameId);
        when(pictureService.findApprovedByGame(gameId)).thenReturn(List.of(p));
        when(pictureService.viewUrlFor(p)).thenReturn("https://storj/view");

        Response response = resource.approvedForGame(gameId);

        assertEquals(200, response.getStatus());
        List<?> body = assertInstanceOf(List.class, response.getEntity());
        PublicPictureResponse dto = (PublicPictureResponse) body.getFirst();
        assertEquals(p.getId(), dto.id());
        assertEquals(gameId, dto.gameId());
        assertEquals("https://storj/view", dto.viewUrl());
    }

    @Test
    void approvedForLatestGame_returnsCachedGallery() {
        PublicPictureResponse dto = PublicPictureResponse.from(picture(UUID.randomUUID()), null);
        when(galleryCache.latestApprovedGallery()).thenReturn(List.of(dto));

        Response response = resource.approvedForLatestGame();

        assertEquals(200, response.getStatus());
        assertEquals(1, ((List<?>) response.getEntity()).size());
    }

    @Test
    void allApproved_queriesByApprovedStatus() {
        Picture p = picture(UUID.randomUUID());
        when(pictureService.findByStatus(PictureStatus.APPROVED)).thenReturn(List.of(p));
        when(pictureService.viewUrlFor(p)).thenReturn(null);

        Response response = resource.allApproved();

        assertEquals(200, response.getStatus());
        List<?> body = assertInstanceOf(List.class, response.getEntity());
	    assertInstanceOf(PublicPictureResponse.class, body.getFirst());
    }

    private Game game(String name, Instant startTime) {
        Game g = new Game();
        g.setName(name);
        g.setStartTime(startTime);
        return g;
    }

    private Picture picture(UUID gameId) {
        Picture p = new Picture();
        p.setId(UUID.randomUUID());
        p.setGameId(gameId);
        p.setStatus(PictureStatus.APPROVED);
        p.setContentType("image/jpeg");
        p.setUploadedAt(Instant.now());
        return p;
    }
}

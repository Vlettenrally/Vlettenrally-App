package nl.vlettenrally.api.v1;

import nl.vlettenrally.data.domain.Picture;
import nl.vlettenrally.data.dto.picture.PublicPictureResponse;
import nl.vlettenrally.data.enums.PictureStatus;
import nl.vlettenrally.service.PictureService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

/**
 * Unit test for {@link PublicGalleryCache} assembly logic. The {@code @CacheResult} memoization
 * itself is provided by the Quarkus cache interceptor (exercised at runtime, configured via
 * {@code quarkus.cache.caffeine."public-latest-gallery".expire-after-write}); here we only verify
 * the DTO assembly delegates correctly.
 */
@ExtendWith(MockitoExtension.class)
class PublicGalleryCacheTest {

    @Mock PictureService pictureService;

    @InjectMocks PublicGalleryCache cache;

    @Test
    void latestApprovedGallery_mapsPicturesToDtosWithViewUrl() {
        UUID gameId = UUID.randomUUID();
        Picture p = new Picture();
        p.setId(UUID.randomUUID());
        p.setGameId(gameId);
        p.setStatus(PictureStatus.APPROVED);
        p.setContentType("image/jpeg");
        p.setUploadedAt(Instant.now());
        when(pictureService.findApprovedForLatestGame()).thenReturn(List.of(p));
        when(pictureService.viewUrlFor(p)).thenReturn("https://storj/view");

        List<PublicPictureResponse> result = cache.latestApprovedGallery();

        assertEquals(1, result.size());
        assertEquals(p.getId(), result.getFirst().id());
        assertEquals(gameId, result.getFirst().gameId());
        assertEquals("https://storj/view", result.getFirst().viewUrl());
    }
}

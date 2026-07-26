package nl.vlettenrally.api.v1;

import io.quarkus.cache.CacheResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import nl.vlettenrally.data.dto.picture.PublicPictureResponse;
import nl.vlettenrally.service.PictureService;

import java.util.List;

/**
 * Caches the assembled "latest approved gallery" response.
 * <p>
 * The {@code /api/v1/public/games/latest/pictures} endpoint takes no parameters, so it is the
 * hottest public endpoint and recomputes a full approved-picture scan plus a pre-signed URL
 * lookup per picture on every hit. This bean memoizes the finished DTO list with a short TTL
 * (configured via {@code quarkus.cache.caffeine."public-latest-gallery".expire-after-write}).
 * <p>
 * It must be a separate bean from {@link PublicGalleryResource}: {@link CacheResult} is applied
 * by a CDI interceptor, which only fires when the method is invoked through the proxy (i.e. from
 * another bean), not via self-invocation. A short staleness window is acceptable — the underlying
 * pre-signed URLs are themselves long-lived and cached.
 */
@ApplicationScoped
public class PublicGalleryCache {

    @Inject
    PictureService pictureService;

    /** The latest game's approved pictures as public DTOs, cached for the configured TTL. */
    @CacheResult(cacheName = "public-latest-gallery")
    public List<PublicPictureResponse> latestApprovedGallery() {
        return pictureService.toPublicResponses(pictureService.findApprovedForLatestGame());
    }
}

package nl.vlettenrally.api.security;

import jakarta.ws.rs.NameBinding;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Security annotation guarding the public gallery API with a single shared secret.
 * <p>
 * Endpoints annotated with this require an {@code Authorization: Bearer <token>} header that
 * matches the configured {@code vlettenrally.public-gallery.token}. This is intentionally
 * separate from the JWT/team authentication: it lets an external, embeddable consumer (e.g. an
 * event slideshow) read approved pictures without a team or admin login.
 * <p>
 * If the token is not configured, or the header is missing or wrong, a 401 Unauthorized response
 * is returned (fail closed). See {@link PublicGalleryTokenFilter}.
 */
@NameBinding
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface PublicGalleryToken {
}

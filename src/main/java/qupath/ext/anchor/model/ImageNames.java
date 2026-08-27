package qupath.ext.anchor.model;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Collection;

import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ServerTools;

/**
 * Derives names from an image's underlying source <b>file</b> (its URI) rather than any name stored in
 * the image's metadata, falling back to QuPath's displayable name only when no file URI is available.
 */
public final class ImageNames {

    private ImageNames() {}

    /** The source file name (with extension), e.g. {@code "slide-01.ome.tif"}. */
    public static String fileName(ImageServer<?> server) {
        try {
            Collection<URI> uris = server.getURIs();
            if (uris != null && !uris.isEmpty()) {
                URI uri = uris.iterator().next();
                String path = uri.getPath();
                if (path == null || path.isBlank())
                    path = uri.getSchemeSpecificPart();
                if (path != null && !path.isBlank()) {
                    int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
                    String name = slash >= 0 ? path.substring(slash + 1) : path;
                    name = URLDecoder.decode(name, StandardCharsets.UTF_8).trim();
                    if (!name.isBlank())
                        return name;
                }
            }
        } catch (RuntimeException e) {
            // fall through to the display name
        }
        return ServerTools.getDisplayableImageName(server);
    }

    /**
     * File name without its extension, sanitized for use as an output file base name. Removes the
     * final extension and, for OME-TIFF, the {@code .ome} part too, so {@code "slide.ome.tif"} becomes
     * {@code "slide"} while dotted names like {@code "xx.xx.34.54.tif"} become {@code "xx.xx.34.54"}.
     */
    public static String baseName(ImageServer<?> server) {
        String name = fileName(server);
        if (name == null || name.isBlank())
            return "landmarks";
        String base = name.replaceFirst("(?i)\\.[a-z0-9]{1,10}$", "");     // drop the final extension
        if (base.length() >= 4 && base.substring(base.length() - 4).equalsIgnoreCase(".ome"))
            base = base.substring(0, base.length() - 4);                   // and .ome from .ome.tif(f)
        base = base.replaceAll("[\\\\/:*?\"<>|]", "_").trim();             // strip illegal path characters
        return base.isBlank() ? "landmarks" : base;
    }
}

package qupath.ext.anchor.transform;

import java.util.List;

/**
 * Outcome of {@link TransformValidation}: whether a fit may proceed, an error message if not, and any
 * non-fatal warnings (e.g. fewer points than recommended).
 *
 * @param valid    true if a fit may proceed
 * @param error    failure reason when {@code !valid}; {@code null} when valid
 * @param warnings non-fatal cautions to surface to the user
 */
public record ValidationResult(boolean valid, String error, List<String> warnings) {

    public ValidationResult {
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    public static ValidationResult ok(List<String> warnings) {
        return new ValidationResult(true, null, warnings);
    }

    public static ValidationResult fail(String error) {
        return new ValidationResult(false, error, List.of());
    }
}

package simulation.ml;

/**
 * Implemented by controllers that expose AI status to the UI.
 */
public interface AIControlDiagnosticsProvider {

    AIControlDiagnostics getDiagnostics();
}


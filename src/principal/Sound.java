package principal;

/**
 * Audio contract used by the {@link Engine}, independent of any audio library.
 */
public interface Sound {

    /**
     * Plays a short tone.
     *
     * @param pitch normalized pitch in {@code [0, 1]}: {@code 0} is the lowest
     *              tone, {@code 1} the highest
     */
    void playTone(double pitch);

    /** Releases audio resources. */
    void shutdown();

    /**
     * @return a sound implementation that silently ignores every request,
     *         used when the application runs with sound disabled
     */
    static Sound silent() {
        return new Sound() {
            @Override
            public void playTone(double pitch) {
                // intentionally silent
            }

            @Override
            public void shutdown() {
                // no resources to release
            }
        };
    }
}
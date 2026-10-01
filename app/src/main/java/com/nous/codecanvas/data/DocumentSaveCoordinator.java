package com.nous.codecanvas.data;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Serialises document writes for the whole process.
 *
 * <p>Each editor screen used to own its own single-thread executor. Two editor instances could
 * therefore queue writes for the same document with no shared ordering, and finishing one screen
 * neither cancelled nor waited for the other's queue — an older save could land after a newer one
 * and quietly revert the user's text. One process-wide queue, plus the repository's revision
 * check, is what makes "the newest edit is what gets stored" actually true.</p>
 *
 * <p>Kept free of Android types so it is unit-testable off-device.</p>
 */
public final class DocumentSaveCoordinator {

    /** Result of one queued write. Both callbacks run on the queue thread, never on the caller's. */
    public interface Result {
        void onSaved(long revision);

        void onFailed(Exception error);
    }

    private static final ExecutorService QUEUE = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "canvas-doc-save");
        t.setDaemon(true);
        return t;
    });

    private DocumentSaveCoordinator() {
    }

    /**
     * Save content for {@code id}. A negative {@code expectedRevision} means "this document does
     * not exist in the store yet" and creates it; otherwise the write is refused if the store has
     * already moved past that revision.
     */
    public static void save(final DocumentRepository repository, final String id, final String title,
                            final String content, final long expectedRevision, final Result callback) {
        QUEUE.execute(() -> {
            try {
                long stored;
                if (expectedRevision < 0) {
                    stored = repository.saveDocument(
                            new com.nous.codecanvas.model.CanvasDocument(id, title, content, System.currentTimeMillis()));
                } else {
                    stored = repository.updateDocument(id, title, content, expectedRevision);
                }
                if (callback != null) {
                    callback.onSaved(stored);
                }
            } catch (Exception e) {
                if (callback != null) {
                    callback.onFailed(e);
                }
            }
        });
    }

    /**
     * Block until every write queued before this call has finished. Used when leaving the editor:
     * the alternative is dropping the last keystrokes because the process moved on first.
     */
    public static boolean drainAndWait(long timeoutMillis) {
        final CountDownLatch barrier = new CountDownLatch(1);
        QUEUE.execute(barrier::countDown);
        try {
            return barrier.await(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}

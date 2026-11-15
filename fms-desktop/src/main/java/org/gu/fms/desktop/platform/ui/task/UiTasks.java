package org.gu.fms.desktop.platform.ui.task;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import org.gu.fms.desktop.platform.i18n.Messages;
import org.gu.fms.desktop.platform.ui.overlay.Toast;

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Exécute un travail (base de données, calcul, fichier) hors du fil JavaFX, puis revient sur le fil
 * JavaFX pour mettre à jour l'écran. L'interface ne gèle jamais.
 *
 * <pre>{@code
 * tasks.job(() -> service.save(vehicle))
 *      .busy(drawer.busyProperty())
 *      .onSuccess(saved -> toasts.success(...))
 *      .onFailure(error -> ...)          // facultatif : sinon message générique + journal
 *      .start();
 * }</pre>
 */
@Singleton
public final class UiTasks {
    private static final System.Logger LOG = System.getLogger(UiTasks.class.getName());

    private final ExecutorService executor;
    private final Toast toasts;
    private final Messages messages;

    @Inject
    public UiTasks(Toast toasts, Messages messages) {
        this.toasts = toasts;
        this.messages = messages;
        AtomicInteger counter = new AtomicInteger();
        this.executor = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "fms-worker-" + counter.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
    }

    public <T> Job<T> job(Callable<T> work) {
        return new Job<>(Objects.requireNonNull(work, "work"));
    }

    public void shutdown() {
        executor.shutdownNow();
    }

    public final class Job<T> {

        private final Callable<T> work;
        private Consumer<? super T> onSuccess = r -> { };
        private Consumer<Throwable> onFailure;
        private BooleanProperty busy;

        private Job(Callable<T> work) {
            this.work = work;
        }

        public Job<T> onSuccess(Consumer<? super T> handler) {
            this.onSuccess = handler;
            return this;
        }

        public Job<T> onFailure(Consumer<Throwable> handler) {
            this.onFailure = handler;
            return this;
        }

        /** Propriété mise à vrai pendant l'exécution (indicateur de chargement, boutons désactivés). */
        public Job<T> busy(BooleanProperty property) {
            this.busy = property;
            return this;
        }

        /** À appeler sur le fil JavaFX. */
        public void start() {
            if (busy != null) {
                busy.set(true);
            }
            executor.submit(() -> {
                try {
                    T result = work.call();
                    Platform.runLater(() -> finish(() -> onSuccess.accept(result)));
                } catch (Throwable t) {
                    Throwable cause = unwrap(t);
                    Platform.runLater(() -> finish(() -> fail(cause)));
                }
            });
        }

        private void finish(Runnable action) {
            if (busy != null) {
                busy.set(false);
            }
            action.run();
        }

        private void fail(Throwable cause) {
            if (onFailure != null) {
                onFailure.accept(cause);
                return;
            }
            LOG.log(System.Logger.Level.ERROR, "Tâche en échec", cause);
            toasts.error(messages.get("error.unexpected"));
        }
    }

    private static Throwable unwrap(Throwable t) {
        return t instanceof CompletionException && t.getCause() != null ? t.getCause() : t;
    }

}

package it.zoo.animal.infrastructure.event;

import io.quarkus.scheduler.Scheduled;
import io.smallrye.reactive.messaging.kafka.api.OutgoingKafkaRecordMetadata;
import io.smallrye.reactive.messaging.MutinyEmitter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.List;

@ApplicationScoped
public class OutboxRelay {

    private static final Logger LOG = Logger.getLogger(OutboxRelay.class);
    private static final int BATCH_SIZE = 100;

    private final MutinyEmitter<String> emitter;
    private final EntityManager em;

    public OutboxRelay(@Channel("animal-events-out") MutinyEmitter<String> emitter, EntityManager em) {
        this.emitter = emitter;
        this.em = em;
    }

    @Scheduled(every = "${zoo.outbox.relay.interval:2s}", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void relay() {
        publishPending();
    }

    @Transactional
    public int publishPending() {
        @SuppressWarnings("unchecked")
        List<OutboxEventEntity> pending = em.createNativeQuery(
                        "SELECT * FROM outbox_event WHERE published_at IS NULL ORDER BY occurred_at LIMIT :limit FOR UPDATE SKIP LOCKED",
                        OutboxEventEntity.class)
                .setParameter("limit", BATCH_SIZE)
                .getResultList();

        int published = 0;
        for (OutboxEventEntity entity : pending) {
            try {
                Message<String> message = Message.of(entity.getPayload())
                        .addMetadata(OutgoingKafkaRecordMetadata.<String>builder()
                                .withKey(entity.getAggregateId().toString())
                                .build());
                emitter.sendMessageAndAwait(message);
                entity.setPublishedAt(Instant.now());
                published++;
            } catch (Exception e) {
                entity.setAttempts(entity.getAttempts() + 1);
                LOG.warnf(e, "Failed to publish outbox event %s, stopping batch to preserve order", entity.getId());
                break;
            }
        }

        return published;
    }
}

package uk.gov.hmcts.divorce.sow014.lib;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.nfdiv.ccd.Ccd;
import org.jooq.nfdiv.ccd.tables.records.SubmittedCallbackQueueRecord;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import uk.gov.hmcts.ccd.sdk.runtime.CallbackController;
import uk.gov.hmcts.reform.ccd.client.model.CallbackRequest;
import uk.gov.hmcts.reform.ccd.client.model.CallbackResponse;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

import static org.jooq.impl.DSL.not;

@Slf4j
@Component
public class SubmittedCallbackExecutor implements DisposableBean {
    private final DSLContext db;

    private final ObjectMapper mapper;
    private volatile boolean finished;


    @Autowired
    public SubmittedCallbackExecutor(DSLContext db, ObjectMapper getMapper) {
        this.db = db;
        this.mapper = getMapper;

        var t = new Thread(this::process);
        t.setDaemon(true);
        t.setName("****CCD Submitted Callback Executor");
        t.start();
    }

    @SneakyThrows
    private void process() {
        while (!finished) {
            try {
                pollForSubmittedCallbacks();
            } catch (Exception e) {
                log.error("Error polling for submitted callbacks", e);
            }
            Thread.sleep(250);
        }
    }

    public synchronized void pollForSubmittedCallbacks() {
        db.transaction(tx -> {
            var r = tx.dsl()
                .selectFrom(Ccd.CCD.SUBMITTED_CALLBACK_QUEUE)
                .where(Ccd.CCD.SUBMITTED_CALLBACK_QUEUE.ATTEMPTED_AT.isNull())
                .orderBy(Ccd.CCD.SUBMITTED_CALLBACK_QUEUE.ID.asc()) // Pick the oldest pending job
                .limit(1)
                .forUpdate()
                .skipLocked()
                .fetchOptional();

            if(r.isPresent()) {
                try {
                    execute(r.get());
                    tx.dsl()
                        .delete(Ccd.CCD.SUBMITTED_CALLBACK_QUEUE)
                        .where(Ccd.CCD.SUBMITTED_CALLBACK_QUEUE.ID.eq(r.get().getId()))
                        .execute();
                } catch (Exception e) {
                    // TODO: Debate whether we build this properly or use an existing job queue library
                    log.error("Error processing submitted callback", e);
                    tx.dsl()
                        .update(Ccd.CCD.SUBMITTED_CALLBACK_QUEUE)
                        .set(Ccd.CCD.SUBMITTED_CALLBACK_QUEUE.ATTEMPTED_AT, LocalDateTime.now())
                        .set(Ccd.CCD.SUBMITTED_CALLBACK_QUEUE.EXCEPTION, serializeToByteArray(e))
                        .set(Ccd.CCD.SUBMITTED_CALLBACK_QUEUE.EXCEPTION_MESSAGE, e.getMessage())
                        .where(Ccd.CCD.SUBMITTED_CALLBACK_QUEUE.ID.eq(r.get().getId()))
                        .execute();
                }
            }
        });
    }

    @SneakyThrows
    public static byte[] serializeToByteArray(Object obj) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(obj);
            oos.flush();
            return baos.toByteArray();
        }
    }


    @SneakyThrows
    private void execute(SubmittedCallbackQueueRecord callback) {
        var passThroughHeaders = Set.of("accept", "authorization", "content-type", "serviceauthorization");

        CallbackRequest r = mapper.readValue(callback.getPayload().data(), CallbackRequest.class);

        final HttpHeaders httpHeaders = new HttpHeaders();

        var headers = mapper.readValue(callback.getHeaders().data(), ObjectNode.class);
        headers.fieldNames().forEachRemaining( k -> {
            if (passThroughHeaders.contains(k.toLowerCase())) {
                httpHeaders.add(k, headers.get(k).asText());
            } else {
                log.info("Ignoring header {}", k);
            }
        });

        RestTemplate template = new RestTemplate();
        var requestEntity = new HttpEntity<>(r, httpHeaders);
        var result = template.exchange(new URI("http://localhost:4013/callbacks/submitted?eventId=" + callback.getEventId()),
            HttpMethod.POST, requestEntity, String.class);
        System.out.println(result);

    }

    @Override
    public void destroy() throws Exception {
        finished = true;
    }
}

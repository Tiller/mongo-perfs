package com.sigfox.monitoring;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;

import com.mongodb.BasicDBObject;
import com.mongodb.MongoClient;
import com.mongodb.client.model.UpdateOptions;

import reactor.core.publisher.Flux;
import reactor.util.function.Tuple4;
import reactor.util.function.Tuples;

public class MongoPerfTest {

    private static final int NB_COLL = 200;
    private static final int NB_UPDATE = 300_000;
    private static final int NB_THREAD = 12;

    private static ExecutorService executor;
    private static MongoClient mongoDriverClient;

    private List<Tuple4<String, String, Double, Integer>> updates;
    private String groupId;

    @BeforeAll
    public static void initMongo() {
        executor = Executors.newFixedThreadPool(NB_THREAD);
        mongoDriverClient = new MongoClient("localhost");

        for (int i = 0; i < NB_UPDATE; i++) {
            mongoDriverClient
                    .getDatabase("test-mongo")
                    .getCollection("Test_" + (i % NB_COLL))
                    .updateOne(new BasicDBObject("_id", "doc-" + i), new BasicDBObject("$set", new BasicDBObject("useless", 1)), new UpdateOptions().upsert(true));
        }
    }

    @BeforeEach
    public void init() {
        updates = Flux.range(0, NB_UPDATE).map(i -> Tuples.of("Test_" + (i % NB_COLL), "doc-" + i, Math.random(), (int) (System.currentTimeMillis() / 1000))).collectList().block();
        groupId = new ObjectId().toString();
    }

    @RepeatedTest(5)
    public void testRaw() throws InterruptedException, ExecutionException {
        CompletableFuture<Boolean> future = CompletableFuture.completedFuture(true);

        for (final Tuple4<String, String, Double, Integer> update : updates) {

            future = future
                    .thenCombine(CompletableFuture
                            .runAsync(() -> mongoDriverClient
                                    .getDatabase("test-mongo")
                                    .getCollection(update.getT1())
                                    .updateOne(new BasicDBObject("_id", update.getT2()), new BasicDBObject("$set", new BasicDBObject()
                                            .append("fieldA", groupId)
                                            .append("fieldB", groupId)
                                            .append("fieldC", update.getT4())
                                            .append("fieldD.0", update.getT3())
                                            .append("fieldE.0", update.getT4()))),
                                    executor),
                            (a, b) -> true);
        }

        future.get();
    }
}

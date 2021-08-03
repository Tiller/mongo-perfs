package com.sigfox.monitoring;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.SimpleReactiveMongoDatabaseFactory;
import org.springframework.data.mongodb.core.query.Update;

import com.mongodb.BasicDBObject;
import com.mongodb.client.result.UpdateResult;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import reactor.util.function.Tuple4;
import reactor.util.function.Tuples;

public class MongoReactivePerfTest {

    private static final int NB_COLL = 200;
    private static final int NB_UPDATE = 300_000;
    private static final int NB_THREAD = 12;

    private static Scheduler scheduler;
    private static ExecutorService executor;
    private static MongoClient mongoClient;
    private static SimpleReactiveMongoDatabaseFactory mongoFactory;
    private static ReactiveMongoTemplate mongoTpl;

    private List<Tuple4<String, String, Double, Integer>> updates;
    private String groupId;

    @BeforeAll
    public static void initMongo() {
        mongoClient = MongoClients.create();
        mongoFactory = new SimpleReactiveMongoDatabaseFactory(mongoClient, "test-mongo");
        mongoTpl = new ReactiveMongoTemplate(mongoFactory);
        scheduler = Schedulers.newParallel("mongo-scheduler", NB_THREAD);
        executor = Executors.newFixedThreadPool(NB_THREAD);

        Flux.range(0, NB_UPDATE).flatMap(i -> mongoTpl.upsert(query(where("_id").is("doc-" + i)), Update.update("useless", 1), "Test_" + (i % NB_COLL))).blockLast();
    }

    @BeforeEach
    public void init() {
        updates = Flux.range(0, NB_UPDATE).map(i -> Tuples.of("Test_" + (i % NB_COLL), "doc-" + i, Math.random(), (int) (System.currentTimeMillis() / 1000))).collectList().block();
        groupId = new ObjectId().toString();
    }

    private Mono<UpdateResult> reactiveUpdate(final Tuple4<String, String, Double, Integer> t) {
        final Update update = Update
                .update("fieldA", groupId)
                .set("fieldB", groupId)
                .set("fieldC", t.getT4())
                .set("fieldD.0", t.getT3())
                .set("fieldE.0", t.getT4());

        return mongoTpl.updateFirst(query(where("_id").is(t.getT2())), update, t.getT1());
    }

    @Test
    public void testFlatMapNoConcurrency() {
        Flux
                .fromIterable(updates)
                .publishOn(scheduler)
                .flatMap(this::reactiveUpdate, 1, 1)
                .subscribeOn(scheduler)
                .blockLast();
    }

    @RepeatedTest(5)
    public void testFlatMapWithConcurrency() {
        Flux
                .fromIterable(updates)
                .publishOn(scheduler)
                .flatMap(this::reactiveUpdate, NB_THREAD, 1)
                .subscribeOn(scheduler)
                .blockLast();
    }

    @RepeatedTest(5)
    public void testFlatMapWithConcurrencyAndPrefetch() {
        Flux
                .fromIterable(updates)
                .publishOn(scheduler)
                .flatMap(this::reactiveUpdate, NB_THREAD, 10_000)
                .subscribeOn(scheduler)
                .blockLast();
    }

    @RepeatedTest(5)
    public void testFlatMapWithBigConcurrency() {
        Flux
                .fromIterable(updates)
                .publishOn(scheduler)
                .flatMap(this::reactiveUpdate, 100, 1)
                .subscribeOn(scheduler)
                .blockLast();
    }

    @RepeatedTest(5)
    public void testParallel() {
        Flux
                .fromIterable(updates)
                .parallel(NB_THREAD)
                .runOn(scheduler)
                .flatMap(this::reactiveUpdate)
                .sequential()
                .subscribeOn(scheduler)
                .blockLast();
    }

    @RepeatedTest(5)
    public void testBlockExecutor() throws InterruptedException, ExecutionException {
        CompletableFuture<Boolean> future = CompletableFuture.completedFuture(true);

        for (final Tuple4<String, String, Double, Integer> update : updates) {
            future = future.thenCombine(CompletableFuture.runAsync(() -> reactiveUpdate(update).block(), executor), (a, b) -> true);
        }

        future.get();
    }

    @RepeatedTest(5)
    public void testReactiveRaw() throws InterruptedException, ExecutionException {
        CompletableFuture<Boolean> allFuture = CompletableFuture.completedFuture(true);

        for (final Tuple4<String, String, Double, Integer> update : updates) {
            final CompletableFuture<Boolean> future = new CompletableFuture<>();

            executor
                    .execute(() -> mongoClient
                            .getDatabase("test-mongo")
                            .getCollection(update.getT1())
                            .updateOne(new BasicDBObject("_id", update.getT2()), new BasicDBObject("$set", new BasicDBObject()
                                    .append("fieldA", groupId)
                                    .append("fieldB", groupId)
                                    .append("fieldC", update.getT4())
                                    .append("fieldD.0", update.getT3())
                                    .append("fieldE.0", update.getT4())))
                            .subscribe(new Subscriber<UpdateResult>() {

                                @Override
                                public void onSubscribe(Subscription s) {
                                    s.request(1l);
                                }

                                @Override
                                public void onNext(UpdateResult t) {}

                                @Override
                                public void onError(Throwable t) {
                                    t.printStackTrace();
                                }

                                @Override
                                public void onComplete() {
                                    future.complete(true);
                                }
                            }));

            allFuture = allFuture.thenCombine(future, (a, b) -> true);
        }

        allFuture.get();
    }
}

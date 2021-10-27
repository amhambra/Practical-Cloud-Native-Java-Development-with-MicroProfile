package com.packt.microprofile.book.ch4.client;

import java.net.URI;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;

import com.packt.microprofile.book.ch4.thesaurus.NoSuchWordException;

import org.eclipse.microprofile.rest.client.RestClientBuilder;

@Path("/client/mp")
public class MPRestClient {

    private final static URI BASE_URI = URI.create("http://localhost:9080/ch4/rest/thesaurus");

    @GET
    @Path("{word}")
    public String synonymsFor(@PathParam("word") String word) throws NoSuchWordException {
        System.out.println("CALL: com.packt.microprofile.book.ch4.client.MPRestClient.synonymsFor(word=" + word + "=)");

        ThesaurusClient thesaurus = RestClientBuilder.newBuilder().baseUri(BASE_URI)
                // .register(NoSuchWordResponseMapper.class)
                .build(ThesaurusClient.class);
        try {
            return thesaurus.getSynonymsFor(word);
        } catch (NoSuchWordException ex) {
            ex.printStackTrace();
            throw ex;
        }
    }

    static ExecutorService executor = Executors.newFixedThreadPool(4);

    @GET
    @Path("/async/{words}")
    public void synonymsForAsync(@Suspended AsyncResponse ar, @PathParam("words") String words) {
        System.out.println("CALL: com.packt.microprofile.book.ch4.client.MPRestClient.synonymsForAsync(ar=<not show>, words=" + words + "=)");

        executor.submit(() -> {
            StringBuffer sb = new StringBuffer();
            String[] wordsArr = words.split(",");

            CountDownLatch latch = new CountDownLatch(wordsArr.length);

            // info: This is accessing the ThesaurusClient endpoint but use a different interface with same 
            //       methods only missing the exception declarations on the signature.
            ThesaurusAsyncClient thesaurus = RestClientBuilder.newBuilder()
                    .baseUri(BASE_URI)
                    // .register(NoSuchWordResponseMapper.class)
                    .build(ThesaurusAsyncClient.class);

            Arrays.stream(wordsArr).parallel()
                                   .map(thesaurus::getSynonymsFor)
                                   .forEach(cs -> {
                cs.exceptionally(t -> {
                    t.printStackTrace();
                    return "unable to complete request";
                }).thenAccept(s -> {
                    sb.append(s + "\n");
                    latch.countDown();
                });
            });
            try {
                latch.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            ar.resume(sb.toString());
        });
    }

    @PostConstruct
    public void initThesaurus() {
        System.out.println("CALL: com.packt.microprofile.book.ch4.client.MPRestClient.initThesaurus()");

        ThesaurusClient thesaurus = RestClientBuilder.newBuilder().baseUri(BASE_URI)
                // .register(NoSuchWordResponseMapper.class)
                .build(ThesaurusClient.class);
        try {
            thesaurus.setSynonymsFor("funny", "silly,hilarious,jovial");
            thesaurus.setSynonymsFor("serious", "grim");
            thesaurus.setSynonymsFor("sleepy", "tired");
            thesaurus.setSynonymsFor("loud", "cacophonous");
            thesaurus.setSynonymsFor("bookish", "intellectual");
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
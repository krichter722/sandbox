package org.glytching.sandbox.mongo;

import com.google.common.collect.Lists;
import com.mongodb.BasicDBObject;
import com.mongodb.MongoBulkWriteException;
import com.mongodb.MongoClient;
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.*;
import org.bson.BsonString;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.glytching.sandbox.surefire.MongoTests;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.mongodb.client.model.Filters.eq;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;

@Category(MongoTests.class)
public class MongoReadWriteTest {
    private static final Logger logger = LoggerFactory.getLogger(MongoClientTest.class);

    @Test
    public void canFindByObjectId() {
        MongoClient mongoClient = new MongoClientFactory().create();

        MongoCollection<Document> collection = mongoClient.getDatabase("stackoverflow").getCollection("sample");

        Document document = collection.find(eq("_id", new ObjectId("59b86ff639f9ba0f9c0dccf6"))).first();

        logger.info(document.toJson());
    }

    @Test
    public void canWriteAndRead() throws IOException {
        MongoClient mongoClient = new MongoClientFactory().create();

        MongoCollection<Document> collection = mongoClient.getDatabase("stackoverflow").getCollection("sample");

        List<BsonString> terms = new ArrayList<>();
        terms.add(new BsonString("termA"));

        List<BsonString> definitions = new ArrayList<>();
        definitions.add(new BsonString("definitionA"));

        Document document = new Document()
                .append("name", new BsonString("beep"));
        document.put("terms", terms);
        document.put("definitions", definitions);

        collection.insertOne(document);

        Bson filter = eq("name", "beep");
        FindIterable<Document> documents = collection.find(filter).batchSize(50).limit(1);


        for (Document d : documents) {
            logger.info(d.toJson());
            logger.info(document.get("_id", ObjectId.class).toHexString());
        }

        Document updatedDocument = collection.findOneAndUpdate(filter,
                new Document("$push",
                        new BasicDBObject("terms", new BsonString("termB"))
                                .append("definitions", new BsonString("definitionB"))),
                new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER));
        logger.info("Updated document: {}", updatedDocument.toJson());
    }

    @Test
    public void canSample() throws IOException {
        MongoClient mongoClient = new MongoClientFactory().create();

        MongoCollection<Document> collection = mongoClient.getDatabase("stackoverflow").getCollection("sample");

        AggregateIterable<Document> documents = collection.aggregate(Arrays.asList(Aggregates.sample(1)));

        for (Document d : documents) {
            logger.info(d.toJson());
        }
    }

    @Test
    public void canBulkWriteAndIdentifySpecificFailedDocuments() throws IOException {
        MongoClient mongoClient = new MongoClientFactory().create();


        MongoCollection<Document> collection = mongoClient.getDatabase("stackoverflow").getCollection("bulkwrite");

        collection.drop();

        Document knownDocument = new Document().append("name", new BsonString("beep"));
        collection.insertOne(knownDocument);


        collection.createIndex(new BasicDBObject("name", 1), new IndexOptions().unique(true));

        int createDuplicateOnIndex = 2;
        List<Document> docs = Lists.newArrayList();
        for (int i = 0; i < 5; i++) {
            if (i == createDuplicateOnIndex) {
                // deliberately trigger a duplicate key exception
                docs.add(knownDocument);
            } else {
                docs.add(new Document().append("name", new BsonString("beep" + i)));
            }

        }

        try {
            collection.insertMany(docs, new InsertManyOptions().ordered(false));
        } catch (MongoBulkWriteException ex) {
            assertThat(ex.getWriteResult().getInsertedCount(), is(4));
            assertThat(ex.getMessage(), containsString("duplicate key error"));
            assertThat(ex.getWriteErrors().size(), is(1));
            assertThat(ex.getWriteErrors().get(0).getIndex(), is(createDuplicateOnIndex));
            assertThat(ex.getWriteErrors().get(0).getCode(), is(11000));
            assertThat(ex.getWriteErrors().get(0).getMessage(), startsWith("E11000 duplicate key error"));
        }

    }
}
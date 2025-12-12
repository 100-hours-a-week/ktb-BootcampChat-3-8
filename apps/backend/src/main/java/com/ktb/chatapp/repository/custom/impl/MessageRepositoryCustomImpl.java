package com.ktb.chatapp.repository.custom.impl;

import com.ktb.chatapp.model.Message;
import com.ktb.chatapp.repository.custom.MessageRepositoryCustom;
import com.mongodb.bulk.BulkWriteResult;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.BulkOperations.BulkMode;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MessageRepositoryCustomImpl implements MessageRepositoryCustom {
    private final MongoTemplate mongoTemplate;

    @Override
    public long bulkUpdate(List<Message> messages) {
        BulkOperations ops = mongoTemplate.bulkOps(BulkMode.UNORDERED, Message.class);

        for (Message message : messages) {
            Query query = Query.query(Criteria.where("_id").is(message.getId()));

            Update update = new Update()
                    .set("readers", message.getReaders());

            ops.updateOne(query, update);
        }

        BulkWriteResult result = ops.execute();

        return result.getModifiedCount();
    }
}

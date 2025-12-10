package com.ktb.chatapp.service;

import com.ktb.chatapp.model.Message;
import com.ktb.chatapp.repository.MessageRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.mongodb.client.result.UpdateResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

/**
 * 메시지 읽음 상태 관리 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageReadStatusService {

    private final MongoTemplate mongoTemplate; // MongoTemplate 사용

    /**
     * 메시지 읽음 상태 업데이트
     *
     * @param messageIds 읽음 상태를 업데이트할 메시지 리스트
     * @param userId 읽은 사용자 ID
     */
    public void updateReadStatus(List<String> messageIds, String userId) {
        if (messageIds.isEmpty()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();

        // 이미 readers.userId에 같은 userId가 없는 문서만 업데이트
        Query query = new Query(Criteria.where("_id").in(messageIds)
                .and("readers.userId").ne(userId));

        Message.MessageReader readerInfo = Message.MessageReader.builder()
                .userId(userId)
                .readAt(LocalDateTime.now())
                .build();

        Update update = new Update().push("readers",
                Message.MessageReader.builder()
                        .userId(userId)
                        .readAt(now)
                        .build()
        );

        // 여러 개 문서를 한 번에 update
        UpdateResult result = mongoTemplate.updateMulti(query, update, Message.class);
        log.debug("Read status updated for {} messages by user {}",
                result.getModifiedCount(), userId);
    }
}

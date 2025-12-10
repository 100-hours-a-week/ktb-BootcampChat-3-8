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
     * @param userId     읽은 사용자 ID
     */
    public void updateReadStatus(List<String> messageIds, String userId) {
            if (messageIds == null || messageIds.isEmpty()) {
                return;
            }

            LocalDateTime now = LocalDateTime.now();

            try {
                Query query = new Query(
                        Criteria.where("_id").in(messageIds)
                                .and("readers.userId").ne(userId)  // 이미 읽지 않은 메시지만
                );

                Update update = new Update().push("readers",
                        Message.MessageReader.builder()
                                .userId(userId)
                                .readAt(now)
                                .build()
                );

                UpdateResult result = mongoTemplate.updateMulti(query, update, Message.class);

                log.debug("Read status updated for {} messages by user {}",
                        result.getModifiedCount(), userId);

            } catch (Exception e) {
                log.error("Read status update error for user {}", userId, e);
            }
        }
    }


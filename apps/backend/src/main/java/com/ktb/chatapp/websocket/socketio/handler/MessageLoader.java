package com.ktb.chatapp.websocket.socketio.handler;

import static java.util.Collections.emptyList;

import com.ktb.chatapp.dto.FetchMessagesRequest;
import com.ktb.chatapp.dto.FetchMessagesResponse;
import com.ktb.chatapp.dto.MessageResponse;
import com.ktb.chatapp.model.Message;
import com.ktb.chatapp.model.User;
import com.ktb.chatapp.repository.MessageRepository;
import com.ktb.chatapp.repository.UserRepository;
import com.ktb.chatapp.service.MessageReadStatusService;
import jakarta.annotation.Nullable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MessageLoader {

    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final MessageResponseMapper messageResponseMapper;
    private final MessageReadStatusService messageReadStatusService;

    private static final int BATCH_SIZE = 30;

    /**
     * 메시지 로드
     */
    public FetchMessagesResponse loadMessages(FetchMessagesRequest data, String userId) {
        try {
            return loadMessagesInternal(data.roomId(), data.limit(BATCH_SIZE), data.before(LocalDateTime.now()), userId);
        } catch (Exception e) {
            log.error("Error loading initial messages for room {}", data.roomId(), e);
            return FetchMessagesResponse.builder()
                    .messages(emptyList())
                    .hasMore(false)
                    .build();
        }
    }

    private FetchMessagesResponse loadMessagesInternal(
            String roomId,
            int limit,
            LocalDateTime before,
            String userId) {

        Pageable pageable = PageRequest.of(0, limit, Sort.by("timestamp").descending());

        Page<Message> messagePage = messageRepository
                .findByRoomIdAndIsDeletedAndTimestampBefore(roomId, false, before, pageable);

        List<Message> messages = messagePage.getContent();

        // DESC로 조회했으므로 ASC로 재정렬 (채팅 UI 표시 순서)
        List<Message> sortedMessages = messages.reversed();

        // 1) 읽음 상태 먼저 업데이트
        var messageIds = sortedMessages.stream()
                .map(Message::getId)
                .toList();
        messageReadStatusService.updateReadStatus(messageIds, userId);

        // 2) N+1 제거: senderId를 모아서 한 번에 조회
        //    - null 필터링 (AI 메시지 등)
        //    - Set으로 중복 제거
        var senderIds = sortedMessages.stream()
                .map(Message::getSenderId)
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.toSet());

        // sender가 하나도 없을 수도 있으니 방어 코드
        var userMap = senderIds.isEmpty()
                ? java.util.Collections.<String, User>emptyMap()
                : userRepository.findAllById(senderIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        // 3) Message → MessageResponse 매핑 시 Map에서 sender 찾기
        List<MessageResponse> messageResponses = sortedMessages.stream()
                .map(message -> {
                    User sender = null;
                    String senderId = message.getSenderId();
                    if (senderId != null) {
                        sender = userMap.get(senderId);   // N+1 대신 Map lookup
                    }
                    return messageResponseMapper.mapToMessageResponse(message, sender);
                })
                .toList();

        boolean hasMore = messagePage.hasNext();

        log.debug("Messages loaded - roomId: {}, limit: {}, count: {}, hasMore: {}",
                roomId, limit, messageResponses.size(), hasMore);

        return FetchMessagesResponse.builder()
                .messages(messageResponses)
                .hasMore(hasMore)
                .build();
    }


    /**
     * AI 경우 null 반환 가능
     */
//    @Nullable
//    private User findUserById(String id) {
//        if (id == null) {
//            return null;
//        }
//        return userRepository.findById(id)
//                .orElse(null);
//    }
}

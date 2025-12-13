package com.ktb.chatapp.event;

import com.ktb.chatapp.config.CacheConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Room 캐시 이벤트 리스너
 * Room 참가자 변경 이벤트를 구독하여 방별 참가자 목록 캐시를 관리합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RoomCacheEventListener {

    /**
     * Room 참가자 변경 이벤트 처리
     * 사용자가 방에 입장하거나 퇴장할 때 해당 방의 참가자 목록 캐시를 무효화
     */
    @Async
    @EventListener
    public void handleRoomParticipantChanged(RoomParticipantChangedEvent event) {
        log.debug("RoomParticipantChangedEvent received - RoomId: {}, UserId: {}, Type: {}",
                event.getRoomId(), event.getUserId(), event.getChangeType());

        // 해당 방의 참가자 목록 캐시 무효화
        evictRoomParticipantsCache(event.getRoomId());

        log.info("Room participants cache evicted - RoomId: {}, Reason: user {} {}",
                event.getRoomId(),
                event.getUserId(),
                event.getChangeType() == RoomParticipantChangedEvent.ChangeType.JOINED ? "joined" : "left");
    }

    /**
     * 방별 참가자 목록 캐시 무효화
     */
    @CacheEvict(value = CacheConfig.ROOM_PARTICIPANTS_CACHE, key = "#roomId")
    private void evictRoomParticipantsCache(String roomId) {
        log.debug("Cache evicted for room participants: {}", roomId);
    }
}

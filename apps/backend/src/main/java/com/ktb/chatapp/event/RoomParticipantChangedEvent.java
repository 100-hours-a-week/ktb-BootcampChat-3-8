package com.ktb.chatapp.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * Room 참가자 변경 이벤트
 * 사용자가 방에 입장하거나 퇴장할 때 발행됩니다.
 */
@Getter
public class RoomParticipantChangedEvent extends ApplicationEvent {
    private final String roomId;
    private final String userId;
    private final ChangeType changeType;

    public enum ChangeType {
        JOINED,  // 입장
        LEFT     // 퇴장
    }

    public RoomParticipantChangedEvent(Object source, String roomId, String userId, ChangeType changeType) {
        super(source);
        this.roomId = roomId;
        this.userId = userId;
        this.changeType = changeType;
    }

    public static RoomParticipantChangedEvent joined(Object source, String roomId, String userId) {
        return new RoomParticipantChangedEvent(source, roomId, userId, ChangeType.JOINED);
    }

    public static RoomParticipantChangedEvent left(Object source, String roomId, String userId) {
        return new RoomParticipantChangedEvent(source, roomId, userId, ChangeType.LEFT);
    }
}

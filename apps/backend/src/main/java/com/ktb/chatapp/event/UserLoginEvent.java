package com.ktb.chatapp.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 사용자 로그인 이벤트
 * 로그인 성공 시 발행되어 캐시 Pre-warming 등의 작업을 수행합니다.
 */
@Getter
public class UserLoginEvent extends ApplicationEvent {
    private final String userId;
    private final String email;
    private final String sessionId;

    public UserLoginEvent(Object source, String userId, String email, String sessionId) {
        super(source);
        this.userId = userId;
        this.email = email;
        this.sessionId = sessionId;
    }
}

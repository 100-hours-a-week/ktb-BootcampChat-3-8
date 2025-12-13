package com.ktb.chatapp.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 사용자 캐시 무효화 이벤트
 * 사용자 정보가 변경되거나 삭제될 때 발행됩니다.
 */
@Getter
public class UserCacheEvictEvent extends ApplicationEvent {
    private final String userId;
    private final String email;
    private final String reason;

    public UserCacheEvictEvent(Object source, String userId, String email, String reason) {
        super(source);
        this.userId = userId;
        this.email = email;
        this.reason = reason;
    }

    /**
     * email만 있는 경우 (userId 없이)
     */
    public static UserCacheEvictEvent withEmail(Object source, String email, String reason) {
        return new UserCacheEvictEvent(source, null, email, reason);
    }

    /**
     * userId만 있는 경우 (email은 리스너에서 조회)
     */
    public static UserCacheEvictEvent withUserId(Object source, String userId, String reason) {
        return new UserCacheEvictEvent(source, userId, null, reason);
    }
}

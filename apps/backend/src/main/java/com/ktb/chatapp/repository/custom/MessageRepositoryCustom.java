package com.ktb.chatapp.repository.custom;

import com.ktb.chatapp.model.Message;
import java.util.List;

public interface MessageRepositoryCustom {
    long bulkUpdate(List<Message> messages);
}

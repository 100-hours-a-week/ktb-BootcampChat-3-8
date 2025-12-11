package com.ktb.chatapp.websocket.socketio;

/**
 * Socket User Record
 * @param id user id
 * @param name user name
 * @param authSessionId user auth session id (DEPRECATED: JWT 인증으로 전환, 하위 호환성 위해 유지, null 허용)
 * @param socketId user websocket session id
 */
public record SocketUser(String id, String name, String authSessionId, String socketId) {
}

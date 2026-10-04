package org.example.howareyou.domain.chat.kafka.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.howareyou.domain.chat.kafka.dto.ChatMessageCreatedEvent;
import org.example.howareyou.domain.chat.repository.ChatRoomMemberRepository;
import org.example.howareyou.domain.chat.websocket.entity.ChatMessageDocument;
import org.example.howareyou.domain.chat.websocket.entity.ChatMessageStatus;
import org.example.howareyou.domain.chat.websocket.repository.ChatMessageDocumentRepository;
import org.example.howareyou.domain.chat.websocket.service.ChatRedisService;
import org.example.howareyou.domain.notification.service.NotificationPushService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ChatMessageEventConsumer {

  private final ChatRedisService chatRedisService;
  private final NotificationPushService notificationPushService;
  private final ObjectMapper objectMapper;
  private final ChatMessageDocumentRepository mongoRepository;
  private final ChatRoomMemberRepository chatRoomMemberRepository;

  @KafkaListener(topics = "chat.message.created", groupId = "chat-consumer")
  public void handleChatMessageCreated(String payload) throws JsonProcessingException {
    ChatMessageCreatedEvent event = objectMapper.readValue(payload, ChatMessageCreatedEvent.class);

    // --- Mongo 저장 (ID·시각은 서비스에서 정한 값 그대로 → ACK와 저장본 일치, 재전송돼도 같은 문서에 덮어씀)
    ChatMessageDocument doc = mongoRepository.save(
        ChatMessageDocument.builder()
            .id(event.getMessageId())
            .chatRoomUuid(event.getRoomUuid())
            .senderId(String.valueOf(event.getSenderId()))
            .senderName(event.getSenderName())
            .content(event.getContent())
            .messageTime(event.getMessageTime() != null ? event.getMessageTime() : Instant.now())
            .chatMessageStatus(ChatMessageStatus.UNREAD)
            .build()
    );

    // --- Redis 캐시
    chatRedisService.addRecentMessage(event.getRoomUuid(), doc);
    chatRedisService.trimRecentMessages(event.getRoomUuid(), 30);

    // --- 수신자: 이 방의 JOINED 참여자 중 보낸 사람 제외 (1:1이면 1명, 그룹이면 여러 명)
    List<Long> receiverIds = chatRoomMemberRepository
        .findJoinedMemberIdsExcept(event.getRoomUuid(), event.getSenderId());

    for (Long receiverId : receiverIds) {
      String receiverKey = String.valueOf(receiverId);

      // 지금 이 방을 보고 있지 않은 사람만 안 읽은 수 +1
      if (!event.getRoomUuid().equals(chatRedisService.getCurrentChatRoom(receiverKey))) {
        chatRedisService.incrementUnread(event.getRoomUuid(), receiverKey);
      }

      // 알림 발송 (수신자별) — 한 명 실패가 나머지를 막지 않도록 개별 처리
      try {
        notificationPushService.sendChatNotify(
            event.getRoomId(),
            event.getSenderId(),
            doc.getId(),
            doc.getContent(),
            receiverId
        );
      } catch (Exception e) {
        log.error("채팅 알림 발송 실패: room={}, receiverId={}", event.getRoomUuid(), receiverId, e);
      }
    }

    log.info("[ASYNC] Message saved + cached + notified. room={}, msgId={}, receivers={}",
        event.getRoomUuid(), doc.getId(), receiverIds.size());
  }


}

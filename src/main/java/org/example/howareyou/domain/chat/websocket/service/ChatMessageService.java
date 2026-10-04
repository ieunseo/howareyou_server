package org.example.howareyou.domain.chat.websocket.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.howareyou.domain.chat.entity.ChatRoom;
import org.example.howareyou.domain.chat.entity.ChatRoomMember;
import org.example.howareyou.domain.chat.entity.ChatRoomMemberStatus;
import org.example.howareyou.domain.chat.kafka.dto.ChatMessageCreatedEvent;
import org.example.howareyou.domain.chat.repository.ChatRoomMemberRepository;
import org.example.howareyou.domain.chat.repository.ChatRoomRepository;
import org.example.howareyou.domain.chat.websocket.dto.ChatMessageDocumentResponse;
import org.example.howareyou.domain.chat.websocket.dto.ChatMessageResponse;
import org.example.howareyou.domain.chat.websocket.dto.CreateChatMessageRequest;
import org.example.howareyou.domain.chat.websocket.entity.ChatMessageDocument;
import org.example.howareyou.domain.chat.websocket.entity.ChatMessageStatus;
import org.example.howareyou.domain.chat.websocket.repository.ChatMessageDocumentRepository;
import org.example.howareyou.domain.member.entity.Member;
import org.example.howareyou.domain.member.repository.MemberRepository;
import org.example.howareyou.domain.notification.service.NotificationPushService;
import org.example.howareyou.global.exception.CustomException;
import org.example.howareyou.global.exception.ErrorCode;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatMessageService {
  private final ChatMessageDocumentRepository mongoRepository;
  private final ChatRedisService chatRedisService;
  private final ChatRoomRepository chatRoomRepository;
  private final NotificationPushService notificationPushService;
  private final MemberRepository memberRepository;
  private final KafkaTemplate<String, Object> kafkaTemplate;
  private final ObjectMapper objectMapper;
  private final ChatRoomMemberRepository chatRoomMemberRepository;

  /**
   * 채팅 메시지를 Redis 캐시에 저장하고, MongoDB에 영구 저장하는 메서드. - 채팅방 및 상대방 정보 확인 - Redis: 최근 메시지 추가, 안읽은 메시지 수
   * 증가 - MongoDB: 메시지 비동기 저장
   *
   * @return
   */
  @Transactional
  public ChatMessageResponse saveChatMessage(CreateChatMessageRequest request) throws JsonProcessingException {

    final String chatRoomUuid = request.getChatRoomUuid();

    // DB: 채팅방 조회
    ChatRoom chatRoom = chatRoomRepository.findByUuid(chatRoomUuid)
        .orElseThrow(() -> new CustomException(ErrorCode.CHAT_ROOM_NOT_FOUND));

    // DB: 발신자 조회
    Long senderId = request.getSenderId();
    Member sender = memberRepository.findById(senderId)
        .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));

    // 보낸 사람이 이 방의 JOINED 참여자인지 확인 (초대만 받았거나 나간 사람은 전송 불가)
    requireJoinedMember(chatRoomUuid, senderId);

    // 1:1 방만 수신자 1명을 ACK에 채워 줌 (기존 클라이언트 호환). 그룹방은 수신자가 여러 명이라 null
    Member receiver = chatRoom.isGroup()
        ? null
        : chatRoomRepository.findReceiverByRoomUuidAndSenderId(chatRoomUuid, senderId);

    // 메시지 ID·시각을 여기서 확정 → Consumer가 같은 값으로 Mongo에 저장
    String messageId = UUID.randomUUID().toString();
    Instant messageTime = Instant.now();

    // Kafka 이벤트 발행 (Mongo 저장은 Consumer에서)
    ChatMessageCreatedEvent event = ChatMessageCreatedEvent.builder()
        .roomId(chatRoom.getId())
        .roomUuid(chatRoomUuid)
        .senderId(senderId)
        .senderName(sender.getMembername())
        .messageId(messageId)
        .content(request.getContent())
        .messageTime(messageTime)
        .build();

    // Kafka 키 = 방 UUID → 같은 방 메시지는 같은 파티션으로 가서 순서가 보장됨
    kafkaTemplate.send("chat.message.created", chatRoomUuid, objectMapper.writeValueAsString(event));

    // ACK 응답
    return new ChatMessageResponse(
        messageId,
        chatRoomUuid,
        String.valueOf(senderId),
        sender.getMembername(),
        receiver != null ? String.valueOf(receiver.getId()) : null,
        receiver != null ? receiver.getMembername() : null,
        request.getContent(),
        messageTime,
        ChatMessageStatus.UNREAD
    );
  }



  /**
   * Redis에서 최근 메시지 30개 조회
   */
  public List<ChatMessageDocumentResponse> getRecentMessagesWithFallback(String chatRoomId, int maxCount,Long memberId) {

    // 0. 이 방 참여자인지 확인 + 그룹방이면 참여 시각 이후 메시지만 보이게
    Instant visibleFrom = getVisibleFrom(chatRoomId, memberId);
    // 1. Redis에서 메시지 조회
    List<Object> rawMessages = chatRedisService.getRecentMessages(chatRoomId, maxCount);
    List<ChatMessageDocument> redisMessages = rawMessages.stream()
        .filter(ChatMessageDocument.class::isInstance)
        .map(o -> (ChatMessageDocument) o)
        .filter(d -> visibleFrom == null || !d.getMessageTime().isBefore(visibleFrom))
        .toList();

    int redisCount = redisMessages.size();

    List<ChatMessageDocumentResponse> responses = redisMessages.stream()
        .map(ChatMessageDocumentResponse::from)
        .toList();

    // 2. 메시지가 충분하면 그대로 리턴
    if (redisCount >= maxCount) {
      return responses;
    }

    // 3. 부족하면 MongoDB에서 보충
    int remaining = maxCount - redisMessages.size();

    // Redis 리스트는 rightPush라 0번이 가장 오래된 메시지 → 그보다 이전 것을 Mongo에서 보충
    Instant oldestRedisTime = redisMessages.isEmpty()
        ? Instant.now()
        : redisMessages.get(0).getMessageTime();

    PageRequest pageable = PageRequest.of(0, remaining, Sort.by(Sort.Direction.DESC, "messageTime"));

    List<ChatMessageDocument> mongoMessages = new ArrayList<>(findMessagesBefore(
        chatRoomId, visibleFrom, oldestRedisTime, pageable));


    // 시간 역순 정렬이므로 다시 정방향 정렬
    Collections.reverse(mongoMessages);

    // 4. 결합 후 반환
    List<ChatMessageDocument> result = new ArrayList<>();
      result.addAll(mongoMessages);
      result.addAll(redisMessages);

      return result.stream()
          .map(ChatMessageDocumentResponse::from)
          .toList();
}

  /**
   * 유저가 채팅방에 입장했을 때 호출되는 로직
   * - 안 읽은 메시지 수 초기화
   * - MongoDB 메시지 상태를 읽음(READ)으로 변경
   */
  public void markMessagesAsRead(String chatRoomId, String userId) {
    // 0. 이 방 참여자인지 확인
    requireJoinedMember(chatRoomId,Long.parseLong(userId));

    // 1. Redis에서 안 읽은 메시지 수 초기화
    chatRedisService.resetUnread(chatRoomId, userId);

    // 그룹방: Mongo의 READ/UNREAD는 메시지 하나에 값이 하나라 "누가 읽었는지"를 표현 못 함
    // → 개인별 안 읽은 수(Redis)만 초기화하고 메시지 상태는 건드리지 않음
    boolean group = chatRoomRepository.findByUuid(chatRoomId)
        .map(ChatRoom::isGroup)
        .orElse(false);
    if (group) {
      return;
    }

    // 2. MongoDB에서 해당 채팅방의 해당 유저가 안 읽은 메시지를 모두 읽음 처리
    List<ChatMessageDocument> unreadMessages = mongoRepository
        .findByChatRoomUuidAndSenderIdNotAndChatMessageStatus(chatRoomId, userId, ChatMessageStatus.UNREAD);

    unreadMessages.forEach(msg -> msg.setChatMessageStatus(ChatMessageStatus.READ));

    mongoRepository.saveAll(unreadMessages);
  }

  /**
   * 이전 메시지 페이징
  */
  public List<ChatMessageDocumentResponse> getPreviousMessages(String chatRoomId, Instant before, int size, Long memberId) {
    //이 방의 참여자인지 + 그룹방이면 참여 시각 이후 메시지만
    Instant visibleFrom = getVisibleFrom(chatRoomId, memberId);
    PageRequest pageable = PageRequest.of(0, size, Sort.by(Sort.Direction.DESC, "messageTime"));

    List<ChatMessageDocument> mongoMessages = new ArrayList<>(findMessagesBefore(
        chatRoomId, visibleFrom, before, pageable));

    // 시간 역순으로 가져왔으므로, 정방향으로 정렬
    Collections.reverse(mongoMessages);

    return mongoMessages.stream()
        .map(ChatMessageDocumentResponse::from)
        .toList();
  }

  // 이 방의 JOINED 참여자가 아니면 403
  private void requireJoinedMember(String roomUuid, Long memberId){
    if(!chatRoomMemberRepository.existsByChatRoom_UuidAndMember_IdAndStatus(roomUuid,memberId, ChatRoomMemberStatus.JOINED)){
      throw new CustomException(ErrorCode.FORBIDDEN_CHAT_ROOM_ACCESS);
    }
  }

  // JOINED 검사 후, 그룹방이면 내가 참여한 시각 반환 (1:1이면 null = 제한 없음)
  private Instant getVisibleFrom(String roomUuid, Long memberId) {
    ChatRoomMember me = chatRoomMemberRepository.findByChatRoom_UuidAndMember_Id(roomUuid, memberId)
        .filter(m -> m.getStatus() == ChatRoomMemberStatus.JOINED)
        .orElseThrow(() -> new CustomException(ErrorCode.FORBIDDEN_CHAT_ROOM_ACCESS));

    boolean group = chatRoomRepository.findByUuid(roomUuid)
        .map(ChatRoom::isGroup)
        .orElse(false);
    if (!group || me.getJoinedAt() == null) {
      return null;
    }
    return me.getJoinedAt().atZone(ZoneId.systemDefault()).toInstant();
  }

  // before 이전 메시지 (visibleFrom이 있으면 그 이후 것만)
  private List<ChatMessageDocument> findMessagesBefore(String roomUuid, Instant visibleFrom,
      Instant before, PageRequest pageable) {
    if (visibleFrom == null) {
      return mongoRepository.findTopNByChatRoomUuidAndMessageTimeBeforeOrderByMessageTimeDesc(
          roomUuid, before, pageable);
    }
    return mongoRepository.findVisibleMessagesBefore(roomUuid, visibleFrom, before, pageable);
  }
}

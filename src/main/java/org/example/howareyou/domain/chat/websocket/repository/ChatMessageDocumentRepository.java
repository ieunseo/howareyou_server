package org.example.howareyou.domain.chat.websocket.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.example.howareyou.domain.chat.websocket.entity.ChatMessageDocument;
import org.example.howareyou.domain.chat.websocket.entity.ChatMessageStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ChatMessageDocumentRepository extends MongoRepository<ChatMessageDocument, String> {

  @Query(value = "{'chatRoomUuid': ?0, 'messageTime': { '$lt': ?1 }}")
  List<ChatMessageDocument> findTopNByChatRoomUuidAndMessageTimeBeforeOrderByMessageTimeDesc(
          String chatRoomUuid,
          Instant messageTimeBefore,
          Pageable pageable
  );

  List<ChatMessageDocument> findByChatRoomUuidAndSenderIdNotAndChatMessageStatus(
          String chatRoomUuid,
          String senderId,
          ChatMessageStatus status
  );

  // 그룹방: 내가 참여한 시각(from) 이후 ~ before 이전 메시지만
  @org.springframework.data.mongodb.repository.Query(
          value = "{'chatRoomUuid': ?0, 'messageTime': { '$gte': ?1, '$lt': ?2 }}")
  List<ChatMessageDocument> findVisibleMessagesBefore(
          String chatRoomUuid,
          Instant from,
          Instant before,
          Pageable pageable
  );

  Optional<ChatMessageDocument> findTopByChatRoomUuidOrderByMessageTimeDesc(String chatRoomUuid);

  List<ChatMessageDocument> findByMessageTimeBetween(Instant start, Instant end);

}

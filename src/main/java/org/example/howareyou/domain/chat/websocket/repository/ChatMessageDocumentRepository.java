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

  Optional<ChatMessageDocument> findTopByChatRoomUuidOrderByMessageTimeDesc(String chatRoomUuid);

  List<ChatMessageDocument> findByMessageTimeBetween(Instant start, Instant end);

}
